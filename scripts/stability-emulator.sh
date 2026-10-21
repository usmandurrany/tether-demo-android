#!/usr/bin/env bash
# Connection stability tests on a running emulator: starts a throwaway ejabberd
# with Toxiproxy in front of it, runs the instrumented tests in
# app/src/androidTest/.../stability (poor network, Wi-Fi/mobile switching,
# network toggling, opening and closing the app), then kills and restarts the
# app repeatedly to time cold starts. Used by CI; works locally too with Docker
# and an emulator running, after ./gradlew assembleDebug assembleDebugAndroidTest.
#
# Writes stability-report.txt (one line per measured recovery), plus
# stability-instrument.txt and stability-logcat.txt for digging into failures.
#
#   STABILITY_CLASSES   test classes to run (default: all four)
#   RECONNECT_LIMIT_MS  how soon the connection must work again (default 10000)
#   COLD_STARTS         number of measured cold starts (default 10; 0 skips them)
set -euo pipefail
APKS=${APKS:-app/build/outputs/apk}
PKG=io.github.usmandurrany.tether.demo
RUNNER=$PKG.test/androidx.test.runner.AndroidJUnitRunner
LIMIT=${RECONNECT_LIMIT_MS:-10000}
COLD_STARTS=${COLD_STARTS:-10}
CLASSES=${STABILITY_CLASSES:-PoorNetworkTest,NetworkSwitchTest,NetworkToggleTest,AppOpenCloseTest}
NET=tether-stability
rm -f stability-report.txt stability-instrument.txt stability-logcat.txt stability-coldstart.txt

# --- Server and proxy ----------------------------------------------------------------

docker network create $NET >/dev/null 2>&1 || true
start_server() {
  docker rm -f tether-stability-ejabberd >/dev/null 2>&1 || true
  docker run -d --name tether-stability-ejabberd --network $NET -p 5222:5222 ghcr.io/processone/ejabberd:latest >/dev/null
  for _ in $(seq 90); do
    docker exec tether-stability-ejabberd ejabberdctl status >/dev/null 2>&1 && return 0
    [ "$(docker inspect -f '{{.State.Running}}' tether-stability-ejabberd)" = true ] || break
    sleep 2
  done
  echo "ejabberd did not start:" >&2
  docker logs tether-stability-ejabberd 2>&1 | tail -n 100 >&2
  return 1
}
start_server || start_server
docker exec tether-stability-ejabberd ejabberdctl register alice localhost secret
docker exec tether-stability-ejabberd ejabberdctl register bob localhost secret

# Toxiproxy on 15222 forwards to ejabberd; its API on 8474 lets the tests spoil the link.
docker rm -f tether-stability-toxiproxy >/dev/null 2>&1 || true
docker run -d --name tether-stability-toxiproxy --network $NET -p 8474:8474 -p 15222:15222 ghcr.io/shopify/toxiproxy:2.12.0 >/dev/null
for _ in $(seq 30); do curl -sf http://localhost:8474/version >/dev/null && break; sleep 1; done
curl -sf -X POST http://localhost:8474/proxies \
  -d '{"name":"xmpp","listen":"0.0.0.0:15222","upstream":"tether-stability-ejabberd:5222"}' >/dev/null

# --- App ------------------------------------------------------------------------------

adb install -r "$APKS/debug/app-debug.apk"
adb install -r "$APKS/androidTest/debug/app-debug-androidTest.apk"
# Lets the instrumentation post notifications without a dialog in the way.
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS || true

echo "--- Networks on the emulator"
adb shell dumpsys connectivity | grep -E "^ *NetworkAgentInfo|Active default network" | head -n 10 || true

ARGS=(-e xmppHost 10.0.2.2 -e xmppDomain localhost -e xmppPassword secret
      -e proxyPort 15222 -e toxiproxyApi http://10.0.2.2:8474 -e reconnectLimitMs "$LIMIT")

adb logcat -G 16M || true
adb logcat -c
adb logcat -v time > stability-logcat.txt &
LOGCAT=$!
trap 'kill $LOGCAT 2>/dev/null || true' EXIT

# --- Instrumented tests ---------------------------------------------------------------

TESTS=$(echo "$CLASSES" | tr ',' '\n' | sed "s/^/$PKG.stability./" | paste -sd, -)
adb shell am instrument -w -r -e class "$TESTS" "${ARGS[@]}" $RUNNER | tee stability-instrument.txt || true
tests_ok=true
grep -q "^OK (" stability-instrument.txt || tests_ok=false

# --- Cold starts ----------------------------------------------------------------------
# Kills the app and starts it again, as after swiping it away on phones that
# kill the process. With "Connect on app start" the SDK resumes or logs in on
# its own; each start is timed until the SDK reports Online.

cold_ok=true
if [ "$COLD_STARTS" -gt 0 ]; then
  adb shell am instrument -w -r -e class $PKG.stability.ColdStartSetup -e prepareColdStart true "${ARGS[@]}" $RUNNER \
    | tee -a stability-instrument.txt
  online_after() { # waits for the SDK's "Online(" log line; prints ms or "-"
    local start=$1 deadline=$(( $1 + 30000 ))
    while [ "$(date +%s%3N)" -lt $deadline ]; do
      if adb logcat -d -s Tether:I 2>/dev/null | grep -q "Online("; then
        echo $(( $(date +%s%3N) - start )); return
      fi
      sleep 0.2
    done
    echo "-"
  }
  launch() { adb shell am start -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n $PKG/.MainActivity >/dev/null; }
  for i in $(seq "$COLD_STARTS"); do
    adb shell am force-stop $PKG
    # Every other round, a start that is killed again before it can connect.
    if [ $((i % 2)) -eq 0 ]; then
      launch
      sleep "0.$((RANDOM % 9 + 1))"
      adb shell am force-stop $PKG
    fi
    adb logcat -c
    start=$(date +%s%3N)
    launch
    ms=$(online_after "$start")
    note=$([ $((i % 2)) -eq 0 ] && echo "after a start killed mid-connect" || echo "")
    if [ "$ms" = "-" ]; then verdict="FAIL (never)"; cold_ok=false
    elif [ "$ms" -gt "$LIMIT" ]; then verdict="FAIL"; cold_ok=false
    else verdict="ok"; fi
    echo "StabilityReport: ColdStart | cold start #$i | $ms ms | limit $LIMIT ms | $verdict${note:+ | $note}" | tee -a stability-coldstart.txt
    sleep "1.$((RANDOM % 9))"
  done
  adb shell am force-stop $PKG
fi

kill $LOGCAT 2>/dev/null || true
docker logs tether-stability-ejabberd > stability-ejabberd.txt 2>&1 || true

# --- Report ---------------------------------------------------------------------------

{
  grep -h "StabilityReport" stability-logcat.txt stability-coldstart.txt 2>/dev/null | sed 's/.*StabilityReport: //'
} > stability-report.txt || true
echo "--- Stability report"
cat stability-report.txt
if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then
  {
    echo "### Connection stability"
    echo
    echo "| Test | Scenario | Took | Limit | Result |"
    echo "| --- | --- | --- | --- | --- |"
    grep " | limit " stability-report.txt | awk -F' \\| ' '{ printf "| %s | %s | %s | %s | %s%s |\n", $1, $2, $3, $4, $5, ($6 ? " (" $6 ")" : "") }'
    echo
    grep "summary:" stability-report.txt | sed 's/^/- /'
  } >> "$GITHUB_STEP_SUMMARY"
fi

$tests_ok && $cold_ok

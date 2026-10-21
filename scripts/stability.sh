#!/usr/bin/env bash
# Connection stability tests on an Android phone or emulator connected over adb:
# runs the instrumented tests in app/src/androidTest/.../stability (the app cut
# off while Wi-Fi stays up, Wi-Fi/mobile switching, network toggling, opening and
# closing the app), and optionally kills and restarts the app to time cold starts.
#
# The server is either an existing XMPP server (XMPP_SERVER set) or a throwaway
# ejabberd in Docker on this computer.
#
# Build first: ./gradlew assembleDebug assembleDebugAndroidTest
# A phone must stay connected over USB (not wireless adb): the tests turn its
# Wi-Fi off and on. It must be unlocked; the script keeps the screen on.
#
# Writes stability-report.txt (one line per measured recovery), plus
# stability-instrument.txt and stability-logcat.txt for digging into failures.
#
# An existing server (passwords come from the environment, never from the repo):
#   XMPP_SERVER         the server's host name or IP, as this computer reaches it
#   XMPP_SERVER_IP      its IP for the device, if the device can't resolve the
#                       name (default: what this computer resolves it to)
#   XMPP_PORT           its client port (default 5222)
#   XMPP_DOMAIN         its XMPP domain
#   ALICE_JID, ALICE_PASSWORD   the account the app signs in as during the tests
#   BOB_JID, BOB_PASSWORD       the account on the other side of the chat
#
# Common:
#   HOST_IP             with the throwaway server, this computer's address as the
#                       device reaches it (default 10.0.2.2, the emulator's; for a
#                       phone, this computer's Wi-Fi IP)
#   MOBILE_DATA         true when the device can also reach the server and this
#                       computer over mobile data (default true on an emulator,
#                       false otherwise). When false, mobile data is kept off during
#                       the run and the Wi-Fi/mobile switching tests are skipped.
#   STABILITY_CLASSES   test classes to run (default: all four)
#   RECONNECT_LIMIT_MS  how soon the connection must work again (default 10000)
#   COLD_STARTS         number of measured cold starts (default 0). They need a
#                       server that stores SCRAM keys, which the throwaway one does not.
#   EJABBERD_IMAGE      throwaway server image (default: ejabberd 26.07; 26.09 does not start in Docker)
set -euo pipefail
APKS=${APKS:-app/build/outputs/apk}
PKG=io.github.usmandurrany.tether.demo
RUNNER=$PKG.test/androidx.test.runner.AndroidJUnitRunner
LIMIT=${RECONNECT_LIMIT_MS:-10000}
COLD_STARTS=${COLD_STARTS:-0}
HOST_IP=${HOST_IP:-10.0.2.2}
if [ -z "${MOBILE_DATA:-}" ]; then
  [ "$HOST_IP" = 10.0.2.2 ] && [ -z "${XMPP_SERVER:-}" ] && MOBILE_DATA=true || MOBILE_DATA=false
fi
CLASSES=${STABILITY_CLASSES:-AppCutOffTest,NetworkSwitchTest,NetworkToggleTest,AppOpenCloseTest}
EJ=tether-stability-ejabberd
# Milliseconds since the epoch; macOS's date has no %N.
now_ms() { python3 -c 'import time; print(int(time.time() * 1000))'; }
rm -f stability-report.txt stability-instrument.txt stability-logcat.txt stability-coldstart.txt

# --- Server -------------------------------------------------------------------------

if [ -n "${XMPP_SERVER:-}" ]; then
  : "${XMPP_DOMAIN:?set XMPP_DOMAIN}" "${ALICE_JID:?set ALICE_JID}" "${ALICE_PASSWORD:?set ALICE_PASSWORD}"
  : "${BOB_JID:?set BOB_JID}" "${BOB_PASSWORD:?set BOB_PASSWORD}"
  XMPP_PORT=${XMPP_PORT:-5222}
  if [ -z "${XMPP_SERVER_IP:-}" ]; then
    XMPP_SERVER_IP=$(python3 -c 'import socket, sys; print(socket.gethostbyname(sys.argv[1]))' "$XMPP_SERVER" 2>/dev/null \
      || getent hosts "$XMPP_SERVER" 2>/dev/null | awk '{print $1; exit}' || true)
    [ -n "$XMPP_SERVER_IP" ] || { echo "Could not resolve $XMPP_SERVER; set XMPP_SERVER_IP" >&2; exit 1; }
  fi
  SERVER_FOR_DEVICE=$XMPP_SERVER_IP
  echo "--- Using the server at $XMPP_SERVER ($XMPP_SERVER_IP:$XMPP_PORT), domain $XMPP_DOMAIN"
else
  XMPP_PORT=5222
  XMPP_DOMAIN=localhost
  ALICE_JID=alice@localhost ALICE_PASSWORD=secret BOB_JID=bob@localhost BOB_PASSWORD=secret
  SERVER_FOR_DEVICE=$HOST_IP
  server_ready() {
    for _ in $(seq 90); do
      docker exec $EJ ejabberdctl status >/dev/null 2>&1 && return 0
      [ "$(docker inspect -f '{{.State.Running}}' $EJ)" = true ] || break
      sleep 2
    done
    echo "ejabberd did not start:" >&2
    docker logs $EJ 2>&1 | tail -n 100 >&2
    return 1
  }
  start_server() {
    docker rm -f $EJ >/dev/null 2>&1 || true
    docker run -d --name $EJ -p 5222:5222 "${EJABBERD_IMAGE:-ghcr.io/processone/ejabberd:26.07}" >/dev/null
    server_ready
  }
  start_server || start_server
  docker exec $EJ ejabberdctl register alice localhost secret
  docker exec $EJ ejabberdctl register bob localhost secret
fi

# --- App ------------------------------------------------------------------------------

adb install -r "$APKS/debug/app-debug.apk"
adb install -r "$APKS/androidTest/debug/app-debug-androidTest.apk"
# Lets the instrumentation post notifications without a dialog in the way.
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS || true

# Keep the screen on and unlocked; give the device its networks back at the end.
adb shell svc power stayon true || true
adb shell input keyevent KEYCODE_WAKEUP || true
adb shell wm dismiss-keyguard || true
restore_device() {
  adb shell cmd connectivity airplane-mode disable >/dev/null 2>&1 || true
  adb shell svc wifi enable >/dev/null 2>&1 || true
  adb shell svc data enable >/dev/null 2>&1 || true
  adb shell svc power stayon false >/dev/null 2>&1 || true
  adb shell cmd connectivity set-package-networking-enabled true $PKG >/dev/null 2>&1 || true
  adb shell cmd connectivity set-chain3-enabled false >/dev/null 2>&1 || true
}

echo "--- Networks on the device"
adb shell dumpsys connectivity | grep -E "^ *NetworkAgentInfo|Active default network" | head -n 10 || true

ARGS=(-e xmppHost "$SERVER_FOR_DEVICE" -e xmppPort "$XMPP_PORT" -e xmppDomain "$XMPP_DOMAIN"
      -e aliceJid "$ALICE_JID" -e alicePassword "$ALICE_PASSWORD" -e bobJid "$BOB_JID" -e bobPassword "$BOB_PASSWORD"
      -e mobileData "$MOBILE_DATA" -e reconnectLimitMs "$LIMIT")

# The device must reach the server before anything else is worth running.
reaches() { adb shell "nc -z -w 5 $1 $2 2>/dev/null || toybox nc -z -w 5 $1 $2" >/dev/null 2>&1; }
reaches "$SERVER_FOR_DEVICE" "$XMPP_PORT" \
  || echo "Warning: could not confirm that the device reaches the server at $SERVER_FOR_DEVICE:$XMPP_PORT." >&2

adb logcat -G 16M || true
adb logcat -c
adb logcat -v time > stability-logcat.txt &
LOGCAT=$!
trap 'kill $LOGCAT 2>/dev/null || true; restore_device' EXIT

# --- Instrumented tests ---------------------------------------------------------------

TESTS=$(echo "$CLASSES" | tr ',' '\n' | sed "s/^/$PKG.stability./" | paste -sd, -)
adb shell am instrument -w -r -e class "$TESTS" "${ARGS[@]}" $RUNNER | tee stability-instrument.txt || true
tests_ok=true
grep -q "^OK (" stability-instrument.txt || tests_ok=false
if ! $tests_ok; then
  if [ -z "${XMPP_SERVER:-}" ]; then
    echo "--- Server log"
    docker logs $EJ 2>&1 | grep -iE "auth|sasl|scram|fail|error" | tail -n 40 || true
  fi
  echo "--- SDK log"
  grep -E " Tether |Stability" stability-logcat.txt | grep -iE "auth|sasl|scram|mechanism|Terminal|fail" | head -n 40 || true
fi

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
    while [ "$(now_ms)" -lt $deadline ]; do
      if adb logcat -d -s Tether:I 2>/dev/null | grep -q "Online("; then
        echo $(( $(now_ms) - start )); return
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
    start=$(now_ms)
    launch
    ms=$(online_after "$start")
    note=$([ $((i % 2)) -eq 0 ] && echo "after a start killed mid-connect" || echo "")
    if [ "$ms" = "-" ]; then
      verdict="FAIL (never)"; cold_ok=false
      echo "--- Tether log of cold start #$i"; adb logcat -d -s Tether:I | tail -n 25
    elif [ "$ms" -gt "$LIMIT" ]; then verdict="FAIL"; cold_ok=false
    else verdict="ok"; fi
    echo "StabilityReport: ColdStart | cold start #$i | $ms ms | limit $LIMIT ms | $verdict${note:+ | $note}" | tee -a stability-coldstart.txt
    sleep "1.$((RANDOM % 9))"
  done
  adb shell am force-stop $PKG
fi

kill $LOGCAT 2>/dev/null || true
[ -n "${XMPP_SERVER:-}" ] || docker logs $EJ > stability-ejabberd.txt 2>&1 || true

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

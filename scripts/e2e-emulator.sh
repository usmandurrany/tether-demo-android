#!/usr/bin/env bash
# Starts a throwaway ejabberd with two accounts, installs the demo and its test
# APKs on the running emulator, and runs the end-to-end test. Used by CI; works
# locally too with Docker and an emulator running, after
# ./gradlew assembleDebug assembleDebugAndroidTest.
set -euo pipefail
APKS=${APKS:-app/build/outputs/apk}

docker rm -f tether-e2e-ejabberd >/dev/null 2>&1 || true
docker run -d --name tether-e2e-ejabberd -p 5222:5222 ghcr.io/processone/ejabberd:latest
for _ in $(seq 90); do docker exec tether-e2e-ejabberd ejabberdctl status >/dev/null 2>&1 && break; sleep 2; done
docker exec tether-e2e-ejabberd ejabberdctl register alice localhost secret
docker exec tether-e2e-ejabberd ejabberdctl register bob localhost secret

adb install -r "$APKS/debug/app-debug.apk"
adb install -r "$APKS/androidTest/debug/app-debug-androidTest.apk"
adb logcat -c
adb shell am instrument -w -r \
  -e xmppHost 10.0.2.2 -e xmppDomain localhost -e xmppPassword secret \
  io.github.usmandurrany.tether.demo.test/androidx.test.runner.AndroidJUnitRunner | tee instrument.txt || true
adb logcat -d > logcat.txt
docker logs tether-e2e-ejabberd > ejabberd.txt 2>&1
grep -q "OK (1 test)" instrument.txt

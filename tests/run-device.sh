#!/usr/bin/env bash
set -eu
adb install app/build/outputs/apk/storyPreview/app-storyPreview.apk
adb install app/build/outputs/apk/debug/app-debug.apk
adb install app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell screenrecord --time-limit 60 /sdcard/serjoy-animation.mp4 > /tmp/serjoy-record.log 2>&1 &
record_pid=$!
adb shell am instrument -w com.sunantongsan.ghostmatch3.test/com.sunantongsan.ghostmatch3.SerJoySmoke | tee smoke-result.txt
wait "$record_pid" || true
adb pull /sdcard/Android/data/com.sunantongsan.ghostmatch3/files/screenshots screenshots || true
adb pull /sdcard/serjoy-animation.mp4 serjoy-animation.mp4 || true
grep 'PASS: quiz screens' smoke-result.txt

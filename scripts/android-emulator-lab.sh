#!/bin/bash
# Dedicated, disposable Android fixture. Never point LAB_DIR at a personal AVD.
set -euo pipefail
export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}"
export LAB_DIR="${LAB_DIR:-$HOME/omnibot-android-lab}"
export ANDROID_AVD_HOME="$LAB_DIR/avd"
ADB="$ANDROID_SDK_ROOT/platform-tools/adb"
EMU="$ANDROID_SDK_ROOT/emulator/emulator"
SERIAL=emulator-5580
PKG=cn.com.omnimind.bot
mkdir -p "$LAB_DIR/logs" "$ANDROID_AVD_HOME"
adb_lab() { "$ADB" -s "$SERIAL" "$@"; }
case "${1:-status}" in
  provision|snapshot|restore|stop|status)
    [ "$(adb_lab emu avd name | tr -d '\r' | sed -n '1p')" = omnibot-lab ] || { echo 'Not the dedicated lab AVD' >&2; exit 1; }
    ;;
esac
case "${1:-status}" in
init)
  image="$ANDROID_SDK_ROOT/system-images/android-34/default/arm64-v8a"
  if [ ! -f "$image/system.img" ]; then
    echo 'd9f2011131919abe952814e041f16f317242c3fa  android34.zip' > "$LAB_DIR/downloads/android34.sha1"
    (cd "$LAB_DIR/downloads" && shasum -c android34.sha1)
    mkdir -p "$(dirname "$image")"
    unzip -q "$LAB_DIR/downloads/android34.zip" -d "$(dirname "$image")"
  fi
  if [ ! -f "$ANDROID_AVD_HOME/omnibot-lab.avd/config.ini" ]; then
    mkdir -p "$ANDROID_AVD_HOME/omnibot-lab.avd"
    printf 'avd.ini.encoding=UTF-8\npath=%s/omnibot-lab.avd\ntarget=android-34\n' "$ANDROID_AVD_HOME" > "$ANDROID_AVD_HOME/omnibot-lab.ini"
    cat > "$ANDROID_AVD_HOME/omnibot-lab.avd/config.ini" <<CONFIG
AvdId=omnibot-lab
avd.ini.displayname=OmniBot disposable Android lab
abi.type=arm64-v8a
hw.cpu.arch=arm64
hw.cpu.ncore=4
hw.ramSize=4096
hw.lcd.width=1080
hw.lcd.height=1920
hw.lcd.density=420
hw.keyboard=yes
hw.gpu.enabled=yes
hw.gpu.mode=swiftshader_indirect
hw.audioInput=no
hw.audioOutput=no
hw.camera.back=none
hw.camera.front=none
disk.dataPartition.size=8G
image.sysdir.1=$image/
tag.id=default
tag.display=Default
PlayStore.enabled=false
showDeviceFrame=no
CONFIG
  fi
  ;;
start)
  if "$ADB" devices | grep -q "^$SERIAL[[:space:]]"; then echo "$SERIAL already present"; exit 1; fi
  snapshot_args=()
  if [ -f "$ANDROID_AVD_HOME/omnibot-lab.avd/snapshots/omnibot-permission-baseline/snapshot.pb" ]; then
    snapshot_args=(-snapshot omnibot-permission-baseline)
  fi
  nohup "$EMU" "${snapshot_args[@]}" -avd omnibot-lab -port 5580 -no-window -no-audio -no-boot-anim -gpu swiftshader_indirect -no-snapshot-save > "$LAB_DIR/logs/emulator.log" 2>&1 < /dev/null &
  echo $! > "$LAB_DIR/emulator.pid"
  echo "Started PID $(cat "$LAB_DIR/emulator.pid")"
  ;;
provision)
  [ "$(adb_lab shell getprop sys.boot_completed | tr -d '\r')" = 1 ]
  [ "$(adb_lab shell getprop ro.kernel.qemu | tr -d '\r')" = 1 ]
  adb_lab install -r -g "$LAB_DIR/downloads/shizuku.apk"
  shizuku_apk="$(adb_lab shell pm path moe.shizuku.privileged.api | tr -d '\r' | sed 's/^package://')"
  adb_lab shell "${shizuku_apk%/base.apk}/lib/arm64/libshizuku.so"
  adb_lab install -r -g "$LAB_DIR/omnibot.apk"
  adb_lab shell pm grant "$PKG" moe.shizuku.manager.permission.API_V23
  for op in SYSTEM_ALERT_WINDOW WRITE_SETTINGS GET_USAGE_STATS MANAGE_EXTERNAL_STORAGE REQUEST_INSTALL_PACKAGES PROJECT_MEDIA SCHEDULE_EXACT_ALARM; do
    adb_lab shell cmd appops set "$PKG" "$op" allow
  done
  adb_lab shell dumpsys deviceidle whitelist +"$PKG"
  adb_lab shell settings put secure enabled_accessibility_services "$PKG/cn.com.omnimind.accessibility.service.AssistsService"
  adb_lab shell settings put secure accessibility_enabled 1
  adb_lab shell cmd notification allow_listener "$PKG/cn.com.omnimind.bot.agent.personal.PersonalNotificationListener"
  adb_lab shell locksettings set-disabled true
  adb_lab shell settings put global window_animation_scale 0
  adb_lab shell settings put global transition_animation_scale 0
  adb_lab shell settings put global animator_duration_scale 0
  adb_lab shell settings put system screen_off_timeout 1800000
  adb_lab shell am start -n "$PKG/.activity.LauncherActivity"
  ;;
snapshot) adb_lab emu avd snapshot save omnibot-permission-baseline ;;
restore) adb_lab emu avd snapshot load omnibot-permission-baseline ;;
stop) adb_lab emu kill ;;
status)
  "$ADB" devices -l
  adb_lab shell getprop sys.boot_completed
  adb_lab shell settings get secure enabled_accessibility_services
  adb_lab shell cmd appops get "$PKG"
  ;;
*) echo 'Usage: omnibot-lab.sh init|start|provision|snapshot|restore|status|stop' >&2; exit 2 ;;
esac

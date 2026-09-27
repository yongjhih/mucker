#!/usr/bin/env bash
set -e

# Mucker One-Line Quickstart & Demo Runner
# Automatically builds demo app, sets up port forwarding, launches on emulator/device, and opens browser.

BOLD="\033[1m"
GREEN="\033[32m"
BLUE="\033[34m"
CYAN="\033[36m"
YELLOW="\033[33m"
RESET="\033[0m"

echo -e "${CYAN}"
cat << "EOF"
  __  __            _             
 |  \/  |_   _  ___| | _____ _ __ 
 | |\/| | | | |/ __| |/ / _ \ '__|
 | |  | | |_| | (__|   <  __/ |   
 |_|  |_|\__,_|\___|_|\_\___|_|   
 Zero-cert CDP OkHttp Mock Engine
EOF
echo -e "${RESET}"

# 1. Resolve Android SDK & Tools
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$ANDROID_HOME}"

if [ -d "/Applications/Android Studio.app/Contents/jbr/Contents/Home" ] && [ -z "$JAVA_HOME" ]; then
  export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
fi

ADB="$ANDROID_HOME/platform-tools/adb"
if ! command -v "$ADB" &> /dev/null; then
  if command -v adb &> /dev/null; then
    ADB="adb"
  else
    echo -e "${YELLOW}[!] Warning: adb not found at $ADB. Make sure Android SDK platform-tools is installed.${RESET}"
  fi
fi

# 2. Check Device / Emulator Connection
echo -e "${BLUE}[1/5] Checking connected Android devices...${RESET}"
DEVICE_COUNT=$("$ADB" devices | grep -v "List" | grep "device$" | wc -l | tr -d ' ' || echo "0")

if [ "$DEVICE_COUNT" -eq "0" ]; then
  echo -e "${YELLOW}[!] No running device/emulator detected.${RESET}"
  EMULATOR_BIN="$ANDROID_HOME/emulator/emulator"
  if [ -x "$EMULATOR_BIN" ]; then
    AVD_NAME=$("$EMULATOR_BIN" -list-avds | head -n 1 || true)
    if [ -n "$AVD_NAME" ]; then
      echo -e "${CYAN}[→] Starting Android emulator: $AVD_NAME...${RESET}"
      "$EMULATOR_BIN" -avd "$AVD_NAME" -no-boot-anim -no-audio &
      echo -e "${BLUE}[→] Waiting for emulator to boot...${RESET}"
      "$ADB" wait-for-device
      while [ "$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" != "1" ]; do
        sleep 1
      done
      echo -e "${GREEN}[✓] Emulator ready!${RESET}"
    fi
  fi
else
  echo -e "${GREEN}[✓] Found $DEVICE_COUNT active Android device/emulator.${RESET}"
fi

# 3. Build & Install Demo App
echo -e "${BLUE}[2/5] Compiling and installing Mucker Demo App...${RESET}"
chmod +x ./gradlew
./gradlew :app:installDebug

# 4. Port Forwarding
echo -e "${BLUE}[3/5] Forwarding ADB port 8080 (tcp:8080 -> tcp:8080)...${RESET}"
"$ADB" forward tcp:8080 tcp:8080

# 5. Launch Demo Activity
echo -e "${BLUE}[4/5] Launching Demo Application on device...${RESET}"
"$ADB" shell am start -n io.github.mucker.demo/.MainActivity > /dev/null

# 6. Open Web Dashboard in Browser
echo -e "${BLUE}[5/5] Launching Mucker Web Dashboard in your browser...${RESET}"
DASHBOARD_URL="http://localhost:8080"
REMOTE_FALLBACK="https://yongjhih.github.io/mucker/dashboard/?target=127.0.0.1:8080"

# Try opening localhost dashboard
if command -v open &> /dev/null; then
  open "$DASHBOARD_URL" || open "$REMOTE_FALLBACK"
elif command -v xdg-open &> /dev/null; then
  xdg-open "$DASHBOARD_URL" || xdg-open "$REMOTE_FALLBACK"
fi

echo -e "\n${GREEN}${BOLD}✓ Mucker is now live!${RESET}"
echo -e "• Embedded Web Dashboard:  ${CYAN}http://localhost:8080${RESET}"
echo -e "• GitHub Pages Dashboard:  ${CYAN}$REMOTE_FALLBACK${RESET}"
echo -e "• REST API Status:         ${CYAN}http://localhost:8080/api/status${RESET}"
echo -e "• CDP WebSocket Endpoint:  ${CYAN}ws://127.0.0.1:8080/devtools/page${RESET}"
echo -e "\n${BOLD}Press endpoints in the Android app to watch requests stream live onto the timeline!${RESET}\n"

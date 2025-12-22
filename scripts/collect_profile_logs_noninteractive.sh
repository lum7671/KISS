#!/usr/bin/env bash
set -euo pipefail

# Non-interactive capture of logcat + ProfileManager CSVs for a fixed duration.
# Usage: bash scripts/collect_profile_logs_noninteractive.sh [duration_seconds]

DUR=${1:-180} # default 180 seconds

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

if ! command -v adb >/dev/null 2>&1; then
  echo -e "${RED}❌ adb not found. Install Android Platform Tools.${NC}"; exit 1
fi

# Check device
if ! adb get-state >/dev/null 2>&1; then
  echo -e "${RED}❌ No device detected. Enable USB debugging and authorize the PC.${NC}"
  adb devices || true
  exit 1
fi

APP_PKG="kr.lum7671.kiss"
PROFILE_DIR="/storage/emulated/0/Android/data/${APP_PKG}/files/kiss_profile_logs"
TS=$(date '+%Y%m%d_%H%M%S')
OUT_DIR="logs/profile_${TS}"
mkdir -p "${OUT_DIR}/profile_logs"
LOG_FILE="${OUT_DIR}/kiss_logcat_${TS}.txt"

# Optional: install latest signed profile apk if present
LATEST_APK=$(ls -1 app/build/outputs/apk/profile/*_profile_signed.apk 2>/dev/null | tail -1 || true)
if [ -n "${LATEST_APK}" ]; then
  echo -e "${BLUE}📦 Installing latest Profile APK:${NC} ${LATEST_APK}"
  adb install -r "${LATEST_APK}" >/dev/null || true
else
  echo -e "${YELLOW}⚠️ No signed Profile APK found. Skipping install.${NC}"
fi

# Clear and start logcat capture
echo -e "${BLUE}🧹 Clearing logcat...${NC}"
adb logcat -c || true

echo -e "${BLUE}🎬 Capturing logcat for ${DUR}s...${NC}"
# Capture focused tags; fallback to broad capture if tag filter unsupported
( adb logcat -v time -s \
  "ProfileManager:V" \
  "ActionPerformanceTracker:V" \
  "DataHandler:V" \
  "Provider:V" \
  "MainActivity:V" \
  "KISS_PERF:V" \
  "*:S" || adb logcat -v time ) | tee "${LOG_FILE}" &
CAP_PID=$!

# Give user a hint window to interact
cat <<EOF
${YELLOW}Now perform scenarios during the next ${DUR}s:${NC}
  - Cold start the app multiple times
  - Background/foreground to trigger onResume
  - Perform searches (first + subsequent)
EOF

sleep "${DUR}" || true

# Stop capture
if ps -p ${CAP_PID} >/dev/null 2>&1; then
  echo -e "${BLUE}🛑 Stopping log capture...${NC}"
  kill ${CAP_PID} || true
  sleep 1
fi

# Pull CSVs
echo -e "${BLUE}⬇️ Pulling ProfileManager CSVs...${NC}"
if adb shell ls -1 "${PROFILE_DIR}"/*.csv >/dev/null 2>&1; then
  adb pull "${PROFILE_DIR}" "${OUT_DIR}/profile_logs" >/dev/null
  echo -e "${GREEN}✅ Pulled CSVs to ${OUT_DIR}/profile_logs${NC}"
else
  echo -e "${YELLOW}⚠️ No CSV files found at ${PROFILE_DIR}${NC}"
fi

# Zip
ZIP_FILE="${OUT_DIR}.zip"
if command -v zip >/dev/null 2>&1; then
  (cd logs && zip -rq "$(basename "${ZIP_FILE}")" "$(basename "${OUT_DIR}")")
  echo -e "${GREEN}📦 Created archive: ${ZIP_FILE}${NC}"
else
  echo -e "${YELLOW}⚠️ zip not found. Skipping archive creation.${NC}"
fi

echo -e "${GREEN}✅ Capture complete.${NC}"
echo -e "${BLUE}Outputs:${NC}\n  - ${LOG_FILE}\n  - ${OUT_DIR}/profile_logs/*.csv (if any)\n  - ${ZIP_FILE}"

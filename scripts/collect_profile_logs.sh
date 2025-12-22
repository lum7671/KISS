#!/usr/bin/env bash
set -euo pipefail

# KISS - Phase 1.3 helper: collect Logcat + pull ProfileManager CSVs
# Usage:
#   bash scripts/collect_profile_logs.sh
# What it does:
#   - Verifies adb + device connection
#   - Clears logcat and starts filtered capture
#   - Waits for you to reproduce scenarios, then stops
#   - Pulls CSVs from app sandbox and zips everything

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# Verify adb
if ! command -v adb >/dev/null 2>&1; then
  echo -e "${RED}❌ adb not found. Install Android Platform Tools.${NC}"
  exit 1
fi

# Verify device
if ! adb get-state >/dev/null 2>&1; then
  echo -e "${RED}❌ No device detected. Enable USB debugging and authorize the PC.${NC}"
  adb devices || true
  exit 1
fi

TS=$(date '+%Y%m%d_%H%M%S')
OUT_DIR="logs/profile_${TS}"
mkdir -p "${OUT_DIR}"

APP_PKG="kr.lum7671.kiss"
PROFILE_DIR="/storage/emulated/0/Android/data/${APP_PKG}/files/kiss_profile_logs"

# Clear logcat
echo -e "${BLUE}🧹 Clearing logcat...${NC}"
adb logcat -c || true

# Start filtered logcat capture in background
LOG_FILE="${OUT_DIR}/kiss_logcat_${TS}.txt"
echo -e "${BLUE}🎬 Starting log capture (press Enter to stop)...${NC}"
# Focus on our tags; silence others
# Fallback to unfiltered if -s fails on some devices
set +e
adb logcat -v time -s \
  "ProfileManager:V" \
  "ActionPerformanceTracker:V" \
  "DataHandler:V" \
  "Provider:V" \
  "MainActivity:V" \
  "KISS_PERF:V" \
  "*:S" | tee "${LOG_FILE}" &
CAP_PID=$!
set -e

# Guidance
cat <<EOF

${YELLOW}Now reproduce the measurement scenarios:${NC}
  1) Cold start the app 10x
  2) Background / foreground to trigger onResume reloads 20x
  3) Perform searches (first + subsequent) 10x
  4) Use the launcher normally for a few minutes

When done, press Enter to stop capture and pull CSV logs.
EOF

# Wait for user
read -r _

# Stop logcat
if ps -p ${CAP_PID} >/dev/null 2>&1; then
  echo -e "${BLUE}🛑 Stopping log capture...${NC}"
  kill ${CAP_PID} || true
  sleep 1
fi

# Pull profile CSVs
echo -e "${BLUE}⬇️ Pulling ProfileManager CSVs...${NC}"
mkdir -p "${OUT_DIR}/profile_logs"
if adb shell ls -1 "${PROFILE_DIR}"/*.csv >/dev/null 2>&1; then
  adb pull "${PROFILE_DIR}" "${OUT_DIR}/profile_logs" >/dev/null
  echo -e "${GREEN}✅ Pulled CSVs to ${OUT_DIR}/profile_logs${NC}"
else
  echo -e "${YELLOW}⚠️ No CSV files found at ${PROFILE_DIR}${NC}"
fi

# Zip the bundle
ZIP_FILE="${OUT_DIR}.zip"
if command -v zip >/dev/null 2>&1; then
  (cd logs && zip -rq "$(basename "${ZIP_FILE}")" "$(basename "${OUT_DIR}")")
  echo -e "${GREEN}📦 Created archive: ${ZIP_FILE}${NC}"
else
  echo -e "${YELLOW}⚠️ zip not found. Skipping archive creation.${NC}"
fi

# Summary
echo -e "${GREEN}✅ Done.${NC}"
echo -e "${BLUE}Files:${NC}"
echo "  - ${LOG_FILE}"
echo "  - ${OUT_DIR}/profile_logs/ (CSV files, if any)"
echo "  - ${ZIP_FILE} (if created)"

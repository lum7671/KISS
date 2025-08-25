#!/bin/bash

echo "=== Shizuku Debug Script ==="
echo "Date: $(date)"
echo

# Shizuku 서비스 상태 확인
echo "1. Checking Shizuku service status..."
adb shell "ps -A | grep shizuku" || echo "   Shizuku process not found"
echo

# Shizuku 앱 설치 확인
echo "2. Checking if Shizuku app is installed..."
adb shell "pm list packages | grep shizuku" || echo "   Shizuku package not found"
echo

# KISS 앱 권한 확인
echo "3. Checking KISS app permissions..."
adb shell "dumpsys package fr.neamar.kiss | grep permission" | grep -i shizuku || echo "   No Shizuku permissions found for KISS"
echo

# KISS 앱의 Shizuku 관련 로그 확인
echo "4. Checking recent logs for Shizuku and KISS..."
adb logcat -d | grep -i "shizuku\|kiss" | tail -20 || echo "   No recent logs found"
echo

# Shizuku 바인더 상태 확인
echo "5. Checking Shizuku binder status..."
adb shell "ls -la /dev/shizuku*" 2>/dev/null || echo "   Shizuku binder not found"
echo

echo "=== Debug Complete ==="

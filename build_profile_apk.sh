#!/bin/bash

# KISS Profile APK 빌드 스크립트
# Galaxy Note 20 Ultra 프로파일링용 APK 자동 빌드

set -e  # 에러 시 스크립트 중단

echo "🚀 KISS Profile APK 빌드 시작..."

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 현재 디렉토리 확인
if [ ! -f "gradlew" ]; then
    echo -e "${RED}❌ 에러: KISS 프로젝트 루트 디렉토리에서 실행해주세요${NC}"
    exit 1
fi

# Android SDK 경로 확인
if [ -z "$ANDROID_HOME" ]; then
    export ANDROID_HOME="$HOME/Library/Android/sdk"
fi

APKSIGNER="$ANDROID_HOME/build-tools/34.0.0/apksigner"
if [ ! -f "$APKSIGNER" ]; then
    APKSIGNER=$(find "$ANDROID_HOME/build-tools" -name "apksigner" -type f | head -1)
    if [ -z "$APKSIGNER" ]; then
        echo -e "${RED}❌ apksigner를 찾을 수 없습니다${NC}"
        exit 1
    fi
fi

# 디버그 키스토어 확인
DEBUG_KEYSTORE="$HOME/.android/debug.keystore"
if [ ! -f "$DEBUG_KEYSTORE" ]; then
    echo -e "${YELLOW}⚠️  디버그 키스토어가 없습니다. 생성합니다...${NC}"
    keytool -genkey -v -keystore "$DEBUG_KEYSTORE" -alias androiddebugkey \
            -keyalg RSA -keysize 2048 -validity 10000 -storepass android \
            -keypass android -dname "CN=Android Debug,O=Android,C=US"
fi

echo -e "${BLUE}📱 ADB 연결 확인...${NC}"
if ! adb devices | grep -q "device$"; then
    echo -e "${RED}❌ 에뮬레이터 또는 디바이스가 연결되지 않았습니다${NC}"
    echo "Android Studio 에뮬레이터를 시작하거나 디바이스를 연결해주세요"
    exit 1
fi

echo -e "${BLUE}🔧 Gradle 빌드 시작...${NC}"
./gradlew assembleProfile

# 빌드된 APK 파일 확인
APK_UNSIGNED="app/build/outputs/apk/profile/app-profile-unsigned.apk"
APK_SIGNED="app/build/outputs/apk/profile/app-profile-signed.apk"

if [ ! -f "$APK_UNSIGNED" ]; then
    echo -e "${RED}❌ 빌드된 APK를 찾을 수 없습니다: $APK_UNSIGNED${NC}"
    exit 1
fi

echo -e "${BLUE}✍️  APK 서명 중...${NC}"
cp "$APK_UNSIGNED" "$APK_SIGNED"
"$APKSIGNER" sign --ks "$DEBUG_KEYSTORE" --ks-pass pass:android --key-pass pass:android "$APK_SIGNED"

echo -e "${BLUE}📦 APK 설치 중...${NC}"
# 기존 앱이 있으면 제거
adb shell pm list packages | grep -q "fr.neamar.kiss.lum7671" && {
    echo -e "${YELLOW}⚠️  기존 앱 제거 중...${NC}"
    adb uninstall fr.neamar.kiss.lum7671 || true
}

# 새 APK 설치
adb install "$APK_SIGNED"

echo -e "${BLUE}🏠 런처로 설정...${NC}"
# 디폴트 런처 설정 (사용자가 수동으로 선택해야 함)
adb shell am start -a android.intent.action.MAIN -c android.intent.category.HOME

echo -e "${GREEN}✅ 빌드 완료!${NC}"
echo -e "${GREEN}📱 앱이 설치되었습니다. 런처로 설정해주세요.${NC}"
echo -e "${BLUE}📊 프로파일 로그는 다음 경로에 저장됩니다:${NC}"
echo "   /storage/emulated/0/Android/data/fr.neamar.kiss.lum7671/files/kiss_profile_logs/"

# 로그 모니터링 시작 여부 묻기
echo ""
read -p "실시간 로그 모니터링을 시작하시겠습니까? (y/N): " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    echo -e "${BLUE}📋 로그 모니터링 시작... (Ctrl+C로 중단)${NC}"
    adb logcat | grep -E "(lum7671|KISS_PERF|ProfileManager)"
fi

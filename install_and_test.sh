#!/bin/bash

# KISS 런처 APK 설치 및 테스트 스크립트
# Intel CPU 맥북프로 2019 최적화

set -e

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}     KISS 런처 APK 설치 및 테스트${NC}"
echo -e "${BLUE}========================================${NC}"

# adb 연결 확인
echo -e "${BLUE}📱 연결된 디바이스 확인 중...${NC}"
if ! command -v adb &> /dev/null; then
    echo -e "${RED}❌ adb를 찾을 수 없습니다.${NC}"
    echo -e "${YELLOW}💡 Android SDK가 설치되어 있는지 확인하세요.${NC}"
    exit 1
fi

# 연결된 디바이스 목록
DEVICES=$(adb devices | grep -v "List of devices" | grep -E "device|emulator" | wc -l | xargs)

if [ "$DEVICES" -eq 0 ]; then
    echo -e "${RED}❌ 연결된 디바이스나 에뮬레이터가 없습니다.${NC}"
    echo -e "${YELLOW}💡 먼저 에뮬레이터를 실행하세요: ./run_emulator.sh${NC}"
    exit 1
fi

echo -e "${GREEN}✅ 연결된 디바이스: $DEVICES개${NC}"
adb devices | grep -E "device|emulator" | while read line; do
    echo -e "   ${GREEN}• $line${NC}"
done

# APK 파일 확인
DEBUG_APK="app/build/outputs/apk/debug/app-debug.apk"
RELEASE_APK="app/build/outputs/apk/release/app-release.apk"

APK_TO_INSTALL=""
if [ -f "$DEBUG_APK" ]; then
    APK_TO_INSTALL="$DEBUG_APK"
    echo -e "${GREEN}✅ 디버그 APK 발견: $DEBUG_APK${NC}"
elif [ -f "$RELEASE_APK" ]; then
    APK_TO_INSTALL="$RELEASE_APK"
    echo -e "${GREEN}✅ 릴리스 APK 발견: $RELEASE_APK${NC}"
else
    echo -e "${RED}❌ APK 파일을 찾을 수 없습니다.${NC}"
    echo -e "${YELLOW}💡 먼저 빌드하세요: ./gradlew assembleDebug${NC}"
    exit 1
fi

# APK 정보 확인
echo -e "${BLUE}📦 APK 정보:${NC}"
APK_SIZE=$(ls -lh "$APK_TO_INSTALL" | awk '{print $5}')
echo -e "   ${GREEN}• 파일 크기: $APK_SIZE${NC}"

# aapt를 사용해서 패키지 정보 확인 (가능한 경우)
if command -v aapt &> /dev/null; then
    PACKAGE_NAME=$(aapt dump badging "$APK_TO_INSTALL" 2>/dev/null | grep "package:" | sed "s/.*name='\([^']*\)'.*/\1/" || echo "kr.lum7671.kiss")
    VERSION_NAME=$(aapt dump badging "$APK_TO_INSTALL" 2>/dev/null | grep "versionName" | sed "s/.*versionName='\([^']*\)'.*/\1/" || echo "4.0.1")
    echo -e "   ${GREEN}• 패키지명: $PACKAGE_NAME${NC}"
    echo -e "   ${GREEN}• 버전: $VERSION_NAME${NC}"
else
    PACKAGE_NAME="kr.lum7671.kiss"
    echo -e "   ${YELLOW}• 패키지명: $PACKAGE_NAME (기본값)${NC}"
fi

# 기존 앱 제거 여부 확인
echo ""
read -p "기존 KISS 런처를 제거하고 새로 설치하시겠습니까? (y/N): " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    echo -e "${YELLOW}🗑️  기존 앱 제거 중...${NC}"
    adb uninstall "$PACKAGE_NAME" 2>/dev/null || echo -e "${YELLOW}⚠️  기존 앱이 설치되어 있지 않습니다.${NC}"
fi

# APK 설치
echo -e "${BLUE}📲 APK 설치 중...${NC}"
if adb install "$APK_TO_INSTALL"; then
    echo -e "${GREEN}✅ KISS 런처 설치 완료!${NC}"
else
    echo -e "${RED}❌ APK 설치 실패${NC}"
    exit 1
fi

# 앱 실행
echo ""
read -p "KISS 런처를 실행하시겠습니까? (y/N): " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    echo -e "${BLUE}🚀 KISS 런처 실행 중...${NC}"
    adb shell am start -n "$PACKAGE_NAME/kr.lum7671.kiss.MainActivity" || {
        echo -e "${YELLOW}⚠️  직접 실행에 실패했습니다. 앱 목록에서 KISS를 찾아 실행하세요.${NC}"
    }
fi

# AsyncTask → Coroutines 변환 테스트 가이드
echo ""
echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}        AsyncTask → Coroutines 테스트${NC}"
echo -e "${BLUE}========================================${NC}"
echo -e "${GREEN}🧪 다음 기능들을 테스트해보세요:${NC}"
echo ""
echo -e "${YELLOW}1. 설정 화면 비동기 로딩:${NC}"
echo -e "   • 설정 → 앱 제외 설정 (Coroutines로 변환됨)"
echo -e "   • 빠른 로딩과 부드러운 UI 확인"
echo ""
echo -e "${YELLOW}2. 아이콘 로딩 테스트:${NC}"
echo -e "   • 앱 목록 스크롤 (Result.setAsyncDrawable)"
echo -e "   • 태그 즐겨찾기 아이콘 (TagDummyResult)"
echo -e "   • 연락처 아이콘 (ContactsResult)"
echo -e "   • 단축키 아이콘 (ShortcutsResult)"
echo ""
echo -e "${YELLOW}3. 아이콘 팩 로딩:${NC}"
echo -e "   • 설정 → 인터페이스 → 아이콘 팩"
echo -e "   • 백그라운드 로딩 확인 (IconsHandler)"
echo ""
echo -e "${YELLOW}4. 메모리 누수 체크:${NC}"
echo -e "   • 앱 전환 후 다시 돌아오기"
echo -e "   • 설정 화면 여러 번 열고 닫기"
echo -e "   • 태그 클릭 반복"
echo ""
echo -e "${GREEN}✨ 모든 기능이 AsyncTask 없이 Coroutines로 작동합니다!${NC}"

# 로그 모니터링 제안
echo ""
read -p "로그 모니터링을 시작하시겠습니까? (y/N): " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    echo -e "${BLUE}📄 KISS 런처 로그 모니터링 시작...${NC}"
    echo -e "${YELLOW}🛑 종료하려면 Ctrl+C를 누르세요.${NC}"
    adb logcat | grep -i kiss
fi

echo -e "${GREEN}🎉 테스트 준비 완료!${NC}"

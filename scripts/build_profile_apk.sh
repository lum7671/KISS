#!/bin/bash

# KISS Profile APK 빌드 스크립트
# Galaxy Note 20 Ultra 프로파일링용 APK 자동 빌드

set -e  # 에러 시 스크립트 중단

echo "🚀 Profile APK 빌드 시작..."

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
INSTALL_APK=false
if adb devices 2>/dev/null | grep -q "device$"; then
    INSTALL_APK=true
    echo -e "${GREEN}✅ ADB 디바이스 발견${NC}"
else
    INSTALL_APK=false
    echo -e "${YELLOW}⚠️  ADB 디바이스 미연결. APK만 빌드합니다${NC}"
fi

echo -e "${BLUE}🔧 Gradle 빌드 시작...${NC}"
./gradlew assembleProfile

# 빌드 정보 설정
BUILD_DATE=$(date '+%Y%m%d_%H%M%S')

# build.gradle에서 버전 정보 추출
echo -e "${BLUE}📋 build.gradle에서 버전 정보 추출 중...${NC}"
VERSION_NAME=$(grep 'versionName' app/build.gradle | head -1 | sed 's/.*versionName[[:space:]]*"\([^"]*\)".*/\1/')
VERSION_CODE=$(grep 'versionCode' app/build.gradle | head -1 | sed 's/.*versionCode[[:space:]]*\([0-9]*\).*/\1/')

# 버전 이름에서 간단한 버전만 추출 (예: "4.0.1-based-on-3.22.1" -> "4.0.1")
VERSION=$(echo "$VERSION_NAME" | sed 's/^\([0-9]*\.[0-9]*\.[0-9]*\).*/v\1/')

echo -e "${GREEN}📝 추출된 버전 정보:${NC}"
echo "   버전명: $VERSION_NAME"
echo "   버전코드: $VERSION_CODE"
echo "   파일용 버전: $VERSION"

# 빌드된 APK 파일 확인
APK_ORIGINAL="app/build/outputs/apk/profile/app-profile.apk"
APK_RENAMED="app/build/outputs/apk/profile/KISS_${VERSION}_b${VERSION_CODE}_${BUILD_DATE}_profile.apk"
APK_SIGNED="app/build/outputs/apk/profile/KISS_${VERSION}_b${VERSION_CODE}_${BUILD_DATE}_profile_signed.apk"

# 원본 파일 확인 (unsigned 또는 일반 파일)
if [ ! -f "$APK_ORIGINAL" ]; then
    APK_ORIGINAL="app/build/outputs/apk/profile/app-profile-unsigned.apk"
    if [ ! -f "$APK_ORIGINAL" ]; then
        echo -e "${RED}❌ 빌드된 APK를 찾을 수 없습니다${NC}"
        echo "   확인한 경로: app/build/outputs/apk/profile/"
        ls -la app/build/outputs/apk/profile/ || echo "디렉토리가 존재하지 않습니다"
        exit 1
    fi
fi

# APK 파일명 변경
echo -e "${BLUE}📝 APK 파일명 변경...${NC}"
mv "$APK_ORIGINAL" "$APK_RENAMED"

# APK 크기 확인
APK_SIZE=$(du -h "$APK_RENAMED" | cut -f1)
echo -e "${GREEN}📦 빌드 완료! APK 크기: $APK_SIZE${NC}"
echo -e "${GREEN}📂 파일명: $(basename "$APK_RENAMED")${NC}"

echo -e "${BLUE}✍️  APK 서명 중...${NC}"
cp "$APK_RENAMED" "$APK_SIGNED"
"$APKSIGNER" sign --ks "$DEBUG_KEYSTORE" --ks-pass pass:android --key-pass pass:android "$APK_SIGNED"

echo -e "${BLUE}📦 APK 설치 중...${NC}"
# ADB 연결 시에만 설치
if [ "$INSTALL_APK" = true ]; then
    PACKAGE_NAME='kr.lum7671.kiss'
    
    # 기존 앱이 있으면 제거
    if adb shell pm list packages | grep -q "^package:$PACKAGE_NAME$"; then
        echo -e "${YELLOW}⚠️  기존 앱 제거 중...${NC}"
        adb uninstall "$PACKAGE_NAME" || true
    fi
    
    # 새 APK 설치
    adb install "$APK_SIGNED"
    
    echo -e "${BLUE}🏠 런처로 설정...${NC}"
    # 디폴트 런처 설정 (사용자가 수동으로 선택해야 함)
    adb shell am start -a android.intent.action.MAIN -c android.intent.category.HOME
else
    echo -e "${YELLOW}⚠️  ADB 미연결 상태이므로 APK 설치를 스킵합니다${NC}"
    echo -e "${YELLOW}   수동으로 APK를 설치해주세요: $APK_SIGNED${NC}"
fi

echo -e "${GREEN}✅ 빌드 완료!${NC}"
echo -e "${GREEN}📱 앱이 설치되었습니다. 런처로 설정해주세요.${NC}"
echo -e "${BLUE}� 빌드 정보:${NC}"
echo "   버전: KISS $VERSION Profile Edition"
echo "   빌드 번호: $VERSION_CODE"
echo "   빌드 날짜: $(date '+%Y년 %m월 %d일 %H:%M:%S')"
echo "   APK 크기: $APK_SIZE"
echo "   파일명: $(basename "$APK_SIGNED")"
echo -e "${BLUE}📊 프로파일 로그는 다음 경로에 저장됩니다:${NC}"
echo "   /storage/emulated/0/Android/data/kr.lum7671.kiss/files/kiss_profile_logs/"

# 로그 모니터링 시작 여부 묻기 (ADB 연결 시에만)
if [ "$INSTALL_APK" = true ]; then
    echo ""
    read -p "실시간 로그 모니터링을 시작하시겠습니까? (y/N): " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        echo -e "${BLUE}📋 로그 모니터링 시작... (Ctrl+C로 중단)${NC}"
        adb logcat | grep -E "(kr.lum7671|KISS_PERF|ProfileManager)"
    fi
fi

#!/bin/bash

# KISS Release APK 빌드 스크립트
# 최적화된 릴리즈 APK 자동 빌드 (1.2MB 경량화)

set -e  # 에러 시 스크립트 중단

echo "🚀 KISS Release APK 빌드 시작..."

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

# 릴리즈 키스토어 경로 설정
RELEASE_KEYSTORE="$HOME/.android/release.keystore"
DEBUG_KEYSTORE="$HOME/.android/debug.keystore"

# 키스토어 선택
if [ -f "$RELEASE_KEYSTORE" ]; then
    echo -e "${BLUE}🔐 릴리즈 키스토어를 사용합니다${NC}"
    KEYSTORE="$RELEASE_KEYSTORE"
    read -s -p "키스토어 비밀번호를 입력하세요: " KEYSTORE_PASS
    echo
    read -s -p "키 비밀번호를 입력하세요: " KEY_PASS
    echo
    read -p "키 alias를 입력하세요 [kiss_release]: " KEY_ALIAS
    KEY_ALIAS=${KEY_ALIAS:-kiss_release}
else
    echo -e "${YELLOW}⚠️  릴리즈 키스토어가 없습니다. 디버그 키스토어를 사용합니다${NC}"
    echo -e "${YELLOW}   프로덕션 배포시에는 릴리즈 키스토어를 생성하세요${NC}"
    
    if [ ! -f "$DEBUG_KEYSTORE" ]; then
        echo -e "${YELLOW}⚠️  디버그 키스토어가 없습니다. 생성합니다...${NC}"
        keytool -genkey -v -keystore "$DEBUG_KEYSTORE" -alias androiddebugkey \
                -keyalg RSA -keysize 2048 -validity 10000 -storepass android \
                -keypass android -dname "CN=Android Debug,O=Android,C=US"
    fi
    
    KEYSTORE="$DEBUG_KEYSTORE"
    KEYSTORE_PASS="android"
    KEY_PASS="android"
    KEY_ALIAS="androiddebugkey"
fi

echo -e "${BLUE}📱 ADB 연결 확인...${NC}"
if ! adb devices | grep -q "device$"; then
    echo -e "${YELLOW}⚠️  에뮬레이터 또는 디바이스가 연결되지 않았습니다${NC}"
    echo "설치를 건너뛰고 APK만 빌드합니다"
    INSTALL_APK=false
else
    INSTALL_APK=true
fi

# 클린 빌드 여부 확인
echo ""
read -p "클린 빌드를 수행하시겠습니까? (권장) (Y/n): " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Nn]$ ]]; then
    echo -e "${BLUE}🧹 클린 빌드 수행...${NC}"
    ./gradlew clean
fi

echo -e "${BLUE}🔧 Release 빌드 시작...${NC}"
echo -e "${BLUE}   - 최적화: ProGuard/R8 활성화${NC}"
echo -e "${BLUE}   - 크기: ~1.2MB (96% 최적화)${NC}"
echo -e "${BLUE}   - 패키지: fr.neamar.kiss.lum7671${NC}"

./gradlew assembleRelease

# 빌드된 APK 파일 확인
APK_UNSIGNED="app/build/outputs/apk/release/app-release-unsigned.apk"
APK_SIGNED="app/build/outputs/apk/release/app-release-signed.apk"

if [ ! -f "$APK_UNSIGNED" ]; then
    echo -e "${RED}❌ 빌드된 APK를 찾을 수 없습니다: $APK_UNSIGNED${NC}"
    exit 1
fi

# APK 크기 확인
APK_SIZE=$(du -h "$APK_UNSIGNED" | cut -f1)
echo -e "${GREEN}📦 빌드 완료! APK 크기: $APK_SIZE${NC}"

echo -e "${BLUE}✍️  APK 서명 중...${NC}"
cp "$APK_UNSIGNED" "$APK_SIGNED"

if [ "$KEYSTORE" = "$DEBUG_KEYSTORE" ]; then
    "$APKSIGNER" sign --ks "$KEYSTORE" --ks-pass pass:$KEYSTORE_PASS --key-pass pass:$KEY_PASS --ks-key-alias $KEY_ALIAS "$APK_SIGNED"
else
    "$APKSIGNER" sign --ks "$KEYSTORE" --ks-pass pass:$KEYSTORE_PASS --key-pass pass:$KEY_PASS --ks-key-alias $KEY_ALIAS "$APK_SIGNED"
fi

echo -e "${GREEN}✅ 서명 완료!${NC}"

# 서명 검증
echo -e "${BLUE}🔍 서명 검증 중...${NC}"
"$APKSIGNER" verify "$APK_SIGNED"
echo -e "${GREEN}✅ 서명 검증 완료!${NC}"

# APK 설치 (연결된 디바이스가 있는 경우)
if [ "$INSTALL_APK" = true ]; then
    echo ""
    read -p "APK를 설치하시겠습니까? (y/N): " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        echo -e "${BLUE}📦 APK 설치 중...${NC}"
        
        # 기존 앱이 있으면 제거 여부 확인
        if adb shell pm list packages | grep -q "fr.neamar.kiss.lum7671"; then
            echo -e "${YELLOW}⚠️  기존 KISS 앱이 설치되어 있습니다${NC}"
            read -p "제거하고 새로 설치하시겠습니까? (y/N): " -n 1 -r
            echo
            if [[ $REPLY =~ ^[Yy]$ ]]; then
                echo -e "${YELLOW}⚠️  기존 앱 제거 중...${NC}"
                adb uninstall fr.neamar.kiss.lum7671 || true
            fi
        fi
        
        # 새 APK 설치
        adb install "$APK_SIGNED"
        
        echo -e "${BLUE}🏠 런처로 설정...${NC}"
        # 홈 화면으로 이동하여 런처 선택 화면 표시
        adb shell am start -a android.intent.action.MAIN -c android.intent.category.HOME
        
        echo -e "${GREEN}📱 앱이 설치되었습니다. 런처로 설정해주세요.${NC}"
    fi
fi

# 최종 빌드 정보 출력
echo ""
echo -e "${GREEN}🎉 KISS Release APK 빌드 완료!${NC}"
echo -e "${BLUE}📄 빌드 정보:${NC}"
echo "   버전: KISS v4.0.0 Optimized Edition"
echo "   패키지: fr.neamar.kiss.lum7671"
echo "   APK 크기: $APK_SIZE"
echo "   파일 위치: $APK_SIGNED"
echo ""
echo -e "${BLUE}🚀 주요 최적화 사항:${NC}"
echo "   ⚡ 3-Tier Icon Caching (검색 성능 99% 향상)"
echo "   💾 Hybrid Memory Database (10x+ 빠른 쿼리)"
echo "   🔋 Smart Screen State Management"
echo "   📱 Android 13+ 최적화"
echo "   📦 96% 크기 최적화 (31MB → 1.2MB)"
echo ""

# 릴리즈 노트 생성 여부 확인
read -p "릴리즈 노트를 생성하시겠습니까? (y/N): " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    RELEASE_NOTES="release_notes_$(date +%Y%m%d_%H%M%S).md"
    cat > "$RELEASE_NOTES" << EOF
# KISS v4.0.0 Release Notes

## 빌드 정보
- **빌드 날짜**: $(date '+%Y년 %m월 %d일 %H:%M:%S')
- **APK 크기**: $APK_SIZE
- **패키지명**: fr.neamar.kiss.lum7671
- **서명**: $([ "$KEYSTORE" = "$DEBUG_KEYSTORE" ] && echo "Debug" || echo "Release")

## 주요 최적화 사항
- ⚡ **검색 성능**: 99% 향상 (1-6ms 응답)
- 💾 **메모리 효율**: Hybrid Database + Smart Caching
- 🔋 **배터리 최적화**: Smart Screen State Management
- 📦 **크기 최적화**: 96% 감소 (31MB → 1.2MB)

## 테스트 환경
- Android 13+ 권장
- 최소 RAM: 2GB
- 저장공간: 10MB

## 설치 방법
\`\`\`bash
adb install $APK_SIGNED
\`\`\`

## 성능 프로파일링 (옵션)
Profile 빌드 사용 시:
\`\`\`bash
./build_profile_apk.sh
python3 analyze_profile_logs.py
\`\`\`
EOF
    echo -e "${GREEN}📝 릴리즈 노트 생성 완료: $RELEASE_NOTES${NC}"
fi

echo -e "${BLUE}💡 다음 단계:${NC}"
echo "   1. 앱을 기본 런처로 설정"
echo "   2. 일반 사용하며 성능 확인"
echo "   3. 이슈 발생 시 Profile 빌드로 분석"
echo "   4. GitHub에 피드백 및 이슈 리포트"

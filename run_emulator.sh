#!/bin/bash

# KISS 런처 테스트용 Android 에뮬레이터 실행 스크립트
# Intel CPU 맥북프로 2019 최적화

set -e

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}  KISS 런처 Android 에뮬레이터 실행${NC}"
echo -e "${BLUE}  Intel CPU 맥북프로 2019 최적화${NC}"
echo -e "${BLUE}========================================${NC}"

# Android SDK 경로 확인
if [ -z "$ANDROID_HOME" ]; then
    echo -e "${RED}❌ ANDROID_HOME 환경 변수가 설정되지 않았습니다.${NC}"
    echo -e "${YELLOW}💡 다음 경로로 설정해보세요: export ANDROID_HOME=/Users/\$USER/Library/Android/sdk${NC}"
    exit 1
fi

echo -e "${GREEN}✅ Android SDK 경로: $ANDROID_HOME${NC}"

# 에뮬레이터 실행 파일 확인
EMULATOR_PATH="$ANDROID_HOME/emulator/emulator"
if [ ! -f "$EMULATOR_PATH" ]; then
    echo -e "${RED}❌ 에뮬레이터를 찾을 수 없습니다: $EMULATOR_PATH${NC}"
    exit 1
fi

echo -e "${GREEN}✅ 에뮬레이터 실행 파일 확인됨${NC}"

# 사용 가능한 AVD 목록 확인
echo -e "${BLUE}📱 사용 가능한 Android Virtual Device:${NC}"
AVDS=$("$EMULATOR_PATH" -list-avds)

if [ -z "$AVDS" ]; then
    echo -e "${RED}❌ 사용 가능한 AVD가 없습니다.${NC}"
    echo -e "${YELLOW}💡 Android Studio에서 AVD를 생성해주세요.${NC}"
    exit 1
fi

echo "$AVDS" | while read line; do
    echo -e "   ${GREEN}• $line${NC}"
done

# 기본 AVD 설정 (Intel CPU 최적화)
DEFAULT_AVD="Medium_Phone_API_36"
SELECTED_AVD=""

# 명령행 인수로 AVD 지정 가능
if [ "$1" ]; then
    SELECTED_AVD="$1"
    echo -e "${BLUE}🎯 지정된 AVD: $SELECTED_AVD${NC}"
else
    # 기본 AVD가 있는지 확인
    if echo "$AVDS" | grep -q "$DEFAULT_AVD"; then
        SELECTED_AVD="$DEFAULT_AVD"
        echo -e "${BLUE}🎯 기본 AVD 선택: $SELECTED_AVD${NC}"
    else
        # 첫 번째 AVD 사용
        SELECTED_AVD=$(echo "$AVDS" | head -n 1)
        echo -e "${YELLOW}⚠️  기본 AVD를 찾을 수 없어 첫 번째 AVD를 사용: $SELECTED_AVD${NC}"
    fi
fi

# AVD가 x86_64인지 확인 (Intel CPU 최적화)
AVD_CONFIG_PATH="$HOME/.android/avd/${SELECTED_AVD}.avd/config.ini"
if [ -f "$AVD_CONFIG_PATH" ]; then
    ABI_TYPE=$(grep "abi.type" "$AVD_CONFIG_PATH" | cut -d'=' -f2 | xargs)
    if [ "$ABI_TYPE" = "x86_64" ]; then
        echo -e "${GREEN}✅ Intel CPU 최적화 확인됨 (x86_64)${NC}"
    else
        echo -e "${YELLOW}⚠️  AVD ABI: $ABI_TYPE (Intel CPU에는 x86_64가 권장됨)${NC}"
    fi
fi

echo -e "${BLUE}🚀 에뮬레이터 실행 중...${NC}"
echo -e "${YELLOW}💡 팁: 에뮬레이터가 실행되면 KISS 런처를 테스트할 수 있습니다.${NC}"

# Intel CPU 최적화 옵션들
EMULATOR_OPTS=""
EMULATOR_OPTS="$EMULATOR_OPTS -avd $SELECTED_AVD"
EMULATOR_OPTS="$EMULATOR_OPTS -no-audio"                    # 오디오 비활성화 (성능 향상)
EMULATOR_OPTS="$EMULATOR_OPTS -no-snapshot-save"            # 스냅샷 저장 비활성화 (빠른 종료)
EMULATOR_OPTS="$EMULATOR_OPTS -gpu host"                    # GPU 가속화 (Intel 내장 그래픽)
EMULATOR_OPTS="$EMULATOR_OPTS -memory 4096"                 # 메모리 4GB 할당
EMULATOR_OPTS="$EMULATOR_OPTS -cores 4"                     # CPU 코어 4개 사용
EMULATOR_OPTS="$EMULATOR_OPTS -netfast"                     # 네트워크 최적화
EMULATOR_OPTS="$EMULATOR_OPTS -cache-size 1024"             # 캐시 크기 1GB

# wipe-data 옵션 확인
if [ "$2" = "--clean" ]; then
    echo -e "${YELLOW}🧹 데이터 초기화 모드로 실행${NC}"
    EMULATOR_OPTS="$EMULATOR_OPTS -wipe-data"
fi

echo -e "${BLUE}📋 에뮬레이터 옵션:${NC}"
echo -e "   ${GREEN}$EMULATOR_OPTS${NC}"

echo ""
echo -e "${GREEN}🎮 에뮬레이터 실행 명령어:${NC}"
echo -e "${GREEN}$EMULATOR_PATH $EMULATOR_OPTS${NC}"
echo ""

# 백그라운드에서 실행할지 묻기
read -p "백그라운드에서 실행하시겠습니까? (y/N): " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    echo -e "${BLUE}🔄 백그라운드에서 에뮬레이터 실행 중...${NC}"
    $EMULATOR_PATH $EMULATOR_OPTS > emulator.log 2>&1 &
    EMULATOR_PID=$!
    echo -e "${GREEN}✅ 에뮬레이터 프로세스 ID: $EMULATOR_PID${NC}"
    echo -e "${YELLOW}📄 로그는 emulator.log 파일에서 확인하세요.${NC}"
    echo -e "${YELLOW}🛑 종료하려면: kill $EMULATOR_PID${NC}"
else
    echo -e "${BLUE}🔄 포그라운드에서 에뮬레이터 실행 중...${NC}"
    echo -e "${YELLOW}🛑 종료하려면 Ctrl+C를 누르세요.${NC}"
    $EMULATOR_PATH $EMULATOR_OPTS
fi

echo ""
echo -e "${GREEN}✨ 에뮬레이터 실행 완료!${NC}"
echo -e "${YELLOW}📱 에뮬레이터가 부팅되면 KISS 런처 APK를 설치할 수 있습니다:${NC}"
echo -e "${GREEN}   adb install app/build/outputs/apk/debug/app-debug.apk${NC}"

#!/bin/bash

# KISS 프로젝트 정적 분석 도구
# 사용법:
#   ./scripts/analyze_code.sh          # 전체 분석 실행
#   ./scripts/analyze_code.sh summary  # 결과 요약만 출력
#   ./scripts/analyze_code.sh help     # 도움말

set -e

# 프로젝트 루트로 이동
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
cd "$PROJECT_ROOT"

# 색상 코드
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 도움말 출력
show_help() {
    echo "======================================"
    echo "KISS 프로젝트 정적 분석 도구"
    echo "======================================"
    echo ""
    echo "사용법:"
    echo "  $0 [command]"
    echo ""
    echo "Commands:"
    echo "  (없음)      - 전체 정적 분석 실행"
    echo "  summary     - 기존 분석 결과 요약 출력"
    echo "  detekt      - Detekt만 실행"
    echo "  lint        - Android Lint만 실행"
    echo "  deps        - 의존성 분석만 실행"
    echo "  clean       - 분석 결과 삭제"
    echo "  help        - 이 도움말 출력"
    echo ""
    echo "예시:"
    echo "  $0              # 모든 분석 실행"
    echo "  $0 summary      # 결과 요약"
    echo "  $0 detekt       # Kotlin 코드만 분석"
    echo ""
}

# 분석 결과 요약 출력
show_summary() {
    echo ""
    echo "======================================"
    echo "📊 정적 분석 결과 요약"
    echo "======================================"
    echo ""

    # Detekt 결과
    if [ -f "app/build/reports/detekt/detekt.xml" ]; then
        echo -e "${BLUE}🔍 Detekt (Kotlin 정적 분석)${NC}"
        DETEKT_ISSUES=$(grep -c "<error" app/build/reports/detekt/detekt.xml 2>/dev/null || echo "0")
        if [ "$DETEKT_ISSUES" -eq 0 ]; then
            echo -e "  ${GREEN}✅ 이슈 없음${NC}"
        else
            echo -e "  ${YELLOW}⚠️  발견된 이슈: $DETEKT_ISSUES개${NC}"
        fi
        echo "  📄 리포트: app/build/reports/detekt/detekt.html"
        echo ""
    else
        echo -e "${BLUE}🔍 Detekt${NC}: ${YELLOW}결과 없음 (실행 필요)${NC}"
        echo ""
    fi

    # Lint 결과
    if [ -f "app/build/reports/lint-results-debug.xml" ]; then
        echo -e "${BLUE}🐛 Android Lint${NC}"
        LINT_ERRORS=$(grep -c 'severity="Error"' app/build/reports/lint-results-debug.xml 2>/dev/null || echo "0")
        LINT_WARNINGS=$(grep -c 'severity="Warning"' app/build/reports/lint-results-debug.xml 2>/dev/null || echo "0")

        if [ "$LINT_ERRORS" -eq 0 ] && [ "$LINT_WARNINGS" -eq 0 ]; then
            echo -e "  ${GREEN}✅ 이슈 없음${NC}"
        else
            [ "$LINT_ERRORS" -gt 0 ] && echo -e "  ${RED}❌ 에러: $LINT_ERRORS개${NC}"
            [ "$LINT_WARNINGS" -gt 0 ] && echo -e "  ${YELLOW}⚠️  경고: $LINT_WARNINGS개${NC}"
        fi
        echo "  📄 리포트: app/build/reports/lint-results-debug.html"
        echo ""
    else
        echo -e "${BLUE}🐛 Android Lint${NC}: ${YELLOW}결과 없음 (실행 필요)${NC}"
        echo ""
    fi

    # 의존성 업데이트
    if [ -f "build/dependencyUpdates/report.txt" ]; then
        echo -e "${BLUE}📦 의존성 업데이트${NC}"
        OUTDATED=$(grep -c "dependencies have later" build/dependencyUpdates/report.txt 2>/dev/null || echo "0")
        if [ "$OUTDATED" -gt 0 ]; then
            echo -e "  ${YELLOW}⚠️  업데이트 가능한 라이브러리 있음${NC}"
        else
            echo -e "  ${GREEN}✅ 모든 의존성 최신${NC}"
        fi
        echo "  📄 리포트: build/dependencyUpdates/report.txt"
        echo ""
    else
        echo -e "${BLUE}📦 의존성${NC}: ${YELLOW}결과 없음 (실행 필요)${NC}"
        echo ""
    fi

    echo "======================================"
    echo ""
    echo "💡 상세 리포트 열기:"
    echo "  open app/build/reports/detekt/detekt.html"
    echo "  open app/build/reports/lint-results-debug.html"
    echo ""
}

# 개별 분석 실행
run_detekt() {
    echo -e "${BLUE}🔍 Detekt 실행 중...${NC}"
    ./gradlew detekt --no-configuration-cache
    echo -e "${GREEN}✅ Detekt 완료${NC}"
}

run_lint() {
    echo -e "${BLUE}🐛 Android Lint 실행 중...${NC}"
    ./gradlew lintDebug --no-configuration-cache
    echo -e "${GREEN}✅ Lint 완료${NC}"
}

run_deps() {
    echo -e "${BLUE}📦 의존성 분석 실행 중...${NC}"
    ./gradlew dependencyUpdates --no-configuration-cache
    echo -e "${GREEN}✅ 의존성 분석 완료${NC}"
}

# 결과 삭제
clean_results() {
    echo "🗑️  분석 결과 삭제 중..."
    rm -rf app/build/reports/detekt/
    rm -rf app/build/reports/lint*/
    rm -rf build/dependencyUpdates/
    echo -e "${GREEN}✅ 삭제 완료${NC}"
}

# 전체 분석 실행
run_all() {
    echo "======================================"
    echo "KISS 프로젝트 정적 분석 실행"
    echo "======================================"
    echo ""
    echo "📍 작업 디렉토리: $(pwd)"
    echo ""

    # 1. 의존성 분석
    echo -e "${BLUE}📦 1/3 의존성 업데이트 확인...${NC}"
    ./gradlew dependencyUpdates --no-configuration-cache || echo -e "${YELLOW}⚠️  의존성 분석 실패 (계속 진행)${NC}"

    # 2. Detekt
    echo ""
    echo -e "${BLUE}🔍 2/3 Detekt (Kotlin 정적 분석) 실행...${NC}"
    ./gradlew detekt --no-configuration-cache || echo -e "${YELLOW}⚠️  Detekt 분석 실패 (계속 진행)${NC}"

    # 3. Android Lint
    echo ""
    echo -e "${BLUE}🐛 3/3 Android Lint 실행...${NC}"
    ./gradlew lintDebug --no-configuration-cache || echo -e "${YELLOW}⚠️  Lint 분석 실패 (계속 진행)${NC}"

    echo ""
    echo -e "${GREEN}======================================"
    echo "✅ 전체 분석 완료!"
    echo "======================================${NC}"

    # 자동으로 요약 출력
    show_summary
}

# 메인 로직
case "${1:-}" in
    help|--help|-h)
        show_help
        ;;
    summary|--summary)
        show_summary
        ;;
    detekt)
        run_detekt
        show_summary
        ;;
    lint)
        run_lint
        show_summary
        ;;
    deps)
        run_deps
        show_summary
        ;;
    clean)
        clean_results
        ;;
    "")
        run_all
        ;;
    *)
        echo -e "${RED}❌ 알 수 없는 명령: $1${NC}"
        echo ""
        show_help
        exit 1
        ;;
esac

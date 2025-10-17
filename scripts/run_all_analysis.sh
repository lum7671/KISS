#!/bin/bash

set -e  # 에러 발생 시 중단

echo "======================================"
echo "KISS 프로젝트 전체 정적 분석 실행"
echo "======================================"
echo ""

# 프로젝트 루트로 이동
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
cd "$PROJECT_ROOT"

echo "📍 작업 디렉토리: $(pwd)"
echo ""

# 1. 의존성 분석
echo "📦 1/4 의존성 업데이트 확인..."
./gradlew dependencyUpdates --no-configuration-cache || echo "⚠️  의존성 분석 실패 (계속 진행)"

# 2. Detekt (Kotlin 정적 분석)
echo ""
echo "🔍 2/4 Detekt (Kotlin 정적 분석) 실행..."
./gradlew detekt --no-configuration-cache || echo "⚠️  Detekt 분석 실패 (계속 진행)"

# 3. Android Lint
echo ""
echo "🐛 3/4 Android Lint 실행..."
./gradlew lintDebug --no-configuration-cache || echo "⚠️  Lint 분석 실패 (계속 진행)"

# 4. Error Prone (컴파일 타임 체크)
echo ""
echo "⚙️  4/4 Error Prone (컴파일 검사)..."
./gradlew compileDebugJavaWithJavac --no-configuration-cache || echo "⚠️  Error Prone 분석 실패 (계속 진행)"

echo ""
echo "======================================"
echo "✅ 분석 완료!"
echo "======================================"
echo ""
echo "📊 리포트 위치:"
echo "  - 의존성: build/dependencyUpdates/report.txt"
echo "  - Detekt: app/build/reports/detekt/detekt.html"
echo "  - Lint: app/build/reports/lint-results-debug.html"
echo "  - Lint (XML): app/build/reports/lint-results-debug.xml"
echo ""
echo "📂 리포트 열기:"
echo "  open app/build/reports/detekt/detekt.html"
echo "  open app/build/reports/lint-results-debug.html"
echo ""
echo "📝 요약 생성:"
echo "  scripts/analyze_code.sh summary"
echo ""

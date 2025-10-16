#!/bin/bash

echo "======================================"
echo "KISS 프로젝트 전체 정적 분석 실행"
echo "======================================"
echo ""

cd /Users/1001028/git/KISS

# 1. 의존성 분석
echo "📦 1/3 의존성 업데이트 확인..."
./gradlew dependencyUpdates --no-configuration-cache -q

# 2. Kotlin 정적 분석
echo ""
echo "🔍 2/3 Detekt 실행..."
./gradlew detekt --no-configuration-cache -q

# 3. Android Lint
echo ""
echo "🐛 3/3 Android Lint 실행..."
./gradlew lintDebug --no-configuration-cache -q

echo ""
echo "======================================"
echo "✅ 분석 완료!"
echo "======================================"
echo ""
echo "📊 리포트 위치:"
echo "  - 의존성: build/dependencyUpdates/report.txt"
echo "  - Detekt: app/build/reports/detekt/detekt.html"
echo "  - Lint: app/build/reports/lint-results-debug.html"
echo ""
echo "📂 리포트 열기:"
echo "  open app/build/reports/detekt/detekt.html"
echo "  open app/build/reports/lint-results-debug.html"
echo ""

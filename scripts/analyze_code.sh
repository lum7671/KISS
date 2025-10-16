#!/bin/bash

echo "======================================"
echo "KISS 프로젝트 정적 분석 도구 모음"
echo "======================================"
echo ""

# 1. 사용하지 않는 의존성 검사
echo "1. 사용하지 않는 라이브러리 검사..."
echo "./gradlew buildHealth"
echo ""

# 2. Detekt으로 Kotlin 코드 분석
echo "2. Kotlin 코드 정적 분석 (Detekt)..."
echo "./gradlew detekt"
echo "   리포트: app/build/reports/detekt/"
echo ""

# 3. Android Lint 분석
echo "3. Android Lint 검사..."
echo "./gradlew lint"
echo "   리포트: app/build/reports/lint/"
echo ""

# 4. 의존성 업데이트 확인
echo "4. 업데이트 가능한 의존성 확인..."
echo "./gradlew dependencyUpdates"
echo ""

# 5. 사용하지 않는 리소스 검사
echo "5. 사용하지 않는 리소스 검사..."
echo "./gradlew lint"
echo "   (UnusedResources 경고 확인)"
echo ""

echo "======================================"
echo "전체 분석 실행 방법:"
echo "======================================"
echo ""
echo "# 모든 분석 한번에 실행:"
echo "./gradlew clean buildHealth detekt lint dependencyUpdates"
echo ""
echo "# 또는 개별 실행:"
echo "./gradlew buildHealth        # 의존성 분석"
echo "./gradlew detekt             # Kotlin 코드 분석"
echo "./gradlew lint               # Android 리소스/코드 분석"
echo "./gradlew dependencyUpdates  # 업데이트 가능한 라이브러리"
echo ""

#!/bin/bash

echo "======================================"
echo "KISS 프로젝트 정적 분석 자동 수정"
echo "======================================"
echo ""

cd /Users/1001028/git/KISS

# 1. Detekt 자동 포맷팅 실행 (아직 detektFormat task가 없을 수 있음)
echo "1. Detekt 코드 스타일 자동 수정 시도..."
./gradlew detektFormat --no-configuration-cache 2>/dev/null || echo "   (detektFormat task 없음 - 스킵)"
echo ""

# 2. Lint baseline 업데이트
echo "2. Lint baseline 업데이트 (수정된 이슈 제거)..."
./gradlew updateLintBaseline --no-configuration-cache
echo ""

# 3. 결과 요약
echo ""
echo "======================================"
echo "완료!"
echo "======================================"
echo ""
echo "변경된 파일 확인:"
echo "  git status"
echo ""
echo "변경 내용 확인:"
echo "  git diff"
echo ""
echo "커밋:"
echo "  git add -A"
echo "  git commit -m 'chore: 정적 분석 자동 수정 및 baseline 업데이트'"
echo ""

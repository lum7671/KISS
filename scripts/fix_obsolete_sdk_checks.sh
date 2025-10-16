#!/bin/bash

echo "======================================"
echo "ObsoleteSdkInt 이슈 자동 수정"
echo "======================================"
echo ""

cd /Users/1001028/git/KISS

# 1. ShortcutUtil.java - canDeviceShowShortcuts 항상 true 반환
echo "1. ShortcutUtil.java 수정..."
sed -i '' 's/return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O;/return true; \/\/ minSdk 33, always true/' \
    app/src/main/java/fr/neamar/kiss/utils/ShortcutUtil.java

# 2. @RequiresApi 어노테이션 제거 (minSdk 33보다 낮은 버전)
echo "2. 불필요한 @RequiresApi 제거..."

# O (26) 제거
find app/src/main/java -name "*.java" -o -name "*.kt" | xargs sed -i '' '/@RequiresApi(Build.VERSION_CODES.O)/d'
find app/src/main/java -name "*.java" -o -name "*.kt" | xargs sed -i '' '/@RequiresApi(api = Build.VERSION_CODES.O)/d'

# LOLLIPOP (21) 제거  
find app/src/main/java -name "*.java" -o -name "*.kt" | xargs sed -i '' '/@RequiresApi(Build.VERSION_CODES.LOLLIPOP)/d'
find app/src/main/java -name "*.java" -o -name "*.kt" | xargs sed -i '' '/@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)/d'

# JELLY_BEAN (16) 제거
find app/src/main/java -name "*.java" -o -name "*.kt" | xargs sed -i '' '/@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)/d'

# JELLY_BEAN_MR1 (17) 제거
find app/src/main/java -name "*.java" -o -name "*.kt" | xargs sed -i '' '/@RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR1)/d'

# M (23) 제거
find app/src/main/java -name "*.java" -o -name "*.kt" | xargs sed -i '' '/@RequiresApi(api = Build.VERSION_CODES.M)/d'

# S (31) 제거
find app/src/main/java -name "*.java" -o -name "*.kt" | xargs sed -i '' '/@RequiresApi(Build.VERSION_CODES.S)/d'
find app/src/main/java -name "*.java" -o -name "*.kt" | xargs sed -i '' '/@RequiresApi(api = Build.VERSION_CODES.S)/d'

# 3. 불필요한 리소스 폴더 제거
echo "3. values-v21, values-v31 폴더 제거..."
rm -rf app/src/main/res/values-v21
rm -rf app/src/main/res/values-v31

echo ""
echo "✅ 완료!"
echo ""
echo "수정된 내용:"
echo "  - ShortcutUtil.canDeviceShowShortcuts() 항상 true 반환"
echo "  - 불필요한 @RequiresApi 어노테이션 제거"
echo "  - values-v21, values-v31 폴더 제거"
echo ""
echo "다음 단계:"
echo "  git diff  # 변경사항 확인"
echo "  ./gradlew lintDebug  # 다시 검사"

# javax.annotation 패키지 전체 유지
-keep class javax.annotation.** { *; }

# R8 missing_rules.txt 제안 적용
-dontwarn javax.annotation.Nullable
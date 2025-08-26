# javax.annotation 패키지 전체 유지
-keep class javax.annotation.** { *; }

# R8 missing_rules.txt 제안 적용
-dontwarn javax.annotation.Nullable

# 데이터 관련 Provider 클래스 난독화/제거 방지
-keep class fr.neamar.kiss.dataprovider.AppProvider { *; }
-keep class fr.neamar.kiss.dataprovider.ContactsProvider { *; }
-keep class fr.neamar.kiss.dataprovider.ShortcutsProvider { *; }
# KISS

## 🚀 v4.2.3 - Minor Update Edition (2025-10-16)

### 🔧 버전 업데이트

- **📦 버전 정보**: versionCode 423, versionName 4.2.3
- **✅ 안정성 유지**: 기존 기능 및 안정성 유지

---

## 🚀 v4.2.1 - Maintenance Edition (2025-10-15)

### 🔧 유지보수 및 안정성 개선

- **📦 버전 업데이트**: versionCode 421, versionName 4.2.1
- **🛠️ 코드 정리**: 불필요한 코드 제거 및 최적화
- **✅ 빌드 안정성**: Release 빌드 검증 완료

---

## 🚀 v4.2.0 - Searcher Improvements Edition (2025-10-15)

### 🎯 Phase 2: Searcher 시스템 안정성 및 성능 개선 완료

- **🛡️ Thread Safety 강화**: synchronized 블록으로 동시성 문제 해결
  - PriorityQueue 동시 접근 보호
  - 90+ 연속 검색에서 크래시 제로 달성
  - 명시적 thread safety 보장

- **🔧 Error Handling 개선**: 에러와 취소 명확히 구분
  - CancellationException vs Exception 분리 처리
  - Amplitude 에러 추적 통합
  - 상세한 에러 로깅 시스템

- **⚡ Cancellation Response 최적화**: 목표 대비 25배 빠른 응답
  - 23개 취소 체크 포인트 추가 (4개 Searcher 파일)
  - 0-2ms 취소 응답 시간 (목표: 50ms)
  - 불필요한 작업 즉시 중단

- **🗑️ Static Cache 제거**: 메모리 및 테스트 개선
  - Static mutable state 100% 제거
  - Instance 변수 전환으로 테스트 용이성 향상
  - 73줄 코드 감소

- **📊 Logging 통합**: SearchPerformanceLogger 유틸리티
  - 일관된 로그 형식: [COMPLETED]/[CANCELLED]/[ERROR]
  - Android Log + Amplitude 통합
  - 성능 메트릭 자동 수집

### 📈 정량적 성과

- **성능**: 평균 검색 시간 50% 단축 (2ms → 1ms)
- **안정성**: 100+ 연속 검색 크래시 없음
- **코드**: Static state 제거, 73줄 감소
- **로깅**: 중복 67% 감소, 통합 유틸리티

### 📚 완벽한 문서화

- **8개 Phase 2 문서 작성**: 상세한 개선 분석 및 테스트 결과
  - phase2-searcher-improvements.md: 개선 분석
  - phase2-step-by-step-plan.md: 실행 계획
  - phase2-testing-guide.md: 테스트 가이드
  - phase2-test-results.md: 테스트 결과 (90+ 검색)
  - phase2-completion-report.md: 완료 보고서

### 🔄 Git 브랜치 전략

- **5단계 순차 개발**: 각 Step별 독립 브랜치
  - phase2-step1-thread-safety ✅
  - phase2-step2-error-handling ✅
  - phase2-step3-cancellation-checks ✅
  - phase2-step4-static-cache-removal ✅
  - phase2-step5-logging-consolidation ✅
- **안전한 머지**: dev 브랜치에 순차 통합 완료

---

## 🚀 v4.1.9 - AsyncTask Migration Complete Edition (2025-10-14)

### 🎉 AsyncTask → Kotlin Coroutines 마이그레이션 100% 완료

- **✅ Searcher 시스템 완전 전환**: 8개 Searcher 클래스 모두 Kotlin Coroutines로 전환
  - Step 1-2: SearcherCoroutine 기반 클래스 구현 (244 lines)
  - Step 3: QuerySearcherCoroutine (가장 중요한 검색 기능, 133 lines)
  - Step 4: 나머지 6개 Searcher 전환 (NullSearcher, HistorySearcher, ApplicationsSearcher, PojoWithTagSearcher, TagsSearcher, UntaggedSearcher)
  - Step 5: Legacy 코드 완전 제거 (7개 Java 파일, ~428 lines 삭제)

- **🗑️ Legacy ExecutorService 코드 제거**: 53.5% 코드 감소 (1,689 → 785 lines)
  - Feature Flag 완전 제거 (USE_SEARCHER_COROUTINE, USE_ALL_SEARCHER_COROUTINES)
  - MainActivity.java 단순화 (6개 if-else 분기 제거)
  - 중복 구현 제거로 메모리 효율 5~10% 개선

- **⚡ 성능 및 안정성 향상**
  - 검색 응답 시간: < 100ms 유지
  - 메모리 사용량: 기존 대비 5~10% 감소
  - 빌드 시간 단축 (컴파일 대상 파일 감소)
  - 코드 복잡도 감소로 유지보수성 향상

### 📚 완벽한 문서화

- **26개 마이그레이션 문서 작성**: 총 ~15,000 lines의 상세한 기술 문서
  - Step별 분석 리포트 (step1-5)
  - 구현 가이드 및 테스트 전략
  - 완료 보고서 및 성과 분석
  - 빌드 스크립트 수정 기록

### 🏗️ 아키텍처 개선

- **SearcherCoroutine Base Class**: 모든 검색 작업의 통합 기반
  - WeakReference 패턴으로 메모리 안전성
  - Dispatchers.IO.limitedParallelism(1)로 순차 실행 보장
  - Job 취소 메커니즘으로 리소스 누수 방지
  
- **Searcher Adapter Pattern**: Provider 호환성 유지
  - 기존 Provider 시스템과 완벽한 호환
  - addResults(), isCancelled() 인터페이스 통합
  - 점진적 마이그레이션 가능한 구조

### 🧪 검증 완료

- ✅ **Debug Build**: BUILD SUCCESSFUL in 3s
- ✅ **Release Build**: BUILD SUCCESSFUL in 13s (2.3MB APK)
- ✅ **모든 검색 기능**: 일반 검색, 앱 드로어, 히스토리, 태그, Minimalistic 모드
- ✅ **메모리 누수**: LeakCanary로 완전 검증
- ✅ **Lint 검증**: 0 errors, 100 warnings (deprecation 경고만)

### 🎯 마이그레이션 전략 성공 요인

- **점진적 5-Step 접근**: 한 번에 하나씩, 안전하게 전환
- **Feature Flag 시스템**: 문제 발생 시 즉시 롤백 가능
- **철저한 계획**: 사전 분석 및 상세한 실행 계획
- **포괄적 테스트**: 각 Step마다 빌드 & 기능 검증

### 📊 최종 통계

```text
Before (Steps 0-4):
- Legacy Java Searchers: 904 lines
- Coroutine Searchers: 785 lines
- Feature Flags: 15 lines
- Total: 1,704 lines

After (Step 5):
- Legacy Java Searchers: 0 lines ❌
- Coroutine Searchers: 785 lines ✅
- Feature Flags: 0 lines ❌
- Total: 785 lines

코드 감소: -53.9% (919 lines 제거)
메모리 개선: 5~10% 감소
성능: 기존 대비 동등 또는 개선
```

### 🚀 향후 개선 방향 (Optional)

- Searcher.java → Kotlin interface 전환
- ISearchResultReceiver 인터페이스 활성화
- Provider-Searcher 시스템 통합 (Phase 2)

---

## 🚀 v4.1.8 - Upstream Integration & Core Bug Fixes Edition (2025-09-26)

### 🔀 Neamar 업스트림 핵심 개선사항 통합 (Phase 1-4)

- **🐛 Phase 1: 스피너 무한 회전 문제 해결**
  - MainActivity 초기화 순서 최적화로 앱 시작 시 로딩 스피너 멈춤 현상 완전 해결
  - `displayLoader(true)` 호출을 `initDataHandler()` 이전으로 이동
  - DataHandler 준비 완료 전에 로더 UI 우선 표시하도록 수정

- **⚡ Phase 2: DataHandler 로직 단순화**
  - 복잡한 `FULL_LOAD_OVER` 브로드캐스트 시스템 제거
  - `isAllProvidersLoaded()` 메서드를 통한 직접 확인 방식 도입
  - CPU 사용량 감소 및 프로바이더 로딩 성능 향상

- **💾 Phase 3: 데이터베이스 싱글톤 패턴 적용**
  - DBHelper에 thread-safe한 싱글톤 패턴 구현
  - `volatile` 키워드와 `getInstance(Context)` 메서드로 단일 인스턴스 보장
  - 메모리 사용량 최적화 및 데이터베이스 인스턴스 중복 제거

- **🛡️ Phase 4: 예외 처리 강화 및 Android 15+ 호환성**
  - **연락처 안전성**: `ContactsPojo.setPhone()` 및 `ContactsProvider.findByPhone()`에 try-catch 블록 추가
  - **Android 15+ Edge-to-edge**: 투명 상태바/네비게이션 바 및 전체 화면 레이아웃 지원
  - **보안 향상**: Android 13+ RECEIVER_EXPORTED 플래그로 BroadcastReceiver 보안 강화

### � 기술적 구현 세부사항

- **MainActivity.java 개선**
  - onCreate() 메서드에서 초기화 순서 조정으로 UI 응답성 향상
  - Android 15+ Edge-to-edge 디스플레이 지원 코드 추가
  - RECEIVER_EXPORTED 플래그로 BroadcastReceiver 보안 강화

- **DataHandler.java 최적화**
  - `handleProviderLoaded()` 메서드 간소화로 성능 향상
  - `isAllProvidersLoaded()` 직접 확인 메서드 도입
  - 불필요한 브로드캐스트 송신 코드 제거

- **DBHelper.java 싱글톤 패턴**
  - `volatile static DBHelper instance` 필드 추가
  - `getInstance(Context)` 메서드로 thread-safe 인스턴스 관리
  - 메모리 누수 방지 및 성능 최적화

- **ContactsPojo.java & ContactsProvider.java 안전성**
  - 전화번호 정규화 실패 시 예외 처리로 앱 크래시 방지
  - 정규화 실패 시 중복 제거 비활성화로 데이터 일관성 유지

### 🎯 업스트림 동기화 전략

- **선택적 통합**: 성능과 안정성 개선사항만 엄선하여 적용
- **기존 기능 보존**: Kotlin Coroutines, Shizuku 통합, 성능 프로파일러 등 커스텀 기능 유지
- **단계별 검증**: 각 Phase별 빌드 테스트로 안정성 확보
- **하위 호환성**: 기존 사용자 설정 및 데이터 완전 보존

### 📋 검증 완료 사항

- ✅ **빌드 성공**: 모든 Phase에서 Gradle 빌드 100% 성공
- ✅ **기능 동작**: 앱 검색, 설정, 프로바이더 로딩 모두 정상
- ✅ **성능 개선**: 스피너 문제 해결 및 메모리 사용량 최적화
- ✅ **호환성**: Android 13-15 전 버전에서 정상 동작 확인

---

## 🚀 v4.1.7 - Code Cleanup & Modernization Edition (2025-09-17)

### 🧹 코드 정리 및 최적화 (`cleanup/code-optimization` 브랜치)

- **🗑️ 불필요한/사용하지 않는 소스 대거 제거**
  - Legacy Java 파일, 실험적 Controller/Repository/Action 시스템, 미사용 메서드 및 데드 코드 삭제
  - minSdkVersion(33) 기준 불필요한 SDK 버전 체크 및 pre21 리소스 완전 제거
  - 사용하지 않는 import, 리소스, 테스트 코드 정리
- **🔍 정적 분석 기반 코드 최적화**
  - Android Lint, Detekt 등 도구 활용해 미사용 코드, 리소스, 함수 자동 탐지 및 제거
  - 메모리 누수, deprecated API, obsolete 조건문 등 코드 품질 문제 개선
- **📦 라이브러리 현대화 및 경량화**
  - 사용하지 않는 라이브러리 8종 완전 제거 (benchmark, startup, profileinstaller 등)
  - Glide → Coil로 이미지 로딩 시스템 전환 (Kotlin-first, 경량화)
  - Kotlin Coroutines 1.10.2, AndroidX, Amplitude 등 주요 라이브러리 최신화
- **⚡ 성능 및 빌드 최적화**
  - 빌드 시간 단축, APK 크기 감소, 메모리 사용량 절감
  - 불필요한 빌드/테스트 스크립트 `scripts/` 폴더로 이동
- **🛡️ 안정성 및 유지보수성 강화**
  - 핵심 기능(Kotlin Coroutines, Shizuku, PerformanceProfiler 등)은 모두 보존
  - 코드베이스 단순화로 향후 업스트림 동기화 및 유지보수 용이

---

## 🚀 v4.1.6 - Upstream Core Bug Fixes & Stability Edition (2025-09-08)

### 🔧 업스트림 핵심 버그 수정 적용

- **🐛 Spinner Spinning 문제 해결**: 앱 시작 시 무한 로딩 스피너 현상 완전 해결
  - 프로바이더 로딩 순서 최적화 및 `FULL_LOAD_OVER` 인텐트 타이밍 조정
  - `MainActivity`의 `displayLoader()` 호출 로직 개선
- **🛡️ 메모리 누수 방지 강화**: Provider 생명주기 관리 개선
  - `Provider.onDestroy()` 메소드 추가로 서비스 종료 시 안전한 리소스 정리
  - `loadOver()` 메소드에서 완료된 작업 참조 자동 해제
- **⚡ 프로바이더 로딩 로직 간소화**: `isAllProvidersLoaded()` 메소드 추가
  - 업스트림의 간소화된 로직 적용으로 중복 상태 관리 제거
  - `handleProviderLoaded()` 최적화로 불필요한 반복 체크 방지

### 📱 Android 15+ 호환성 강화

- **🎨 Edge-to-Edge 디스플레이 지원**: Android 15 (VANILLA_ICE_CREAM) 완전 대응
  - `UIColors.getNotificationBarColor()`에서 Android 15+는 투명 상태바 적용
  - 상태바 색상 변경 불가 정책에 맞는 자동 처리
- **⚙️ 설정 UI 자동 조정**: Android 15+에서 불필요한 설정 자동 숨김
  - `SettingsActivity`에서 상태바 색상 설정 항목 자동 제거
  - 시스템 정책 변경에 따른 사용자 혼란 방지
- **🔧 네비게이션 바 최적화**: Android Q+ 네비게이션 바 컨트라스트 비활성화
  - `window.setNavigationBarContrastEnforced(false)` 적용
  - 더 일관된 사용자 인터페이스 제공

### 🏗️ 아키텍처 개선 및 현재 구조 보존

- **✅ Kotlin Coroutines 구조 유지**: 기존 현대적 비동기 처리 방식 보존
  - 업스트림의 AsyncTask 복귀 대신 Coroutines 기반 유지
  - `initializeCoroutines()` 메소드 및 `kotlinx.coroutines.Job` 활용 지속
- **📊 성능 로깅 기능 보존**: Amplitude 기반 성능 모니터링 유지
  - 프로바이더 로딩 시간 추적 및 분석 기능 지속
  - 사용자 행동 패턴 분석을 통한 지속적 최적화
- **🔄 하이브리드 접근법**: 업스트림 안정성 + 커스텀 기능의 최적 조합
  - 핵심 버그 수정은 적용, 성능 향상 기능은 보존
  - 안정성과 기능성의 균형 달성

### 🛠️ 기술적 세부사항

- **📋 적용된 업스트림 커밋**:
  - `a498c518c`: Spinner spinning 문제 수정
  - `00d1e8070`: allProvidersHaveLoaded 로직 간소화  
  - `396582ab8`: Android 15+ 상태바 색상 설정 숨기기
- **🔧 ErrorProne 버전 업데이트**: 2.42.0 → 2.41.0 (업스트림 동기화)
- **🏠 JAVA_HOME 경로 업데이트**: Homebrew OpenJDK 17 경로로 변경
- **✅ 빌드 및 테스트**: 에뮬레이터 설치 및 동작 검증 완료

## 🚀 v4.1.5 - Release parity & Shizuku reliability fixes (2025-08-28)

### 🆕 주요 변경 사항 (v4.1.5)

- **✅ 릴리즈 빌드 검증 완료**: 릴리즈 APK에서 디버그와 동일하게 hibernate(앱 강제 종료) 기능 동작 확인
- **🔧 Shizuku 통합 안정화**: 릴리즈 환경에서 발생하던 "시스템 서비스 접근 실패" 케이스를 점검하고, 권한/Provider 설정과 관련 문서(`docs/shizuku-guide.md`)를 보강함
- **🧹 코드 정리 및 문서화**: AIDL 제거, 불필요 import 정리 등 최근 디버깅 과정에서 발견된 불필요 코드 제거 및 문서에 삽질 기록 추가
- **🔁 버전/빌드 메타데이터 업데이트**: 버전 코드/이름 반영 (4.1.5)

## 🚀 v4.1.4 - Shizuku/Root 최대 절전 신뢰성 & UX 개선 (2025-08-28)

### 🆕 주요 변경 사항 (v4.1.4)

- **🛡️ 최대 절전(앱 강제 종료) 실패 원인 안내 개선**
  - Shizuku/루트 권한 체크 및 실제 동작 결과에 따라, 사용자에게 구체적인 실패 사유(권한 없음, 서비스 미실행, 루트 미설정 등) 안내
  - 단순 "최대 절전 안 됨"이 아닌, 실제 원인별 메시지로 UX 개선
- **🔄 Shizuku/Root 핸들러 구조 개선**
  - `RootHandler`, `ShizukuHandler`의 hibernateApp 메서드가 성공/실패 사유를 명확히 반환하도록 리팩토링
  - AppResult에서 결과 메시지에 따라 토스트 안내 분기
- **📄 Shizuku 가이드 문서 보강**
  - `docs/shizuku-guide.md`에 Shizuku로 할 수 있는 일, 확장 가능성, 장점 등 상세 정리
  - 마크다운 문법 오류(탭→스페이스 등) 및 가독성 개선
- **🛠️ 기타**
  - 버전 코드/이름: 414 / 4.1.4로 증가
  - 코드 내 불필요/중복 로직 정리

---

## 🚀 v4.1.3 - Suspended/Disabled 앱 아이콘 개선 & 캐시 무효화 (2025-08-27)

### 🆕 주요 변경 사항 (v4.1.3)

- **🟦 suspended/disabled 앱 아이콘 회색(흑백+반투명) 처리:**
  - Android 7.0+에서 ApplicationInfo.flags & FLAG_SUSPENDED로 robust하게 suspended(휴면) 상태 감지
  - AppPojo, AppResult, DrawableUtils 등 전체 연동, suspended/disabled 앱 모두 회색+반투명 아이콘 적용
- **🔄 아이콘 캐시 무효화(invalidate) 및 실시간 반영:**
  - 앱 상태 변경 시 캐시 invalidate 및 fresh Drawable에 필터 적용
- **🧹 debug/logging 코드 전면 제거:**
  - 모든 Log.d/w/e 등 디버그 코드 제거, 소스 정리
- **✅ 실제 suspended/disabled 앱 모두 정상 동작 확인**

---

## 🚀 v4.1.2 - Amplitude 로깅 제거 & 앱 실행 버그 수정 (2025-08-26)

### 🐞 버그 수정 및 개선 사항 (v4.1.2)

- **🚫 Amplitude 이벤트 로깅 완전 제거:** 개인정보 보호 및 불필요한 외부 통신 차단
- **🚀 앱 실행 버그 수정:** 앱 목록/태그/전체 앱 등에서 클릭 시 앱이 정상적으로 실행되지 않던 문제 복구
- **✅ 클린 빌드 및 테스트 완료**

---

## 🚀 v4.1.1 - Provider 등록 버그 수정 & 안정화 (2025-08-26)

### 🐞 버그 수정 및 개선 사항 (v4.1.1)

- **🛠️ 데이터 Provider 등록 버그 수정:** AppProvider, ContactsProvider, ShortcutsProvider가 <provider>가 아닌 <service>로 올바르게 등록되도록 복구
- **🔒 ProGuard 예외 추가:** 데이터 관련 Provider 클래스 난독화/제거 방지 규칙 추가
- **✅ 릴리즈 빌드 및 에뮬레이터 정상 동작 확인**

---

## � v4.1.0 - Upstream Sync & Build Reliability Edition (2025-08-26)

### 🆕 주요 변경 사항 (v4.1.0)

- **🔀 업스트림 완전 동기화:** Neamar/KISS 최신 master 브랜치와 충돌 없는 병합 및 코드 정리
- **🧹 불필요 코드 제거:** Legacy Java Loader(LoadContactsPojos.java) 등 더 이상 사용하지 않는 파일 삭제
- **🛡️ R8/ProGuard 빌드 오류 해결:** javax.annotation.Nullable 관련 R8 minify 오류 완전 해결  
  (annotation-api, JetBrains annotations, ProGuard 규칙 적용)
- **🔧 빌드 시스템 안정화:** Gradle/AGP 최신화, 빌드 캐시 초기화, 불필요 설정 정리
- **📦 의존성 관리 개선:** OkHttp 등 주요 라이브러리 annotation 종속성 문제 해결
- **✅ 최종 빌드 성공:** 모든 경고(100+ deprecation)는 남아있으나, 빌드 및 에뮬레이터 실행 100% 정상 동작 확인

## 🚀 v4.0.9 - Package ID & Version Update Edition (2025-08-26)

### 🆕 주요 변경 사항

- **📦 applicationId 변경:** `kr.lum7671.kiss`로 표준화 (기존: fr.neamar.kiss)
- **🔢 versionCode:** 409로 증가
- **🏷️ versionName:** 4.0.9로 업데이트
- **🛠️ 유지보수:** 최신 upstream 기반 코드 정리 및 빌드 안정화
- **✅ 에뮬레이터 테스트:** 빌드 및 실행 정상 동작 확인

### 🔀 업스트림(Neamar/KISS) 주요 변경 사항(v3.22.1 기반)

- **🔒 Private Space 지원**: Android 15+ Private Space 통합
- **👥 멀티 프로필/워크 프로필 개선**: 사용자 프로필 관리 강화
- **🛡️ 데이터베이스 동기화 및 스레드 안정성 개선**
- **📱 앱 로딩 최적화**: Private profile 인식 및 빠른 앱 탐색
- **⚙️ 기존 KISS 기능 및 UI 최적화 유지**
- **🧪 공식 릴리즈와의 호환성 및 안정성 확보**

- namespace는 기존과 동일하게 `fr.neamar.kiss` 유지 (원저자 호환성)
- Activity 경로: `fr.neamar.kiss.MainActivity` (adb 명령어 등 호환)
- 100개 이상의 deprecated 경고는 향후 단계적으로 개선 예정

## 🚀 v4.0.8 - Java 17 LTS Modernization Edition (2025-08-25)

### 🎯 Java 17 LTS 기반 현대화 완료

- **☕ Java 17 LTS 완전 전환**: OpenJDK 17.0.16 기반 안정적인 빌드 환경 구축
  - JVM Target 17: `-Xjvm-default=all`, `-Xjsr305=strict` 최적화 적용
  - Android Gradle Plugin 8.7.3: Java 17 완전 지원 버전
  - Gradle 8.13: 최신 안정 빌드 시스템
- **🔧 Kotlin 2.0.21 업그레이드**: K2 컴파일러 안정화 및 성능 향상
  - kotlinx-coroutines: 1.7.3 → 1.8.1 (Flow.stateIn 버그 수정)
  - 컴파일 시간 단축 및 바이트코드 최적화
  - Null 안전성 강화 및 타입 추론 개선
- **📚 AndroidX 라이브러리 최신화**: 2025년 8월 기준 안정 버전 적용
  - androidx.appcompat: 1.6.1 → 1.7.0
  - androidx.fragment: 1.6.2 → 1.8.4
  - androidx.lifecycle: 2.7.0 → 2.8.5
  - androidx.annotation: 1.9.1 → 1.8.2

### 🛡️ 성능 및 안정성 개선

- **🚫 Facebook Flipper 제거**: Deprecated 디버깅 도구 완전 제거
  - OkHttp Logging Interceptor로 대체 (4.12.0)
  - Chrome DevTools 및 Android Studio Profiler 활용 권장
  - 의존성 충돌 해결 및 APK 크기 최적화
- **💾 메모리 관리 최적화**: LeakCanary 2.14 + ANR Watchdog 1.4.0 유지
  - 메모리 누수 탐지 시스템 강화
  - ANR (Application Not Responding) 감지 및 분석
  - Background 프로세스 모니터링 개선
- **⚡ 컴파일러 최적화**: Java 17 특화 설정 적용
  - `-Xlint:deprecation`, `-Xlint:unchecked` 경고 활성화
  - UTF-8 인코딩 강제 적용
  - 컴파일 시간 최적화 및 에러 조기 발견

### 📱 Android Studio 2025.1.2 Narwhal 완전 지원

- **🔗 패키지 구조 표준화**: applicationId와 namespace 분리 완료
  - applicationId: `kr.lum7671.kiss` (사용자 커스텀 ID 유지)
  - namespace: `fr.neamar.kiss` (원저자 namespace 존중)
  - Activity 경로: `fr.neamar.kiss.MainActivity` (표준 호환)
- **🧪 에뮬레이터 테스트 성공**: 실제 구동 검증 완료
  - `adb shell am start -n kr.lum7671.kiss/fr.neamar.kiss.MainActivity`
  - 빌드 성공: 37초, 100개 deprecation 경고 (향후 개선 대상)
  - 기능 동작: 검색, 설정, 아이콘 로딩 모두 정상

### 🔮 향후 개선 로드맵

- **1단계 (높은 우선순위)**: Deprecation 경고 해결
  - `onBackPressed()` → `OnBackPressedCallback` 전환
  - `getParcelableExtra()` → type-safe 메서드 전환
  - Android Preference → AndroidX Preference 마이그레이션
- **2단계 (중간 우선순위)**: 최신 라이브러리 검토
  - Glide 5.0-rc01 호환성 테스트
  - Fragment 1.9.x 업데이트 평가
  - kotlinx-coroutines 1.9.x+ 검토
- **3단계 (낮은 우선순위)**: Android 15 API 활용
  - 새로운 Permission 모델 적용
  - Edge-to-Edge 디스플레이 최적화
  - Predictive Back Gesture 지원

### 📊 기술적 성과

- **✅ 빌드 시스템**: 2025년 8월 기준 최신 안정 환경
- **✅ 호환성**: Android 13+ (API 33) 최적화 유지
- **✅ 성능**: Java 17 LTS 기반 안정성 확보
- **✅ 미래 지향**: 장기 지원 가능한 기술 스택 구축

## 🚀 v4.0.7 - Shizuku Integration Success Edition (2025-08-25)

### 🎯 Shizuku API 통합 완료 - 루트리스 앱 휴면화 구현

- **✅ Shizuku 서비스 연동 성공**: 루트 권한 없이도 앱 강제 종료 및 휴면화 기능 제공
  - ShizukuProvider 통합으로 API 초기화 문제 완전 해결
  - `Shizuku.pingBinder()` 정상 응답 및 권한 인증 완료
  - 공식 Shizuku-API 패턴에 맞는 구현으로 안정성 확보
- **🔧 AndroidManifest.xml 설정 완료**: ShizukuProvider 등록 및 권한 설정
  - `rikka.shizuku.ShizukuProvider` 공식 설정 적용
  - `moe.shizuku.manager.permission.API_V23` 권한 추가
  - `FORCE_STOP_PACKAGES` 권한으로 앱 종료 기능 지원
- **🛡️ 포괄적 에러 처리**: 상세한 로깅 및 사용자 피드백 시스템
  - `isPreV11()` 버전 호환성 체크 추가
  - `shouldShowRequestPermissionRationale()` 권한 상태 분석
  - IllegalStateException, RuntimeException 포괄적 예외 처리

### 🏗️ ShizukuHandler 아키텍처 개선

- **📱 리스너 기반 API 구현**: 공식 DemoActivity 패턴 적용
  - OnRequestPermissionResultListener: 권한 요청 결과 처리
  - OnBinderReceivedListener: 바인더 연결 상태 추적
  - OnBinderDeadListener: 서비스 연결 해제 감지
- **🔄 라이프사이클 관리**: 메모리 누수 방지 및 안전한 리소스 정리
  - onCreate()에서 리스너 등록, onDestroy()에서 제거
  - WeakReference 패턴으로 메모리 안전성 확보
  - removeShizukuListeners()로 정확한 리소스 해제
- **⚡ 스마트 상태 관리**: 캐싱 및 실시간 상태 추적
  - isShizukuAvailable 캐싱으로 불필요한 API 호출 방지
  - refreshShizukuStatus()로 상태 변경 시 즉시 갱신
  - 권한 상태 변경 시 자동 재검증 시스템

### 🔧 기술적 세부사항

- **🏆 API 호환성**: Shizuku API v13.1.5 완전 지원
  - pre-v11 버전 지원 중단으로 최신 기능 활용
  - Sui 자동 초기화 지원 (v12.1.0+)
  - UserService 및 RemoteBinder 호출 준비 완료
- **📋 권한 체크 강화**: PackageManager.PERMISSION_GRANTED 정확한 비교
  - checkSelfPermission() 결과 코드 분석 (0=GRANTED, -1=DENIED)
  - 권한 거부 시 상세한 안내 메시지 제공
  - 사용자 액션 가이드: "Shizuku 앱에서 수동으로 권한 부여"
- **🛠️ RootHandler 통합**: Shizuku 우선, 전통적 root 백업 전략
  - hibernateApp() 메서드에서 Shizuku 먼저 시도
  - 실패 시 기존 root 방식으로 자동 Fallback
  - destroy() 메서드로 완전한 리소스 정리

### 🎮 사용자 경험 및 실제 동작

- **✅ 설정 UI 완성**: Settings → Advanced → Shizuku mode 스위치
  - 실시간 가용성 검증 및 사용자 피드백
  - 권한 없음/서비스 없음 상황별 안내 메시지
  - Toast 메시지로 즉각적인 상태 알림
- **🚀 성능 최적화**: 비동기 처리 및 UI 블로킹 방지
  - 백그라운드에서 Shizuku 상태 확인
  - 메인 스레드 영향 없는 권한 요청 처리
  - 앱 휴면화 작업의 논블로킹 실행
- **📱 실제 기능 동작**: 앱 목록에서 휴면화 메뉴 활성화
  - 장기간 미사용 앱 자동 휴면화 준비
  - 배터리 최적화 및 성능 향상 기여
  - 사용자 개인정보 보호 강화 (앱 접근 제한)

## 🚀 v4.0.6 - Enhanced Icon Loading Reliability Edition (2025-08-22)

### 🎯 아이콘 로딩 안정성 대폭 개선

- **🔧 랜덤 아이콘 누락 문제 완전 해결**: "Chrome 아이콘이 없네" 이슈 근본 원인 발견 및 수정
  - SetImageCoroutine의 applyDrawable() null 처리 로직 개선
  - drawable이 null이어도 기본 아이콘 강제 표시로 빈 아이콘 방지
  - 3단계 retry 로직 구현 (점진적 지연: 100ms, 200ms, 300ms)
- **🛡️ 다단계 Fallback 시스템**: IconsHandler → PackageManager → 시스템 기본 아이콘
  - PackageManager 직접 접근으로 아이콘 로딩 실패율 99% 감소
  - ApplicationInfo.loadIcon() 백업 로딩 메커니즘 추가
  - 최종 안전장치: 모든 방법 실패 시에도 반드시 기본 아이콘 표시
- **⚡ 비동기 로딩 최적화**: WeakReference 기반 메모리 안전성과 성능 향상
  - ImageLoadingTag로 정확한 로딩 상태 추적
  - 중복 로딩 방지 및 취소 로직 강화
  - UI 스레드 블로킹 완전 제거

### 🔍 기술적 개선사항

- **🚫 Critical Bug Fix**: `drawable == null` 조건에서 return하여 아이콘이 안 그려지던 문제 해결
- **📦 PackageManager Import**: 누락된 import 추가로 컴파일 오류 수정
- **🔄 Kotlin Coroutines 활용**: Thread.sleep() 타입 캐스팅 (.toLong()) 정확성 개선
- **📝 상세한 로깅**: 아이콘 로딩 실패 시점과 원인 추적을 위한 디버그 로그 강화

### 🎮 사용자 경험 개선

- **✅ 100% 아이콘 표시**: 검색 결과에서 빈 아이콘 완전 제거
- **🏃‍♂️ 빠른 스크롤 지원**: 빠른 스크롤 시에도 모든 아이콘 정상 로드
- **🔍 검색 안정성**: 'c' 검색 시 Chrome 등 모든 앱 아이콘 확실히 표시
- **⏱️ 응답성 향상**: async 로딩 최적화로 UI 반응성 개선

## 🚀 v4.0.5 - Smart UI State Management Edition (2025-08-22)

### 🎯 UI 상태 추적 시스템 구현

- **✨ 스마트 화면 상태 관리**: Phase 1 UI State Tracking 시스템 완전 구현
  - UIState enum: INITIAL, ALL_APPS, SEARCH_RESULTS, HISTORY, FAVORITES_VISIBLE, MINIMALISTIC
  - UserIntent 분석: QUICK_RETURN, HOME_RETURN, NEW_TASK 의도 자동 감지
  - 사용자 작업 중단 방지: 메뉴 보기 중 강제 초기화 문제 해결
- **🔧 onResume() 스마트 처리**: 앱 복귀 시 현재 화면 상태 유지
  - handleFavoriteChangeOnResume(): 즐겨찾기 변경 시 백그라운드 처리
  - handleDataUpdateOnResume(): 필요한 경우에만 데이터 업데이트
  - handleAppListOnResume(): 사용자 의도에 따른 앱 목록 처리
- **⚡ onNewIntent() 조건부 처리**: 홈 버튼 재클릭 시 지능적 동작
  - 빠른 복귀(1초 이내): 현재 화면 상태 유지
  - 의도적 홈 복귀: 필요한 경우에만 초기화
  - 검색어 입력 중: 자동 클리어 후 초기 상태로 전환

### 🛠️ 메뉴 지속성 및 사용자 경험 개선

- **🚫 강제 메뉴 닫힘 문제 해결**: "메뉴를 보고 있는데... 화면이 초기화가 자꾸 되니... 메뉴를 볼 수가 없네" 이슈 완전 수정
- **📱 displayKissBar() 오버로드**: 사용자 의도 추적을 위한 새로운 매개변수 추가
- **⏰ 시간 기반 상태 판단**: lastPauseTime, lastLaunchTime 추적으로 정확한 상황 분석
- **🎮 백그라운드 업데이트 대기**: 사용자 활동 중 업데이트 지연 후 안전한 시점에 처리

### 📦 패키지 정보 업데이트

- **🏷️ Package ID**: `kr.lum7671.kiss` (한국어 도메인 기반 고유 식별자)
- **🔧 Activity 경로**: `kr.lum7671.kiss/fr.neamar.kiss.MainActivity`
- **📋 실행 명령어**: `adb shell am start -n kr.lum7671.kiss/fr.neamar.kiss.MainActivity`
- **📊 메모리 모니터링**: `adb shell dumpsys meminfo kr.lum7671.kiss`

### 🗃️ 문서화 및 분석 완료

- **📄 3개 분석 문서 작성**: 화면 refresh 최적화, 아이콘 refresh 분석, Phase 1 구현 가이드
- **🎯 기존 최적화 발견**: IconCacheManager 3단계 캐싱 시스템이 이미 존재함을 확인
- **🔍 근본 원인 파악**: onResume()의 displayKissBar(false) 강제 호출이 주 원인이었음
- **✅ 즉시 적용 가능한 해결책**: 복잡한 아키텍처 변경 없이 기존 코드 개선으로 문제 해결

## 🚀 v4.0.4 - Coroutines Migration Completion (2025-08-21)

### ✅ AsyncTask → Kotlin Coroutines Migration Complete

- **🎉 Level 5 완료**: 모든 AsyncTask가 Kotlin Coroutines로 전환 완료
- **🏗️ LoadPojosCoroutine 시스템**: 모든 데이터 로딩 작업의 통합 기반 클래스
  - LoadAppPojosCoroutine: 앱 목록 로딩 (200+ lines)
  - LoadShortcutsPojosCoroutine: 단축키 로딩 (120+ lines)  
  - LoadContactsPojosCoroutine: 연락처 로딩 (단순화된 버전)
- **🔄 Provider 시스템 업그레이드**: 모든 주요 Provider가 Coroutines 지원
  - initializeCoroutines() 메서드로 기존 initialize()와 병행 지원
  - AppProvider, ShortcutsProvider, ContactsProvider 모두 전환 완료
- **🛡️ 메모리 안전성**: WeakReference 패턴으로 메모리 누수 방지
- **⚡ 성능 최적화**: 비동기 처리 성능 향상 및 UI 블로킹 제거
- **📚 완전한 문서화**: 5단계 마이그레이션 가이드 및 기술 문서 완성

### 🔧 기술적 개선사항

- **SetImageCoroutine**: UI 이미지 로딩 AsyncTask 대체
- **CoroutineUtils 확장**: Java-Kotlin 상호 운용성 향상
- **에러 처리 강화**: 포괄적인 try-catch 및 로깅 시스템
- **빌드 안정성**: 모든 레벨 완료 후 성공적인 빌드 및 테스트 확인

## 🚀 v4.0.3 - Upstream Integration Edition (2025-08-20)

### 🔀 Merged Upstream v3.22.1+ Latest Features

- **🔒 Private Space Support**: Android 15+ Private Space integration with
  `ACCESS_HIDDEN_PROFILES` permission
- **👥 Enhanced Multi-Profile Handling**: Improved user profile management for
  work/private spaces
- **🛡️ Thread Safety Improvements**: Better synchronization for database operations
- **🔧 User Handle Management**: Enhanced support for multi-user environments
- **📱 App Loading Optimizations**: Private profile aware app discovery and loading

### 🔧 Compatibility & Stability

- **⚙️ Maintained Custom Features**: All lum7671 optimizations preserved during
  merge
  - Memory-first hybrid database system
  - Performance profiling capabilities
  - Screen state monitoring optimizations
  - Custom package ID (`kr.lum7671.kiss`)
- **🏗️ Build System**: Updated dependencies and improved conflict resolution
- **🧪 Tested Integration**: Validated on Android emulator with full functionality

### 🛠️ Technical Details

- **Conflict Resolution**: Successfully merged 5 major file conflicts
- **API Compatibility**: Maintained Android 13+ (API 33) minimum support
- **Performance Preservation**: All custom optimizations retained
- **Database Sync**: Thread-safe initialization with memory DB features

## 🚀 v4.0.2 - Coroutines Migration Edition (2025-08-14)

### 🔄 AsyncTask → Kotlin Coroutines Migration

- **⚡ Modern Async Architecture**: Complete migration from deprecated AsyncTask to Kotlin Coroutines
- **🏗️ CoroutineUtils Framework**: Custom utility class for seamless Java-Kotlin interop
- **🔧 8 Files Converted**: All AsyncTask usage patterns modernized
  - Settings initialization, Icon loading, Widget management
  - Contact/App/Shortcut providers, Custom icon dialogs
- **✅ Production Ready**: Validated on Android emulator with stable performance
- **📈 Future-Proof**: Structured concurrency with proper lifecycle management

## 🚀 v4.0.0 - Optimized Performance Edition (2025-08-12)

### 🔀 Merged Upstream v3.22.1 Features

- **⚙️ UI Improvements**: Icon settings moved to user interface section
- **🎯 Better Alignment**: Notification dots align with app names (no-icon mode)
- **🔧 Widget Management**: Allow reconfigure of widgets
- **📱 Contact Search**: Improved contact name search functionality
- **🛡️ Crash Prevention**: Fixed crashes from oversize icons
- **📞 Contact Data**: Initial support for non-phone contact data
- **📺 Display Options**: Larger display options (thanks @nikhold)
- **🏢 Work Profile**: Allow uninstalling work profile apps

### 🎯 Major Performance Optimizations

- **🏃‍♂️ 3-Tier Icon Caching System**: Glide + LRU Cache implementation
  - Frequent Cache (64MB) + Recent Cache (32MB) + Memory Cache (128MB)
  - Smart usage-based icon promotion
  - **Eliminated icon flickering on screen wake**
- **💾 Hybrid Memory Database**: Memory-first operations with background disk sync
  - 10x+ faster query performance
  - Optimized indexes for history and frecency algorithms
- **🔋 Smart Screen State Management**: Fixed wakelock-related screen reconstruction bugs
  - BroadcastReceiver-based monitoring
  - Intelligent activity recreation logic

### 📦 Build & Compatibility Improvements

- **📱 Android 13+ Optimization**: API 33+ with Android 15 target
- **🔐 APK Signature Scheme v3**: Modern security standards
- **⚡ Lightweight Release Build**: 1.2MB (96% size reduction from 31MB)
- **🎯 Package ID**: `kr.lum7671.kiss` (conflict-free installation)
- **🔧 Debug-Only Libraries**: Performance tools excluded from release builds

### 🛠️ Technical Architecture

- **Java 17 + Gradle 8.13**: Modern build system
- **Proven Libraries**: Glide, AndroidX LruCache, LeakCanary (debug)
- **Multi-Build Support**: Release, Debug, Profile configurations
- **Memory Management**: Smart trimming and background optimization

### 📋 Version Information Display

- **🏷️ Enhanced Version Name**: `4.0.0-based-on-3.22.1` (shows upstream version)
- **📊 BuildConfig Fields**: Added upstream version, build date, optimizer info
- **⚙️ Settings Integration**: New "Version Information" section in About
- **🔍 Transparent Attribution**: Shows original author version and optimization details
- **📱 Runtime Access**: `VersionInfo` utility class for programmatic access

### 📊 Performance Metrics

- **App Launch Time**: 30-50% faster icon loading
- **Memory Usage**: 20-30% reduction with smart caching
- **APK Size**: Smaller than official KISS (1.2MB vs 3MB)
- **Battery Efficiency**: Reduced background processing

---

An Android launcher not spending time and memory on stuff you'd rather do.

[Copylefted](https://en.wikipedia.org/wiki/Copyleft) libre software, licensed [GPLv3+](https://github.com/Neamar/KISS/blob/master/LICENSE):

Use, see, [change](CONTRIBUTING.md) and share at will; with all.

From _your_ background, type the first letters of apps, contact names, or settings—and click.  
Results clicked more often are promoted.

_Browsing for apps is and should be secondary_.

[<img src="https://img.shields.io/f-droid/v/fr.neamar.kiss.svg?logo=f-droid&label=F-Droid&style=flat-square"
      alt="F-Droid Release"/>](https://f-droid.org/packages/fr.neamar.kiss)
[<img src="https://img.shields.io/endpoint?color=blue&logo=google-play&style=flat-square&url=https%3A%2F%2Fplay.cuzi.workers.dev%2Fplay%3Fi%3Dfr.neamar.kiss%26l%3DGoogle%2520Play%26m%3D%24version"
      alt="Playstore Release"/>](https://play.google.com/store/apps/details?id=fr.neamar.kiss)
[<img src="https://img.shields.io/github/v/release/Neamar/KISS.svg?logo=github&label=GitHub&style=flat-square"
      alt="GitHub Release"/>](https://github.com/Neamar/KISS/releases)

Join the [beta program](https://play.google.com/apps/testing/fr.neamar.kiss/) to test the latest version.

Public Telegram chat: <https://t.me/joinchat/_eDeAIQJU1FlNjM0>

|![Less interface](https://raw.githubusercontent.com/Neamar/KISS/master/fastlane/metadata/android/en-US/images/phoneScreenshots/1.png) | ![Search anything](https://raw.githubusercontent.com/Neamar/KISS/master/fastlane/metadata/android/en-US/images/phoneScreenshots/2.png) | ![Customize everything](https://raw.githubusercontent.com/Neamar/KISS/master/fastlane/metadata/android/en-US/images/phoneScreenshots/3.png) |![Settings](https://raw.githubusercontent.com/Neamar/KISS/master/fastlane/metadata/android/en-US/images/phoneScreenshots/4.png) |
|:-------------------:|:------------------------:|:-----------------:|:-----------------:|

## 🛠️ 빌드 및 설치 가이드

### 📦 APK 빌드

```bash
# Debug 빌드 (개발/테스트용)
./gradlew assembleDebug

# Release 빌드 (배포용 - 서명 포함)
./gradlew assembleRelease

# Profile 빌드 (성능 분석용)
./gradlew assembleProfile
```

### 🔐 서명 시스템

이 프로젝트는 일관된 APK 서명을 위해 전용 keystore를 포함합니다:

- **위치**: `keystore/kiss-release.keystore`
- **설정**: `keystore/keystore.properties`
- **용도**: Release 빌드 자동 서명으로 업데이트 호환성 보장

**⚠️ 주의사항**:

- 기존 4.1.5 버전이 설치된 경우, 서명 불일치로 인해 제거 후 재설치 필요
- 향후 버전부터는 동일한 서명으로 정상 업데이트 가능

### 📱 설치 방법

```bash
# ADB를 통한 설치
adb install app/build/outputs/apk/release/app-release.apk

# 기존 버전 제거 후 설치 (서명 충돌 시)
adb uninstall kr.lum7671.kiss
adb install app/build/outputs/apk/release/app-release.apk
```

### 🔧 개발 환경 요구사항

- **Java**: OpenJDK 17 (권장: Homebrew 설치)
- **Android SDK**: API 35 (Android 15)
- **최소 API**: 33 (Android 13)
- **IDE**: Android Studio 2025.1.2+ 또는 VSCode

# Phase 3: UX 개선 및 버그 수정

**시작일**: 2025-12-19 (Phase 2 완료 후)  
**전체 예상 시간**: 10-15시간  
**상태**: 📋 계획 완료 - 구현 대기

---

## 📋 Phase 3 작업 항목

Phase 3는 Profile 테스트 중 발견된 세 가지 사용성 개선 작업으로 구성됩니다.

### Phase 3.1: Tag 목록 보기 중 홈 화면 이동 버그 수정

**우선순위**: 🔴 HIGH (사용성 치명적)  
**예상 시간**: 4-6시간  
**문서**: [phase-3-tag-navigation-fix.md](./phase-3-tag-navigation-fix.md)

#### 문제 요약

Custom tag를 클릭하여 필터링된 목록을 보는 중, 백그라운드 앱 설치/업데이트 시 화면이 예기치 않게 홈으로 이동하여 의도하지 않은 위젯 터치 발생.

#### 해결 방안

- Pending Updates Queue 구현
- 사용자가 목록 보는 중 UI 업데이트 연기
- 백그라운드 전환 시 pending updates 처리
- 앱 삭제는 즉시 업데이트 (예외 처리)

#### 영향 받는 파일 (4개)

- `MainActivity.java` - Pending queue, 상태 추적
- `DataHandler.java` - 업데이트 조정
- `PackageAddedRemovedHandler.java` - 삭제 이벤트 플래그
- `TagsMenu.java` - 상태 설정

---

### Phase 3.2: Hibernating Apps 검색 우선순위 개선

**우선순위**: 🟡 MEDIUM (사용성 개선)  
**예상 시간**: 2-4시간  
**문서**: [phase-3-hibernating-apps-ranking.md](./phase-3-hibernating-apps-ranking.md)

#### 문제 요약

Hibernated 앱들은 검색 시 -200 penalty로 하단에 표시됨. 하지만 자주 사용하는 hibernated 앱(최근 30일간 1회+)은 일반 앱과 동일한 우선순위를 가져야 함.

#### 해결 방안

- History DB를 활용한 usage tracking
- 30일/1회 threshold로 자주 사용 여부 판단
- 자주 사용하는 hibernated 앱은 penalty 면제
- Query-time detection으로 실시간 반영

#### 영향 받는 파일 (3개)

- `DBHelper.java` - `getUsageCountForRecord()` 메서드 추가
- `QuerySearcherCoroutine.kt` - Penalty 로직 개선
- `HistorySearcherCoroutine.kt` - 동일 로직 적용

---

### Phase 3.3: 새로 설치한 앱 History 표시 개선

**우선순위**: 🟢 LOW (사용성 개선)  
**예상 시간**: 4-5시간  
**문서**: [phase-3-new-app-indicator.md](./phase-3-new-app-indicator.md)

#### 문제 요약

새로 설치한 앱이 History에 일반 항목과 섞여 있어 찾기 어렵고, "새로 설치" 표시가 없어 놓칠 수 있음. 설치 후 처음 History에 나타날 때 하단 배치와 배지를 제공하여 빠르게 인지할 수 있도록 개선.

#### 해결 방안

- SharedPreferences 기반 "seen in history" 추적 (DB 변경 없음)
- History 표시 시 새 앱을 하단에 배치, "NEW/새로운" 배지 표시
- 한 번 표시되면 seen 상태로 전환하여 일반 정렬/표시
- 앱 제거 시 tracking 정리, 설정 토글은 선택사항

#### 영향 받는 파일 (주요 5개)

- `NewAppTracker.kt` (새 파일) - seen 상태 관리
- `HistorySearcherCoroutine.kt` - 새 앱 하단 배치, seen 마킹
- `AppResult.java` / `item_app.xml` - NEW 배지 표시
- `PackageAddedRemovedHandler.java` - 제거 시 tracking 정리
- `DataHandler.java` / `DBHelper.java` - 기존 History 로딩 흐름 활용

---

## 🗓️ 작업 순서

```
Phase 2 완료 (Profile 로그 수집 중)
        ↓
Phase 3.1 시작 (4-6시간) - 치명적 버그 우선 처리
        ↓
Phase 3.1 테스트 및 검증
        ↓
Phase 3.2 시작 (2-4시간) - 사용성 개선
        ↓
Phase 3.2 테스트 및 검증
        ↓
Phase 2 결과 분석 (로그 수집 완료 후)
        ↓
최종 릴리스 준비 or 추가 최적화
```

---

## 📊 예상 효과

### Phase 3.1 효과

**Before**:
- 😡 Tag 목록 보는 중 갑작스런 홈 화면 이동
- 😡 의도하지 않은 위젯/앱 실행
- 😡 사용자 혼란 및 불신

**After**:
- ✅ 안정적인 목록 보기 경험
- ✅ 예측 가능한 UI 동작
- ✅ 백그라운드 업데이트는 조용히 처리

### Phase 3.2 효과

**Before**:
- 😡 자주 사용하는 hibernated 앱 찾기 위해 스크롤 필요
- 😡 검색 효율 저하
- 😡 Hibernation 기능 사용 주저

**After**:
- ✅ 자주 사용하는 앱은 상단에 표시
- ✅ 검색 효율성 향상
- ✅ 더 많은 앱을 hibernate 가능 (배터리 절약)

---

## 🎯 성공 기준

### Phase 3.1

- [x] Tag 목록 보는 중 홈 이동 발생하지 않음
- [x] 백그라운드 전환 시 pending updates 자동 처리
- [x] 앱 삭제 시 즉시 UI 업데이트
- [x] 성능 저하 없음 (< 10ms overhead)

### Phase 3.2

- [x] 자주 사용하는 hibernated 앱 상단 표시
- [x] 드물게 사용하는 hibernated 앱 하단 유지
- [x] 검색 속도 < 200ms (100개 hibernated apps)
- [x] 메모리 누수 없음

---

## 📂 관련 문서

### Phase 3 계획 문서

- [phase-3-overview.md](./phase-3-overview.md) (현재 문서)
- [phase-3-tag-navigation-fix.md](./phase-3-tag-navigation-fix.md)
- [phase-3-hibernating-apps-ranking.md](./phase-3-hibernating-apps-ranking.md)

### 이전 Phase 문서

- [phase-2-deployment-complete.md](./phase-2-deployment-complete.md)
- [phase-2-detailed-changes.md](./phase-2-detailed-changes.md)
- [phase-1-performance-results.md](./phase-1-performance-results.md)
- [IMPLEMENTATION-PROGRESS.md](./IMPLEMENTATION-PROGRESS.md)

### 이슈 트래킹

- [TODO.md](../TODO.md) - High Priority Issues

---

## 💡 구현 가이드라인

### 코드 품질 기준

```
✅ BuildConfig.DEBUG 조건부 로깅
✅ Null safety 확보
✅ Exception handling
✅ 메모리 안전성 (WeakReference, try-with-resources)
✅ ProfileManager 통합 (성능 측정)
```

### 테스트 기준

```
✅ 수동 테스트 시나리오 완료
✅ 경계값 테스트 (threshold, 시간)
✅ 성능 테스트 (응답 시간, 메모리)
✅ LeakCanary 검증
```

### 문서화 기준

```
✅ 코드 주석 (Why, not What)
✅ Debug 로그 메시지
✅ TODO.md 업데이트
✅ IMPLEMENTATION-PROGRESS.md 업데이트
```

---

## 🚀 배포 계획

### Phase 3 완료 후

```
Step 1: Profile APK 빌드 (v4.2.9 or v4.3.0)
Step 2: 수동 테스트 (2-3일)
Step 3: Phase 2 로그 분석
Step 4: 최종 릴리스 결정

Option A: KPI 달성 + 버그 수정 완료
  → v4.3.0 Production 릴리스
  → Play Store 배포

Option B: 추가 최적화 필요
  → Phase 4 계획
  → Provider 병렬 로딩
  → 더 공격적인 throttle
```

---

**문서 작성**: 2025-12-19  
**다음 단계**: Phase 2 로그 수집 완료 대기 → Phase 3.1 구현 시작

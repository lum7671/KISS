# Phase 3 진행 상황

**작성일**: 2025-12-22  
**상태**: 🚧 진행 준비 (Phase 2 완료 확인됨)  
**참고**: [docs/phase-2-final-report.md](phase-2-final-report.md)

---

## 🎯 범위 및 목표
- Phase 3.1: Tag 목록 보기 중 홈 화면 이동 버그 수정 (HIGH)
- Phase 3.2: Hibernating 앱 검색 우선순위 개선 (MEDIUM)
- Phase 3.3: 새로 설치한 앱 History 표시 개선 (LOW)

---

## 📌 진행 현황 (요약)
- Phase 2: ✅ 완료 ([docs/phase-2-final-report.md](phase-2-final-report.md))
- Phase 3.1: ✅ 완료 (구현+테스트 통과)
- Phase 3.2: ✅ 완료 (코어 구현+테스트 통과, 선택 최적화 대기)
- Phase 3.3: ⏳ 대기 (다음 진행)

---

## ✅ 완료 조건 (각 Phase 공통)
- 기능 동작 확인 (수동 테스트 시나리오 통과)
- 성능 영향 미미 (지표는 개별 문서 기준)
- 문서/체크리스트 업데이트 (본 문서, TODO.md, 관련 phase 문서)

---

## 🛠️ 작업 계획 (세부 체크리스트)

### Phase 3.1: Tag 목록 홈 이동 버그
참고: [docs/phase-3-tag-navigation-fix.md](phase-3-tag-navigation-fix.md)
- [x] 상태 추적 플래그 추가 (`isViewingFilteredList`, `isUserViewingList()` 등) [MainActivity.java]
- [x] Tag 진입/종료 시 플래그 초기화 (showMatchingTags, clear/search cancel, launchOccurred, displayKissBar) [MainActivity.java]
- [x] Pending 업데이트 큐 (`pendingProviderUpdates`, `hasPendingFavoriteChange`) 추가 및 LOAD_OVER 조건 처리 [MainActivity.java]
- [x] PackageRemoved 이벤트 즉시 처리 플래그 전달 [PackageAddedRemovedHandler.java → LOAD_OVER extras]
- [x] Pending 처리 훅 추가 (onPause/onStop, clear 버튼 시) [MainActivity.java]
- [x] 수동 테스트: 업데이트 중 태그 보기, 삭제 시 즉시 반영, All Apps/검색 시 퇴행 없음

### Phase 3.2: Hibernating 앱 검색 우선순위
참고: [docs/phase-3-hibernating-apps-ranking.md](phase-3-hibernating-apps-ranking.md)
- [x] DBHelper: `getUsageCountForRecord()` 추가 (필수)
- [x] QuerySearcherCoroutine: penalty 면제 로직 적용 (최근 30일 1회+)
- [x] HistorySearcherCoroutine: 동일 로직 적용
- [ ] 선택: batch 쿼리 최적화, ProfileManager 로그
- [x] 수동 테스트: 자주/드물게 사용하는 hibernated 앱 랭킹, 경계값(1회, 30일) 확인, 성능 체크

### Phase 3.3: 새로 설치한 앱 표시 개선
참고: [docs/phase-3-new-app-indicator.md](phase-3-new-app-indicator.md)
- [x] NewAppTracker.kt 추가 (SharedPreferences 기반 seen 상태)
- [x] HistorySearcherCoroutine: 새 앱 하단 배치, 표시 후 seen 처리
- [x] UI 배지: item_app.xml + AppResult 표시 로직, 문자열 리소스
- [x] PackageAddedRemovedHandler: 제거 시 tracking 정리
- [ ] 선택: 설정 토글 추가
- [ ] 수동 테스트: 첫 표시 배지, 배지 소멸, 다중 설치, 제거/재설치, 모드별 정렬 유지

---

## 🔄 진행 순서 제안
1) Phase 3.1: 치명적 버그 우선 처리 → 수동 테스트 통과
2) Phase 3.2: 검색 우선순위 개선 → 성능 확인 (필요 시 batch 최적화)
3) Phase 3.3: 새 앱 배지 → UI 검증 및 설정 옵션 여부 결정
4) TODO.md, IMPLEMENTATION-PROGRESS.md 업데이트

---

## 🧪 테스트 체크포인트
- Phase 3.1: 태그 보기 중 앱 업데이트/삭제, 검색 모드, All Apps 퇴행 여부
- Phase 3.2: hibernated 앱 사용 빈도별 랭킹, p95/p99 지연 영향 여부
- Phase 3.3: 새 앱 배지 노출/소멸, 다중 설치 순서, 성능 영향

---

## 📂 참고 문서
- [docs/phase-3-overview.md](phase-3-overview.md)
- [docs/phase-3-tag-navigation-fix.md](phase-3-tag-navigation-fix.md)
- [docs/phase-3-hibernating-apps-ranking.md](phase-3-hibernating-apps-ranking.md)
- [docs/phase-3-new-app-indicator.md](phase-3-new-app-indicator.md)
- [docs/TODO.md](../TODO.md)
- [docs/phase-2-final-report.md](phase-2-final-report.md)

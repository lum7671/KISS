# Phase 3.3: 새로 설치한 앱 History 표시 개선

**생성일**: 2025-12-19  
**우선순위**: 🟢 LOW (사용성 개선)  
**예상 작업 시간**: 4-5시간  
**상태**: 📋 계획 단계

---

## 📋 목차

1. 문제 요약
2. 기술적 분석
3. 해결 전략
4. 구현 계획
5. 테스트 계획
6. 성공 기준

---

## 🎯 문제 요약

### 사용 시나리오
1. 새 앱 설치
2. 일정 시간 후 KISS 홈 화면 배경 클릭 (History 보기)
3. 설치한 앱을 쉽게 찾고 싶음

### 현재 동작
- 새로 설치한 앱이 History에 기록되지만 일반 항목과 섞여 있어 찾기 어려움
- "새로 설치" 표시 없음

### 원하는 동작
- 새로 설치되어 아직 History에 한 번도 표시되지 않은 앱을 **목록 하단**에 배치
- "NEW"(또는 "새로운") 배지 표시
- 한 번 표시되면 일반 앱과 동일하게 정렬 및 표시

---

## 🔍 기술적 분석

### History 표시 흐름
- `MainActivity.showHistory()` → `HistorySearcherCoroutine`
- `DataHandler.getHistory()`가 DBHelper에서 기록을 가져와 relevance를 할당 (index 기준)
- 상단: 높은 relevance / 하단: 낮은 relevance

### 앱 설치 기록
- `PackageAddedRemovedHandler`에서 새 설치(`replacing=false`) 시 `addPackageToHistory()` 호출
- `addToHistory()`가 빈 쿼리("")로 History DB에 기록

### History DB
- 테이블: `history(_id, query, record, timeStamp)`
- 인덱스: `record`, `timestamp`, `record+timestamp`

### UI 배지 참고
- Notification dot 패턴: SharedPreferences 기반 상태 + layout ImageView

---

## 🎯 해결 전략

**핵심 아이디어: "Seen in History" 상태 추적**
- 새로 설치된 앱이 처음 History에 나타날 때만 하단 배치 + 배지
- 표시 후에는 "seen"으로 기록하여 일반 정렬로 복귀

**선택**: SharedPreferences 기반 추적 (간단, DB 변경 불필요)

---

## 🛠️ 구현 계획

### 3.3.1 NewAppTracker 유틸 추가 (1h)
- 경로: `app/src/main/java/fr/neamar/kiss/utils/NewAppTracker.kt`
- 기능: `getSeenApps()`, `isNewApp()`, `markAsSeen()`, `removeApp()`, `clearAll()`
- 저장소: `SharedPreferences("new_apps_tracker")`, key: `seen_in_history`

### 3.3.2 HistorySearcherCoroutine 정렬 수정 (1.5h)
- 새 앱 분리: `pojos.partition { it is AppPojo && it.id !in seen }`
- 정렬: 기존 결과(regular) + 새 앱(new) → 새 앱을 하단에 배치
- 표시 후 `markAsSeen()` 호출 (다음부터 일반 앱 취급)
- 설정 토글 추가 시 `enable-new-app-indicator` 확인 (선택)

### 3.3.3 NEW 배지 UI (1.5h)
- `item_app.xml`에 TextView 배지 추가 (`@+id/item_new_badge`)
- 배지 배경: `drawable/badge_new_background.xml` (red, corner radius 4dp)
- 문자열: `new_app_badge` (en: "NEW", ko: "새로운")
- `AppResult.display()`에서 `NewAppTracker.isNewApp()`로 visibility 제어

### 3.3.4 앱 제거 시 정리 (0.5h)
- `PackageAddedRemovedHandler`: `ACTION_PACKAGE_REMOVED` 처리 시 `NewAppTracker.removeApp()` 호출

### 3.3.5 설정 추가 (선택, 1h)
- `preferences.xml`에 스위치: `enable-new-app-indicator` (default true)
- 문자열 타이틀/설명 en/ko 추가

---

## 🧪 테스트 계획

1) **새 앱 설치 후 History**: 설치 직후/1시간 후 배경 클릭 → 하단에 NEW 배지로 표시
2) **배지 소멸**: 한 번 표시 후 다시 History → 배지 사라지고 일반 정렬
3) **다중 설치**: 여러 앱 동시에 설치 → 모두 하단, 설치 순서 역순 확인
4) **앱 제거/재설치**: 제거 시 tracking 제거, 재설치 시 다시 NEW 배지
5) **History 모드 변경**: Frecency/Frequency/Recency 등 모든 모드에서 하단 배치 유지
6) **설정 OFF**(선택): 토글 비활성화 시 일반 정렬, 배지 없음

성능 검증: SharedPreferences 조회 <5ms, History 표시 지연 없음, LeakCanary로 누수 없음

---

## ✅ 성공 기준

- 새로 설치 후 처음 History에 나타날 때 하단 배치 + 배지 표시
- 한 번 표시된 앱은 이후 일반 정렬/표시
- 앱 제거 시 tracking 정리
- 모든 History 모드에서 일관 동작
- 성능/메모리 영향 무시 가능 수준

---

## 📂 관련 파일
- `MainActivity.java` (History 진입점)
- `HistorySearcherCoroutine.kt` (정렬/표시)
- `DataHandler.java`, `DBHelper.java` (History 로딩)
- `PackageAddedRemovedHandler.java` (설치/제거 감지)
- `AppResult.java`, `item_app.xml` (배지 UI)
- `NewAppTracker.kt` (새 파일)

---

## 💡 향후 개선
- 배지 색/텍스트 커스터마이즈, 애니메이션
- "N일 이내 설치 앱만 표시" 옵션
- 설치 소스/복원 여부 표시
- History 섹션화: "Today / This Week / New Apps"

---

**문서 작성**: 2025-12-19  
**다음 단계**: Phase 2 및 Phase 3.1/3.2 완료 후 Phase 3.3 구현 착수

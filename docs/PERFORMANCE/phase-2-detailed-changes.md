# Phase 2 상세 변경점 분석

**최종 업데이트**: 2025-12-16  
**상태**: 코드 미반영, 검토 전용

---

## S1: Searcher 캐시 제거 ✅ (완료됨)

### 상태: **이미 구현됨**
- `QuerySearcherCoroutine.kt` (L51-52): 주석에 "Phase 2 Step 4" 표시
- `HistorySearcherCoroutine.kt` (L37-40): 정적 캐시 제거, 인스턴스 변수 사용

```kotlin
// 기존(제거됨): static maxResultCountCache
// 현재: private var maxResultCount: Int? = null
```

**추가 점검**: 다른 Searcher 구현체가 있는지 확인
- `QuerySearcher.java` (구 코드, 이미 대체됨)
- `SearcherCoroutine.kt` (기본 클래스)

→ **S1 비용: 0** (이미 완료)

---

## S2: RecordAdapter 및 Result 성능 최적화

### 현재 상태 분석

#### 2.1 RecordAdapter.updateResults() (L112-119)

```java
public void updateResults(@NonNull Context context, List<Result<?>> results, 
                          boolean isRefresh, String query) {
    this.results.clear();
    this.results.addAll(results);  // ← 할당 O(n)
    StringNormalizer.Result queryNormalized = StringNormalizer.normalizeWithResult(query, false);  // ← 매번 생성
    fuzzyScore = FuzzyFactory.createFuzzyScore(context, queryNormalized.codePoints, true);  // ← 매번 생성
    notifyDataSetChanged();  // ← 전체 리프레시
    ...
}
```

**최적화 아이디어**:
- `fuzzyScore` 재사용: 같은 쿼리면 재사용 + 캐시 무효화 명확화
- `queryNormalized` 지연 생성: 필요 시에만 생성

**변경점**:
- `String lastQuery` 추가 필드: 이전 쿼리 저장
- `fuzzyScore` 재사용 조건 추가: `!query.equals(lastQuery)` 체크
- 주석: "getView()에서 FuzzyScore 동시 사용, Thread-safe 또는 단일 스레드"

**예상 효과**: 연속 입력 시 FuzzyFactory 호출 50% 감소

#### 2.2 RecordAdapter.getView() (L87-90)

```java
public View getView(int position, View convertView, @NonNull ViewGroup parent) {
    return results.get(position).display(parent.getContext(), convertView, parent, fuzzyScore);
}
```

**점검 사항**:
- `convertView` 재사용 여부 → `Result.display()` 구현마다 다름
- `fuzzyScore` null-safe 여부

**변경점**: 
- null-guard 추가: `if (fuzzyScore == null) { fuzzyScore = createDefaultFuzzyScore(...); }`

#### 2.3 Result.display() 구현체 (예: AppResult, SearchResult 등)

**점검 대상**:
- `app/src/main/java/fr/neamar/kiss/result/*.java`
- 특히: `AppResult.display()`, `ContactsResult.display()`, `SearchResult.display()`

**검사 항목**:
1. `convertView` 타입 일치 확인 (올바른 레이아웃 재사용)
2. 불필요한 `String` 또는 `ArrayList` 생성 여부
3. 핫패스에서 정규식 컴파일, 이미지 로드 등

**기대 효과**:
- 메모리 할당 30% 감소
- GC 빈도 개선
- 스크롤 프레임 드랍 50% 감소

---

## S3: Provider ensureLoaded() 타이밍 및 오버헤드 최소화

### 현재 구조

#### 3.1 Provider.ensureLoaded() (구현된 상태)

```java
// app/src/main/java/fr/neamar/kiss/dataprovider/Provider.java
protected synchronized void ensureLoaded() {
    if (!isInitialized) {
        reload();
        isInitialized = true;
    }
}
```

**현재 호출 위치**:
- `ContactsProvider.requestResults()` (L1)
- `ShortcutsProvider.requestResults()` (L1)
- 첫 검색 시 자동 트리거

#### 3.2 첫 검색 오버헤드 분석

**시나리오**: 사용자가 `a` 입력
1. `MainActivity.updateSearchRecords()` 호출
2. Searchers 실행 (QuerySearcher 중심)
3. `QuerySearcher` → Providers에 `requestResults()` 요청
4. `ContactsProvider.requestResults()` 첫 호출 → `ensureLoaded()` 트리거
5. `reload()` 수행 (DB 쿼리, 메모리 할당)
6. **총 추가 지연: 200-300ms**

**최적화 전략**:
- 옵션 A (현재): 첫 검색에서 지연 수용 → 이후 검색은 캐시 히트
- 옵션 B (프리웜): 앱 시작 후 1초 뒤 백그라운드에서 `ensureLoaded()` 호출
  - 장점: 첫 검색이 빠름
  - 단점: 초기 메모리/배터리 추가 비용
  - 제약: 너무 이르면 불필요한 로드, 너무 늦으면 첫 검색과 겹칠 수 있음

#### 3.3 변경점 (보수적 접근: 옵션 A 유지)

**의도**:
- 현재 구조 유지 (지연 로드가 올바름)
- 대신 계측 추가하여 "첫 검색 지연" 정량화

**변경 파일**:
- `MainActivity.java`: 첫 검색 vs 후속 검색 구분 계측 추가
- `DataHandler.java`: 리로드 요청 이벤트 추가 (RELOAD_REQUESTED)

**예상 비용**: 낮음 (계측만)

---

## S4: 계측 확장 (COLD_START, RELOAD_REQUESTED, p95/p99)

### 4.1 MainActivity에 추가할 이벤트

#### COLD_START 이벤트

```java
// MainActivity.onCreate() 이후 최초 UI 상호작용 가능 시점
// 예: 첫 검색 쿼리 입력 가능, 또는 리스트 표시 완료

// 시점 1: onCreate() 완료 후
private void onCreateCompleted() {
    ProfileManager.getInstance().logEvent(
        "COLD_START",
        "MAIN_ACTIVITY_CREATED," + System.currentTimeMillis()
    );
}

// 시점 2: 첫 리스트 표시 (beforeListChange)
@Override
public void beforeListChange() {
    if (!isFirstListDisplayed) {
        long ttfb = System.currentTimeMillis() - appStartTime;
        ProfileManager.getInstance().logEvent(
            "COLD_START_TTFB",
            "first_list_displayed:" + ttfb + "ms"
        );
        isFirstListDisplayed = true;
    }
}
```

#### RELOAD_REQUESTED 이벤트

```java
// DataHandler.shouldReload() / forceReload() 경로
public boolean shouldReload() {
    long now = System.currentTimeMillis();
    ProfileManager.getInstance().logEvent(
        "RELOAD_REQUESTED",
        "time_since_last:" + (now - lastReloadTime) + "ms"
    );
    
    if ((now - lastReloadTime) < RELOAD_THROTTLE_MS) {
        ProfileManager.getInstance().logEvent(
            "RELOAD_THROTTLED",
            "throttle_ms:2000"
        );
        return false;
    }
    lastReloadTime = now;
    return true;
}
```

### 4.2 분석 스크립트 확장

**파일**: `utils/analyze_profile_custom_events.py`

**추가 계산**:
- p50, p95, p99 (현재는 평균만)
- 스로틀 비율: `RELOAD_THROTTLED count / RELOAD_REQUESTED count`
- 첫 검색 지연: SEARCH 이벤트 중 첫 번째 vs 이후 비교

```python
# 예시
import numpy as np

def compute_percentiles(durations):
    return {
        'p50': np.percentile(durations, 50),
        'p95': np.percentile(durations, 95),
        'p99': np.percentile(durations, 99),
    }

throttle_ratio = throttled_count / requested_count
```

**예상 비용**: 1-2시간

---

## S5: UI 비동기 안전성 가이드

### 현재 상태 (Settings 크래시 수정 이후)

**개선된 패턴** (SettingsFragment에 적용됨):
```kotlin
CoroutineUtils.runAsync(() -> { }, runnable)  // 백그라운드 = 빈 것, 콜백 = UI 작업
```

**일반화 가이드**:
- UI 업데이트가 필요하면 → `runAsyncWithLifecycle()` 추천
- 단순 배경 작업만 필요하면 → `execute()` 계속 사용

**변경점**: 선택사항
- `MainActivity` 내 비동기 경로 점검 (현재는 안전한 것으로 보임)
- 문서화: `CoroutineUtils.kt` 주석에 사용 가이드 보강

**예상 비용**: 0.5시간 (문서화만)

---

## 📋 변경점 요약 (실제 코드 반영 순서)

| # | Step | 파일 | 변경점 | 예상 비용 |
|---|------|------|--------|----------|
| 1 | S1 | - | 이미 완료 | 0h |
| 2 | S2 | RecordAdapter.java | fuzzyScore 캐시, null-guard | 2h |
| 2b | S2 | result/*.java | Result.display() 할당 최소화 | 2-3h |
| 3 | S3 | Provider.java, *Provider.java | 계측 추가 (현재 구조 유지) | 1h |
| 4 | S4 | MainActivity.java, DataHandler.java | COLD_START, RELOAD_REQUESTED 이벤트 | 1.5h |
| 4b | S4 | analyze_profile_custom_events.py | p95/p99, 스로틀 비율 | 1.5h |
| 5 | S5 | CoroutineUtils.kt, MainActivity.java | 가이드 문서화 | 0.5h |

**총 예상 비용**: 8-10시간

---

## 🔍 검증 계획

1. **단위 검증**:
   - S2 후: 메모리 프로파일링 (RecordAdapter 할당 추이)
   - S3 후: 첫 검색 vs 후속 검색 지연 비교
   - S4 후: p50/p95/p99 통계 확인

2. **통합 검증**:
   - Profile APK 빌드
   - 콜드스타트 10회, onResume 20회, 검색 20회 반복
   - CSV 분석: COLD_START_TTFB, 스로틀 비율 확인

3. **기준 (Pass/Fail)**:
   - p95 검색 ≤ 8ms ✓
   - 스로틀 비율 ≥ 60% ✓
   - 크래시 없음 ✓

---

**다음 단계**: 사용자 검토 후 S2부터 순차 구현


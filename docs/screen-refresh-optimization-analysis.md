# 화면 Refresh 최적화 분석

**작성일**: 2025년 8월 21일  
**목적**: GUI refresh를 최대한 제한하고 백그라운드 처리로 사용자 작업 방해 최소화

## 🔍 현재 문제점 분석

### 1. 주요 문제 상황

- 즐겨찾기 목록이나 전체 목록을 보고 있을 때
- refresh, 아이콘 변경, 목록 정렬 등의 작업 시
- 현재 보던 목록이 닫히고 KISS 앱의 초기 화면으로 되돌아감
- 사용자 작업 흐름이 중단됨

### 2. 문제 발생 코드 위치

#### 2.1 MainActivity.onResume() - 자동 리셋 문제

**파일**: `MainActivity.java` (라인 668-694)

```java
@SuppressLint("CommitPrefEdits")
protected void onResume() {
    // ... 
    if (isViewingAllApps()) {
        displayKissBar(false);  // ← 문제: 앱 목록을 강제로 닫음
    }
    // ...
    if (KissApplication.getApplication(this).getDataHandler().shouldUpdateOnResume()) {
        updateSearchRecords();  // ← 문제: 강제 업데이트로 현재 목록 초기화
    }
}
```

**문제점**:

- 앱이 resume될 때마다 앱 목록이 자동으로 닫힘
- 데이터 변경 감지 시 무조건 화면 새로고침

#### 2.2 MainActivity.onNewIntent() - 홈 버튼 재클릭 문제

**파일**: `MainActivity.java` (라인 765-768)

```java
@Override
protected void onNewIntent(Intent intent) {
    // ...
    if (isViewingAllApps()) {
        displayKissBar(false);  // ← 문제: 홈 버튼 재클릭 시 목록 닫음
    }
}
```

**문제점**:

- 홈 버튼을 다시 눌렀을 때 현재 보던 목록이 강제로 닫힘

#### 2.3 DataHandler.shouldUpdateOnResume() - 과도한 업데이트 트리거

**파일**: `DataHandler.java` (라인 502-520)

```java
public boolean shouldUpdateOnResume() {
    long currentTime = System.currentTimeMillis();
    long timeSinceLastUpdate = currentTime - lastDataUpdateTime;
    long timeSinceLastResume = currentTime - lastResumeTime;
    
    lastResumeTime = currentTime;
    
    return timeSinceLastUpdate > UPDATE_THRESHOLD_MS && timeSinceLastResume > 1000;
}
```

**문제점**:

- 시간 기반으로만 업데이트 필요성 판단
- 현재 사용자가 보고 있는 화면 상태 무시

#### 2.4 Favorites.onFavoriteChange() - 즐겨찾기 변경 시 강제 새로고침

**파일**: `Favorites.java` (라인 124-200)

```java
void onFavoriteChange() {
    // 즐겨찾기 목록 전체 재구성
    // 현재 사용자가 보고 있는 화면 상태와 무관하게 실행
}
```

**문제점**:

- 즐겨찾기 변경 시 현재 화면 상태와 관계없이 전체 UI 재구성

## 📊 영향도 분석

### 높은 영향도 (즉시 수정 필요)

1. **onResume() 자동 리셋** - 사용자 경험에 가장 큰 악영향
2. **onNewIntent() 강제 닫기** - 홈 버튼 사용 시 항상 발생

### 중간 영향도 (점진적 개선)

1. **shouldUpdateOnResume() 로직** - 불필요한 업데이트 방지
2. **onFavoriteChange() 최적화** - 백그라운드 처리 가능

### 낮은 영향도 (장기적 개선)

1. **ExperienceTweaks 제스처 처리** - 특정 상황에서만 발생

## 🎯 해결 전략 (단계별)

### Phase 1: 긴급 수정 (사용자 작업 중단 방지)

1. **현재 화면 상태 추적 시스템 구현**
   - 사용자가 보고 있는 화면 타입 저장 (즐겨찾기, 앱 목록, 검색 결과 등)
   - 각 상태별 적절한 처리 로직 분기

2. **onResume() 스마트 처리**
   - 현재 화면 상태 확인 후 필요한 경우에만 변경
   - 백그라운드 데이터 업데이트와 UI 업데이트 분리

3. **onNewIntent() 조건부 처리**
   - 사용자 의도 파악 (단순 홈 복귀 vs 새로운 작업 시작)
   - 필요한 경우에만 화면 초기화

### Phase 2: 백그라운드 처리 최적화

1. **비동기 데이터 업데이트**
   - 데이터 변경을 백그라운드에서 처리
   - UI 업데이트는 사용자가 해당 화면을 볼 때만 실행

2. **스마트 캐싱 시스템**
   - 자주 사용하는 데이터 미리 준비
   - 화면 전환 시 즉시 표시 가능

3. **업데이트 우선순위 시스템**
   - 현재 보고 있는 화면 관련 업데이트 우선 처리
   - 백그라운드 화면 업데이트는 지연 처리

### Phase 3: 장기적 아키텍처 개선

1. **상태 관리 시스템 도입**
   - 현재 화면 상태와 데이터 상태 분리
   - 상태 변경 시 최소한의 UI 업데이트만 수행

2. **이벤트 기반 업데이트**
   - 실제 변경이 발생한 부분만 업데이트
   - 불필요한 전체 화면 새로고침 제거

## 📋 구현 계획

### 1단계: 현재 상태 추적 (Week 1)

- [ ] 화면 상태 enum 정의
- [ ] 현재 상태 추적 변수 추가
- [ ] 상태 변경 감지 로직 구현

### 2단계: 조건부 업데이트 (Week 2)

- [ ] onResume() 스마트 처리 구현
- [ ] onNewIntent() 조건부 처리 구현
- [ ] shouldUpdateOnResume() 로직 개선

### 3단계: 백그라운드 처리 (Week 3)

- [ ] 비동기 데이터 업데이트 구현
- [ ] UI 업데이트와 데이터 업데이트 분리
- [ ] 즐겨찾기 변경 백그라운드 처리

### 4단계: 테스트 및 최적화 (Week 4)

- [ ] 각 시나리오별 테스트
- [ ] 성능 측정 및 최적화
- [ ] 사용자 피드백 수집

## ⚠️ 주의사항

1. **호환성 유지**: 기존 기능 동작 방식 보장
2. **성능 영향**: 백그라운드 처리가 과도한 리소스 사용하지 않도록 주의
3. **사용자 경험**: 변경 사항이 사용자에게 혼란을 주지 않도록 점진적 적용

## 📈 예상 효과

### 사용자 경험 개선

- ✅ 작업 중단 없이 연속적인 앱 사용 가능
- ✅ 즐겨찾기/앱 목록 보기 중 refresh 시에도 화면 유지
- ✅ 반응성 향상으로 더 빠른 앱 실행 체감

### 시스템 성능 개선

- ✅ 불필요한 UI 업데이트 감소로 배터리 절약
- ✅ 백그라운드 처리로 메인 스레드 부하 감소
- ✅ 스마트 캐싱으로 데이터 로딩 시간 단축

## 🎨 아이콘 Refresh 주요 원인 분석

### 1. 설정 변경으로 인한 아이콘 캐시 전체 삭제

**주요 트리거**: `IconsHandler.onPrefChanged()` (라인 165)

```java
public void onPrefChanged(SharedPreferences pref, String key) {
    if (key.equalsIgnoreCase("icons-pack") ||
            key.equalsIgnoreCase("adaptive-shape") ||
            key.equalsIgnoreCase("force-adaptive") ||
            key.equalsIgnoreCase("force-shape") ||
            key.equalsIgnoreCase("contact-pack-mask") ||
            key.equalsIgnoreCase("contacts-shape") ||
            key.equalsIgnoreCase(DrawableUtils.KEY_THEMED_ICONS)) {
        cacheClear();  // ← 전체 아이콘 캐시 삭제!
        // ...아이콘 팩 재로딩...
    }
}
```

**문제점**:
- 아이콘 관련 설정 하나만 변경해도 전체 아이콘 캐시가 삭제됨
- 모든 아이콘을 다시 로딩해야 함
- `cacheClear()`가 파일 시스템에서 물리적으로 캐시 파일들을 삭제

### 2. RecordAdapter.notifyDataSetChanged() 과다 호출

**주요 트리거**: `RecordAdapter.updateResults()` (라인 133)

```java
public void updateResults(@NonNull Context context, List<Result<?>> results, boolean isRefresh, String query) {
    this.results.clear();
    this.results.addAll(results);
    // ...
    notifyDataSetChanged();  // ← 전체 리스트 뷰 새로고침!
}
```

**문제점**:
- 검색 결과 업데이트 시 전체 리스트뷰가 다시 그려짐
- 각 아이템의 아이콘도 다시 로딩됨
- `isRefresh=true`인 경우에도 전체 새로고침 수행

### 3. 설정 화면에서의 연쇄 refresh

**주요 트리거**: `SettingsActivity.onSharedPreferenceChanged()` (라인 628)

```java
public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
    if (key != null) {
        KissApplication.getApplication(this).getIconsHandler().onPrefChanged(sharedPreferences, key);
        // 모든 설정 변경마다 아이콘 핸들러 호출
    }
}
```

**문제점**:
- 아이콘과 무관한 설정 변경 시에도 아이콘 핸들러 호출
- 불필요한 아이콘 시스템 체크 및 처리

### 4. 화면 전환 시 아이콘 재로딩

**주요 트리거**: 앱 전환 후 복귀, onResume() 등

```java
// MainActivity에서 화면 복귀 시
adapter.notifyDataSetChanged(); // 뷰 갱신 중단
```

**문제점**:
- 화면 전환 후 복귀 시 불필요한 아이콘 재로딩
- 메모리에서 아이콘이 해제된 후 다시 로딩

## 🎯 아이콘 Refresh 최적화 방안

### 1. 선택적 캐시 삭제 (Selective Cache Clear)

```java
// 개선 방향
private void cacheSelectiveClear(String changeType) {
    switch (changeType) {
        case "icons-pack":
            // 아이콘 팩만 삭제, 시스템 아이콘은 유지
            break;
        case "adaptive-shape":
            // adaptive 아이콘만 삭제
            break;
        case "contact-settings":
            // 연락처 아이콘만 삭제
            break;
    }
}
```

### 2. 스마트 View 업데이트

```java
// 개선 방향: 부분 업데이트
public void updateResultsSelectively(List<Result<?>> newResults, List<Result<?>> changedResults) {
    // 변경된 아이템만 업데이트
    for (Result<?> changed : changedResults) {
        notifyItemChanged(results.indexOf(changed));
    }
}
```

### 3. 백그라운드 아이콘 Preloading

```java
// 개선 방향: 백그라운드에서 미리 로딩
private void preloadIconsInBackground() {
    // 자주 사용하는 앱들의 아이콘을 백그라운드에서 미리 준비
    // 사용자가 해당 화면을 볼 때 즉시 표시
}
```

### 4. 아이콘 캐시 지능화

```java
// 개선 방향: 스마트 캐시 관리
public class SmartIconCache {
    // 사용 빈도 기반 캐시 우선순위
    // 메모리 압박 시 선택적 해제
    // 설정 변경 시 부분적 무효화
}
```

## 📊 아이콘 Refresh 영향도

### 높은 영향도
1. **설정 변경 시 전체 캐시 삭제** - 모든 아이콘 재로딩 필요
2. **notifyDataSetChanged 과다 호출** - 매번 전체 리스트 새로고침

### 중간 영향도
1. **화면 전환 시 재로딩** - 일부 아이콘만 영향
2. **메모리 압박 시 캐시 해제** - 점진적 성능 저하

### 낮은 영향도
1. **개별 아이콘 로딩 실패** - 특정 앱만 영향

---

**다음 단계**: Phase 1 구현을 위한 상세 설계 문서 작성

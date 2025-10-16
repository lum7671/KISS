# 아이콘 Refresh 최적화 분석

**작성일**: 2025년 8월 21일  
**목적**: 아이콘 새로고침으로 인한 사용자 경험 저하 문제 해결

## 🎨 아이콘 Refresh 문제 현황

### 주요 증상

1. **설정 변경 시 모든 아이콘이 사라졌다가 다시 나타남**
2. **앱 목록 스크롤 중 아이콘이 깜빡거림**
3. **화면 전환 후 아이콘 로딩 지연**
4. **아이콘 팩 변경 시 긴 대기 시간**

## 🔍 근본 원인 분석

### 1. 전역 캐시 삭제 문제

**위치**: `IconsHandler.cacheClear()` (라인 574)

```java
private void cacheClear() {
    TagDummyResult.resetShape();
    clearCustomIconIdCache();

    File cacheDir = this.getIconsCacheDir();
    File[] fileList = cacheDir.listFiles();
    if (fileList != null) {
        for (File item : fileList) {
            if (!item.delete()) {
                Log.w(TAG, "Failed to delete file: " + item.getAbsolutePath());
            }
        }
    }
}
```

**문제점**:

- 아이콘 관련 설정 하나만 변경해도 전체 캐시 삭제
- 파일 시스템에서 물리적 삭제 수행
- 수백 개의 아이콘을 다시 생성해야 함

### 2. 설정 변경 트리거 과다

**위치**: `IconsHandler.onPrefChanged()` (라인 165)

```java
public void onPrefChanged(SharedPreferences pref, String key) {
    if (key.equalsIgnoreCase("icons-pack") ||
            key.equalsIgnoreCase("adaptive-shape") ||
            key.equalsIgnoreCase("force-adaptive") ||
            key.equalsIgnoreCase("force-shape") ||
            key.equalsIgnoreCase("contact-pack-mask") ||
            key.equalsIgnoreCase("contacts-shape") ||
            key.equalsIgnoreCase(DrawableUtils.KEY_THEMED_ICONS)) {
        cacheClear();  // ← 7개 설정 중 하나만 변경해도 전체 삭제
    }
}
```

**문제점**:

- 너무 많은 설정이 전체 캐시 삭제를 트리거
- 설정 간의 의존성 무시
- 부분 업데이트 불가능

### 3. UI 업데이트 방식의 비효율성

**위치**: `RecordAdapter.updateResults()` (라인 133)

```java
public void updateResults(@NonNull Context context, List<Result<?>> results, boolean isRefresh, String query) {
    this.results.clear();
    this.results.addAll(results);
    StringNormalizer.Result queryNormalized = StringNormalizer.normalizeWithResult(query, false);
    fuzzyScore = FuzzyFactory.createFuzzyScore(context, queryNormalized.codePoints, true);
    notifyDataSetChanged();  // ← 전체 리스트 새로고침
}
```

**문제점**:

- `isRefresh=true`인 경우에도 전체 새로고침
- 변경되지 않은 아이템도 다시 그려짐
- 각 아이템의 아이콘도 재로딩됨

### 4. 메모리 관리 부족

**현재 상황**:

- 아이콘 캐시의 LRU 정책 부재
- 메모리 압박 시 무차별 해제
- 화면 전환 시 불필요한 해제

## 📊 성능 영향 측정

### 현재 성능 지표 (추정)

1. **설정 변경 시 아이콘 로딩 시간**: 3-5초
2. **앱 목록 스크롤 시 끊김**: 100-200ms 지연
3. **메모리 사용량**: 아이콘당 평균 50KB
4. **캐시 히트율**: 약 60-70%

### 목표 성능 지표

1. **설정 변경 시 아이콘 로딩 시간**: 0.5-1초
2. **앱 목록 스크롤 시 끊김**: 16ms 이하 (60fps)
3. **메모리 사용량**: 현재와 동일 유지
4. **캐시 히트율**: 90% 이상

## 🎯 최적화 전략

### Phase 1: 긴급 수정 (스마트 캐시 삭제)

#### 1.1 선택적 캐시 무효화

```java
// 개선안
public enum CacheInvalidationType {
    ICON_PACK_ONLY,     // 아이콘 팩만 변경
    ADAPTIVE_ONLY,      // adaptive 설정만 변경
    CONTACTS_ONLY,      // 연락처 관련만 변경
    SHAPE_ONLY,         // 모양 관련만 변경
    FULL_CLEAR          // 전체 삭제 (마지막 수단)
}

private void cacheInvalidate(CacheInvalidationType type, String newValue) {
    switch (type) {
        case ICON_PACK_ONLY:
            // 아이콘 팩 관련 캐시만 삭제
            invalidateIconPackCache();
            break;
        case ADAPTIVE_ONLY:
            // adaptive 아이콘만 재생성 필요한 것들만 삭제
            invalidateAdaptiveCache();
            break;
        // ...
    }
}
```

#### 1.2 설정 변경 분석

```java
// 개선안
public void onPrefChanged(SharedPreferences pref, String key) {
    String newValue = getStringValue(pref, key);
    String oldValue = getCurrentValue(key);
    
    if (Objects.equals(newValue, oldValue)) {
        return; // 값이 실제로 변경되지 않음
    }
    
    switch (key) {
        case "icons-pack":
            cacheInvalidate(CacheInvalidationType.ICON_PACK_ONLY, newValue);
            break;
        case "adaptive-shape":
            cacheInvalidate(CacheInvalidationType.ADAPTIVE_ONLY, newValue);
            break;
        // ...
    }
}
```

### Phase 2: 스마트 UI 업데이트

#### 2.1 부분 업데이트 구현

```java
// 개선안
public void updateResultsIncrementally(List<Result<?>> newResults, boolean isRefresh) {
    if (!isRefresh) {
        // 새로운 검색은 전체 업데이트
        updateResults(newResults, false);
        return;
    }
    
    // refresh인 경우 차이점만 업데이트
    List<Integer> changedPositions = findChangedPositions(this.results, newResults);
    this.results.clear();
    this.results.addAll(newResults);
    
    // 변경된 위치만 업데이트
    for (int position : changedPositions) {
        notifyItemChanged(position);
    }
}
```

#### 2.2 백그라운드 아이콘 준비

```java
// 개선안
public class BackgroundIconLoader {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    
    public void preloadIcons(List<ComponentName> upcomingApps) {
        executor.submit(() -> {
            for (ComponentName component : upcomingApps) {
                if (!iconCache.contains(component)) {
                    Drawable icon = loadIconSynchronously(component);
                    iconCache.put(component, icon);
                }
            }
        });
    }
}
```

### Phase 3: 고급 최적화

#### 3.1 지능형 캐시 관리

```java
// 개선안
public class IntelligentIconCache {
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
    
    static class CacheEntry {
        final Drawable icon;
        final long lastAccessTime;
        final int accessCount;
        final CacheInvalidationType dependencies;
        
        // 접근 빈도와 의존성을 고려한 스마트 캐시
    }
    
    public void invalidateByType(CacheInvalidationType type) {
        cache.entrySet().removeIf(entry -> 
            entry.getValue().dependencies.includes(type));
    }
}
```

#### 3.2 메모리 압박 대응

```java
// 개선안
public class MemoryAwareIconManager {
    private final MemoryWatcher memoryWatcher = new MemoryWatcher();
    
    public void onMemoryPressure(MemoryLevel level) {
        switch (level) {
            case LOW:
                // 사용 빈도 낮은 아이콘만 해제
                releaseInfrequentIcons();
                break;
            case CRITICAL:
                // 현재 화면 아이콘만 유지
                releaseOffScreenIcons();
                break;
        }
    }
}
```

## 📋 구현 로드맵

### Week 1: 긴급 수정

- [ ] 선택적 캐시 무효화 구현
- [ ] 설정 변경 분석 로직 개선
- [ ] 기본적인 부분 업데이트 구현

### Week 2: 스마트 업데이트

- [ ] RecordAdapter 부분 업데이트 구현
- [ ] 백그라운드 아이콘 로더 구현
- [ ] 메모리 압박 감지 시스템

### Week 3: 고급 최적화

- [ ] 지능형 캐시 관리 시스템
- [ ] 접근 패턴 분석 및 예측 로딩
- [ ] 성능 측정 및 튜닝

### Week 4: 테스트 및 검증

- [ ] 다양한 시나리오 테스트
- [ ] 성능 벤치마크 수행
- [ ] 사용자 피드백 수집

## 🧪 테스트 계획

### 성능 테스트

1. **아이콘 로딩 시간 측정**

   ```bash
   # 설정 변경 전후 시간 측정
   adb shell am start -W kr.lum7671.kiss/.SettingsActivity
   # 아이콘 팩 변경 시간 측정
   ```

2. **메모리 사용량 모니터링**

   ```bash
   # 아이콘 캐시 메모리 사용량
   adb shell dumpsys meminfo kr.lum7671.kiss | grep -i icon
   ```

3. **UI 반응성 측정**

   ```bash
   # 스크롤 성능 측정
   adb shell dumpsys gfxinfo kr.lum7671.kiss
   ```

### 사용자 시나리오 테스트

1. **아이콘 팩 변경 시나리오**
   - 아이콘 팩 변경 → 즉시 적용 확인
   - 메모리 사용량 증가 여부 확인

2. **대량 앱 설치 시나리오**
   - 100개 이상 앱 설치 환경에서 테스트
   - 스크롤 성능 및 아이콘 로딩 속도 확인

3. **메모리 부족 시나리오**
   - 의도적으로 메모리 압박 상황 생성
   - 아이콘 캐시 관리 동작 확인

## 📈 성공 지표

### 정량적 지표

1. **아이콘 로딩 시간**: 현재 대비 80% 단축
2. **캐시 히트율**: 90% 이상 달성
3. **메모리 사용량**: 현재 수준 유지
4. **UI 프레임 드롭**: 5% 이하

### 정성적 지표

1. **사용자 체감 반응성**: 아이콘 깜빡임 현상 제거
2. **설정 변경 즉시성**: 설정 변경 시 즉시 반영
3. **앱 목록 스크롤 부드러움**: 끊김 현상 제거

## ⚠️ 주의사항

1. **기존 캐시 호환성**: 기존 아이콘 캐시와의 호환성 유지
2. **메모리 누수 방지**: 새로운 캐시 시스템에서 메모리 누수 주의
3. **성능 회귀 방지**: 최적화로 인한 다른 성능 저하 방지

---

**연관 문서**: `screen-refresh-optimization-analysis.md`, `phase1-ui-state-tracking-design.md`

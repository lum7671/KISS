# Step 4: Remaining Searchers Migration Plan

**Branch**: `step4-remaining-searchers`  
**Date**: 2025-10-14  
**Status**: 🚧 In Progress

---

## 🎯 목표

남은 5개 Searcher 클래스를 Coroutines로 마이그레이션:
1. **NullSearcher** (가장 간단 - 시작하기 좋음)
2. **HistorySearcher** (QuerySearcher와 유사)
3. **ApplicationsSearcher** (custom PriorityQueue)
4. **TagsSearcher** (PojoWithTagSearcher 상속)
5. **UntaggedSearcher** (PojoWithTagSearcher 상속)

---

## 📊 복잡도 분석

### 1. NullSearcher ⭐ (가장 간단)
**LOC**: 22 lines  
**Complexity**: Very Low  
**Special Features**: 
- Empty doInBackground() (아무것도 안 함)
- Override displayActivityLoader() (loader 표시 안 함)

**Migration Strategy**:
```kotlin
class NullSearcherCoroutine(activity: MainActivity) 
    : SearcherCoroutine(activity, "<null>", false) {
    
    override fun displayActivityLoader() {
        // Don't display loader
    }
    
    override fun doInBackground(): Unit {
        // nothing found ;)
    }
}
```

**Estimated Time**: 10 minutes

---

### 2. HistorySearcher ⭐⭐ (Medium)
**LOC**: 105 lines  
**Complexity**: Medium  
**Similar to**: QuerySearcher (DB queries, relevance adjustments)

**Special Features**:
- SharedPreferences 사용 (getMaxResultCount)
- DataHandler.getHistory() 호출
- Exclude favorites/history logic
- Disabled items penalty (-200)
- Shortcut handling (API 26+)

**Key Code**:
```java
@Override
protected void doInBackground() {
    // Gather excluded items
    Set<String> excludedPojoById = new HashSet<>(excludedFromHistory);
    
    // Add shortcuts for excluded apps (API 26+)
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
        // ShortcutUtil operations
    }
    
    // Get history from DataHandler
    List<Pojo> pojos = dataHandler.getHistory(activity, getMaxResultCount(), excludedPojoById);
    this.addResults(pojos);
}

@Override
public boolean addResults(List<? extends Pojo> pojos) {
    if (historyMode != ALPHABETICALLY) {
        for (Pojo pojo : pojos) {
            if (pojo.isDisabled()) {
                pojo.relevance -= 200;  // Penalty
            }
        }
    }
    return super.addResults(pojos);
}
```

**Migration Strategy**:
- Static cache for getMaxResultCount() (@Volatile)
- 1:1 logic preservation (no optimization)
- ShortcutUtil API 26+ handling preserved

**Estimated Time**: 30-45 minutes

---

### 3. ApplicationsSearcher ⭐⭐ (Medium)
**LOC**: 87 lines  
**Complexity**: Medium  
**Special Features**:
- **Custom PriorityQueue**: ReversedNameComparator (A→Z sorting)
- getMaxResultCount() = Integer.MAX_VALUE
- Filter favorites logic
- **onPostExecute() override**: adapter.buildSections()

**Key Code**:
```java
@Override
PriorityQueue<Pojo> getPojoProcessor(Context context) {
    // Sort from A to Z, reverse for ListView (bottom to top)
    return new PriorityQueue<>(DEFAULT_MAX_RESULTS, new ReversedNameComparator());
}

@Override
protected void onPostExecute() {
    super.onPostExecute();
    activity.adapter.buildSections();  // Build fast scroll sections
}

private <T extends Pojo> List<T> getPojosWithoutFavorites(List<T> pojos, Set<String> excludedFavoriteIds) {
    // Filter favorites
}
```

**Migration Strategy**:
- Override getPojoProcessor() (from SearcherCoroutine base)
- Preserve ReversedNameComparator usage
- Override onPostExecuteInternal() for buildSections()
- Helper method getPojosWithoutFavorites() copied

**Estimated Time**: 30-45 minutes

---

### 4. PojoWithTagSearcher ⭐⭐ (Abstract Base)
**LOC**: 83 lines  
**Complexity**: Medium  
**Special Features**:
- Abstract base class for TagsSearcher, UntaggedSearcher
- Filter logic in addResults()
- HistoryMode-based sorting
- Optimized tag search (requestRecordsByTag)

**Key Code**:
```java
@Override
protected void doInBackground() {
    // 태그 검색인 경우 최적화
    if (this instanceof TagsSearcher && query != null && !query.equals("<tags>")) {
        dataHandler.requestRecordsByTag(query, this);
    } else {
        dataHandler.requestAllRecords(this);
    }
}

@Override
public boolean addResults(List<? extends Pojo> pojos) {
    // Filter: only PojoWithTags + acceptPojo()
    List<Pojo> filteredPojos = new ArrayList<>();
    for (Pojo pojo : pojos) {
        if (pojo instanceof PojoWithTags && acceptPojo((PojoWithTags) pojo)) {
            filteredPojos.add(pojo);
        }
    }
    
    // Apply history-based relevance
    dataHandler.applyRelevanceFromHistory(filteredPojos, getTaggedResultSortMode());
    
    return super.addResults(filteredPojos);
}

protected abstract boolean acceptPojo(PojoWithTags pojoWithTags);
```

**Migration Strategy**:
- Create PojoWithTagSearcherCoroutine (abstract base)
- acceptPojo() abstract method
- Override addResults() with filtering + sorting logic
- HistoryMode preference handling

**Estimated Time**: 45-60 minutes

---

### 5. TagsSearcher ⭐ (Simple)
**LOC**: 19 lines  
**Complexity**: Very Low  
**Inherits**: PojoWithTagSearcher

**Key Code**:
```java
@Override
protected boolean acceptPojo(PojoWithTags pojoWithTags) {
    return pojoWithTags.getTags() != null && pojoWithTags.getTags().contains(query);
}
```

**Migration Strategy**:
- Extend PojoWithTagSearcherCoroutine
- Override acceptPojo() only

**Estimated Time**: 10 minutes

---

### 6. UntaggedSearcher ⭐ (Simple)
**LOC**: 18 lines  
**Complexity**: Very Low  
**Inherits**: PojoWithTagSearcher

**Key Code**:
```java
@Override
protected boolean acceptPojo(PojoWithTags pojoWithTags) {
    return pojoWithTags.getTags() == null || pojoWithTags.getTags().isEmpty();
}
```

**Migration Strategy**:
- Extend PojoWithTagSearcherCoroutine
- Override acceptPojo() only

**Estimated Time**: 10 minutes

---

## 📋 Implementation Order

### Phase 1: Simple Classes (30 minutes)
1. **NullSearcher** → NullSearcherCoroutine (10 min)
   - Test: Minimalistic mode, press home twice
   
2. **TagsSearcher** → TagsSearcherCoroutine (10 min)
   - Requires: PojoWithTagSearcherCoroutine
   - Test: Tag search menu
   
3. **UntaggedSearcher** → UntaggedSearcherCoroutine (10 min)
   - Requires: PojoWithTagSearcherCoroutine
   - Test: Untagged apps view

### Phase 2: Base Class (45-60 minutes)
4. **PojoWithTagSearcher** → PojoWithTagSearcherCoroutine (60 min)
   - Abstract base for TagsSearcher, UntaggedSearcher
   - Filtering logic + HistoryMode sorting
   - Test: Both TagsSearcher and UntaggedSearcher

### Phase 3: Complex Classes (60-90 minutes)
5. **HistorySearcher** → HistorySearcherCoroutine (45 min)
   - Similar to QuerySearcher
   - DB queries, exclude logic, shortcut handling
   - Test: Empty search (history view)
   
6. **ApplicationsSearcher** → ApplicationsSearcherCoroutine (45 min)
   - Custom PriorityQueue (ReversedNameComparator)
   - buildSections() in onPostExecute
   - Test: App drawer view

---

## 🔧 Technical Approach

### Feature Flag Pattern (from Step 3)
```kotlin
// app/build.gradle
buildConfigField "boolean", "USE_HISTORY_SEARCHER_COROUTINE", "true"
buildConfigField "boolean", "USE_APPLICATIONS_SEARCHER_COROUTINE", "true"
buildConfigField "boolean", "USE_NULL_SEARCHER_COROUTINE", "true"
buildConfigField "boolean", "USE_TAGS_SEARCHER_COROUTINE", "true"
buildConfigField "boolean", "USE_UNTAGGED_SEARCHER_COROUTINE", "true"
```

Or use single flag:
```kotlin
buildConfigField "boolean", "USE_ALL_SEARCHER_COROUTINES", "true"
```

### MainActivity Integration
```java
// HistorySearcher
if (BuildConfig.USE_ALL_SEARCHER_COROUTINES) {
    runTaskCoroutine(new HistorySearcherCoroutine(this, isRefresh));
} else {
    runTask(new HistorySearcher(this, isRefresh));
}

// ApplicationsSearcher
if (BuildConfig.USE_ALL_SEARCHER_COROUTINES) {
    runTaskCoroutine(new ApplicationsSearcherCoroutine(this, isRefresh));
} else {
    runTask(new ApplicationsSearcher(this, isRefresh));
}

// NullSearcher
if (BuildConfig.USE_ALL_SEARCHER_COROUTINES) {
    runTaskCoroutine(new NullSearcherCoroutine(this));
} else {
    runTask(new NullSearcher(this));
}
```

### Memory Management
- WeakReference<MainActivity> (from SearcherCoroutine base)
- Job cancellation in MainActivity.resetTask()
- Static caches with @Volatile for thread-safety

---

## 🧪 Testing Strategy

### Test Cases per Searcher

#### NullSearcher
- [ ] Minimalistic mode: Press home twice (no loader)
- [ ] No results displayed
- [ ] No crashes

#### HistorySearcher
- [ ] Empty search → Shows history
- [ ] Exclude favorites option works
- [ ] Exclude history items option works
- [ ] Disabled items have lower relevance
- [ ] Shortcuts excluded for excluded apps (API 26+)
- [ ] MAX_RESULT_COUNT preference respected

#### ApplicationsSearcher
- [ ] App drawer view (all apps listed)
- [ ] A→Z sorting (reversed for ListView)
- [ ] Fast scroll sections built
- [ ] Favorites excluded if configured
- [ ] Pinned shortcuts included

#### PojoWithTagSearcher / TagsSearcher / UntaggedSearcher
- [ ] Tag search: Only tagged items shown
- [ ] Untagged search: Only untagged items shown
- [ ] HistoryMode-based sorting works
- [ ] Optimized requestRecordsByTag() called (TagsSearcher)
- [ ] Relevance from history applied

### Performance Targets
- **All Searchers**: < 100ms response time
- **NullSearcher**: < 1ms (instant)
- **HistorySearcher**: < 20ms (similar to QuerySearcher)
- **ApplicationsSearcher**: < 50ms (large dataset)
- **Tag Searchers**: < 30ms

### Stability Checks
- [ ] No crashes during testing
- [ ] No memory leaks (WeakReference)
- [ ] Proper cancellation on consecutive searches
- [ ] UI remains responsive

---

## 📦 Deliverables

### Code Files
1. `NullSearcherCoroutine.kt` (~25 lines)
2. `HistorySearcherCoroutine.kt` (~120 lines)
3. `ApplicationsSearcherCoroutine.kt` (~100 lines)
4. `PojoWithTagSearcherCoroutine.kt` (~90 lines, abstract)
5. `TagsSearcherCoroutine.kt` (~20 lines)
6. `UntaggedSearcherCoroutine.kt` (~20 lines)

### Integration
- `MainActivity.java` updates (5-6 locations)
- `app/build.gradle` feature flags
- `SettingsActivity.java` cache clear (if needed)

### Documentation
- `step4-implementation.md` (design decisions)
- `step4-testing-report.md` (test results)
- `step4-summary.md` (completion summary)

---

## 🎯 Success Criteria

- [ ] All 5 Searcher classes migrated to Coroutines
- [ ] 100% functional equivalence with original classes
- [ ] All test cases passing
- [ ] Performance targets met
- [ ] No stability issues (crashes, leaks)
- [ ] Feature flags working
- [ ] Documentation complete
- [ ] Code committed to `step4-remaining-searchers` branch

---

## ⏱️ Estimated Timeline

| Phase | Tasks | Time |
|-------|-------|------|
| Phase 1 | NullSearcher (simple) | 10 min |
| Phase 2 | PojoWithTagSearcher (base) | 60 min |
| Phase 3 | TagsSearcher + UntaggedSearcher | 20 min |
| Phase 4 | HistorySearcher | 45 min |
| Phase 5 | ApplicationsSearcher | 45 min |
| Phase 6 | Integration + Testing | 60 min |
| Phase 7 | Documentation | 30 min |
| **Total** | | **~4.5 hours** |

---

## 🚀 Next Steps

1. Start with **NullSearcher** (simplest, good warm-up)
2. Implement **PojoWithTagSearcher** base class
3. Complete **TagsSearcher** and **UntaggedSearcher** (depends on base)
4. Migrate **HistorySearcher** (similar to QuerySearcher)
5. Migrate **ApplicationsSearcher** (custom PriorityQueue)
6. Integrate all with MainActivity + feature flags
7. Test on emulator (comprehensive test plan)
8. Document results and commit

Let's begin with NullSearcher! 🎯

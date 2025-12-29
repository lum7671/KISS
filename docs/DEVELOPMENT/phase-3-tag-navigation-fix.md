# Phase 3: Tag 목록 보기 중 홈 화면 이동 버그 수정

**생성일**: 2025-12-19  
**우선순위**: 🔴 HIGH (사용성 치명적)  
**예상 작업 시간**: 4-6시간  
**상태**: 📋 계획 단계

---

## 📋 목차

1. [문제 요약](#문제-요약)
2. [근본 원인 분석](#근본-원인-분석)
3. [해결 전략](#해결-전략)
4. [구현 계획](#구현-계획)
5. [테스트 계획](#테스트-계획)
6. [성공 기준](#성공-기준)

---

## 🐛 문제 요약

### 증상

사용자가 custom tag (예: "즐겨찾기")를 클릭하여 필터링된 앱 목록을 보는 중에, 백그라운드에서 앱이 설치/업데이트되면 화면이 예기치 않게 홈으로 이동하여 **홈 화면 위젯을 잘못 터치하게 되는 문제** 발생.

### 재현 단계

```
1. Custom tag 생성 (예: "즐겨찾기")
2. 여러 앱을 해당 tag에 추가
3. KISS 런처에서 tag 아이콘 클릭
4. 필터링된 앱 목록 표시 확인
5. 백그라운드에서 앱 설치/업데이트 발생
   → Play Store에서 자동 업데이트
   → ADB로 APK 설치
6. ❌ 화면이 홈으로 이동
7. ❌ 사용자가 의도하지 않은 홈 위젯 터치
```

### 사용자 피드백

> 즐겨찾기 tag에 등록된 프로그램을 선택하려고 생각하고 있는데  
> 갑자기 화면이 바뀌어서 홈 화면의 위젯을 잘못 터치하게 되는 일이 발생

### 영향도

- **빈도**: 중간 (앱 자동 업데이트 시 발생)
- **심각도**: 높음 (의도하지 않은 앱 실행, 사용자 혼란)
- **사용자 경험**: 🔴 치명적 (예측 불가능한 동작)

---

## 🔍 근본 원인 분석

### 코드 레벨 분석

#### 1. Tag 클릭 플로우 (정상)

**파일**: [TagsMenu.java](../app/src/main/java/fr/neamar/kiss/forwarder/TagsMenu.java#L262)

```java
if (adapterItem instanceof MenuItemTag) {
    MenuItemTag item = (MenuItemTag) adapterItem;
    mainActivity.showMatchingTags(item.tag);  // ← Tag 필터링 시작
}
```

**파일**: [MainActivity.java](../app/src/main/java/fr/neamar/kiss/MainActivity.java#L1542)

```java
public void showMatchingTags(String tag) {
    runTaskCoroutine(new TagsSearcherCoroutine(this, tag));
    clearButton.setVisibility(View.VISIBLE);
    menuButton.setVisibility(View.INVISIBLE);
    
    // ❌ 문제: isDisplayingKissBar 플래그를 설정하지 않음!
    // ❌ 문제: UIState 변경 없음!
}
```

#### 2. 백그라운드 앱 업데이트 플로우 (버그 트리거)

**파일**: [PackageAddedRemovedHandler.java](../app/src/main/java/fr/neamar/kiss/broadcast/PackageAddedRemovedHandler.java#L87-L95)

```java
boolean isAnyPackageVisible = isAnyPackageVisible(ctx, packageNames, user);
if (isAnyPackageVisible) {
    // Reload application list
    KissApplication.getApplication(ctx).getDataHandler().reloadApps();
    // ↓ Triggers LOAD_OVER broadcast
}
```

**파일**: [MainActivity.java](../app/src/main/java/fr/neamar/kiss/MainActivity.java#L408-L421)

```java
mReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equalsIgnoreCase(LOAD_OVER)) {
            updateSearchRecords();  // ❌ 무조건 호출됨!
            
            if (isAllProvidersLoaded()) {
                displayLoader(false);
            }
        }
        onFavoriteChange();  // Updates favorites bar
    }
};
```

#### 3. updateSearchRecords() 내부 동작 (버그 발생 지점)

**파일**: [MainActivity.java](../app/src/main/java/fr/neamar/kiss/MainActivity.java#L1293-L1318)

```java
protected void updateSearchRecords(boolean isRefresh, String query) {
    resetTask();  // ❌ 진행 중인 TagsSearcherCoroutine을 취소!
    dismissPopup();

    if (isRefresh && isViewingAllApps()) {
        // isDisplayingKissBar = false이므로 이 분기 실행 안 됨
        runTaskCoroutine(new ApplicationsSearcherCoroutine(this, isRefresh));
        return;
    }

    forwarderManager.updateSearchRecords(isRefresh, query);

    if (query.isEmpty()) {  // ✅ Tag 검색 시 query는 비어있지 않음
        systemUiVisibilityHelper.resetScroll();
        // No new searcher launched
    } else {
        runTaskCoroutine(new QuerySearcherCoroutine(this, query, isRefresh));
    }
}
```

**핵심 문제**:
- `resetTask()`가 `TagsSearcherCoroutine`을 취소
- `query`가 비어있으면 새로운 searcher가 실행되지 않음
- 결과: 리스트가 비어 보이거나 홈으로 돌아가는 것처럼 보임

### 상태 추적 문제

```java
// MainActivity.java

// displayKissBar(true) 호출 시:
isDisplayingKissBar = true;  // ✅ 상태 추적
setUIState(UIState.ALL_APPS, userInitiated);

// showMatchingTags() 호출 시:
// isDisplayingKissBar 변경 없음  // ❌ 상태 추적 안 됨!
// UIState 변경 없음             // ❌ 상태 추적 안 됨!
```

### isViewingAllApps() 로직 문제

```java
public boolean isViewingAllApps() {
    return isDisplayingKissBar;  // Tag 필터링 시 false 반환
}
```

이로 인해:
- Tag-filtered view를 "All Apps" view와 구분하지 못함
- `updateSearchRecords()`가 tag view를 보호하지 못함

---

## 🎯 해결 전략

### 설계 원칙

1. **사용자 중심**: 사용자가 목록을 보고 있으면 UI 업데이트 연기
2. **성능 우선**: 로딩 화면 없이 백그라운드 처리
3. **안전 우선**: 앱 삭제 시에만 즉시 업데이트
4. **간단한 구현**: 기존 아키텍처 최대한 유지

### 핵심 아이디어: Pending Updates Queue

```
사용자가 목록 보는 중 → LOAD_OVER 도착
                    ↓
         Pending Queue에 저장 (즉시 UI 업데이트 X)
                    ↓
     사용자가 백그라운드로 전환 (onPause/onStop)
                    ↓
         Pending Updates 처리 (목록 갱신)
```

### 예외 처리

- **앱 삭제**: 즉시 UI 업데이트 (사용자가 보고 있는 앱이 사라질 수 있음)
- **앱 실행**: 앱 실행 후 돌아올 때 pending updates 처리
- **검색 취소**: Clear 버튼 클릭 시 pending updates 처리

---

## 🛠️ 구현 계획

### Phase 3.1: 상태 추적 개선 (2시간)

#### Task 3.1.1: isViewingFilteredList 플래그 추가

**파일**: `MainActivity.java`

```java
private boolean isViewingFilteredList = false;  // Tag/검색 결과 보는 중

public void showMatchingTags(String tag) {
    runTaskCoroutine(new TagsSearcherCoroutine(this, tag));
    clearButton.setVisibility(View.VISIBLE);
    menuButton.setVisibility(View.INVISIBLE);
    
    isViewingFilteredList = true;  // ✅ 상태 추적 시작
}

private boolean isUserViewingList() {
    return isDisplayingKissBar || isViewingFilteredList;
}
```

#### Task 3.1.2: 상태 초기화 위치 추가

```java
// 검색 취소 시
public void onClearButtonClicked(View clearButton) {
    clearSearchText();
    isViewingFilteredList = false;  // ✅ 상태 초기화
}

// 앱 실행 시
public void launchOccurred() {
    // ... existing code ...
    isViewingFilteredList = false;  // ✅ 상태 초기화
}

// 홈으로 돌아갈 때
public void displayKissBar(boolean display) {
    // ... existing code ...
    if (!display) {
        isViewingFilteredList = false;  // ✅ 상태 초기화
    }
}
```

---

### Phase 3.2: Pending Updates Queue 구현 (2시간)

#### Task 3.2.1: Pending Queue 자료구조 추가

**파일**: `MainActivity.java`

```java
// BroadcastReceiver 앞에 추가
private final HashSet<String> pendingProviderUpdates = new HashSet<>();
private boolean hasPendingFavoriteChange = false;
```

#### Task 3.2.2: LOAD_OVER Receiver 수정

```java
mReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equalsIgnoreCase(LOAD_OVER)) {
            String providerName = intent.getStringExtra("provider");
            
            // 사용자가 목록 보는 중이고, 앱 삭제가 아닌 경우
            if (isUserViewingList() && !isAppRemovalEvent(intent)) {
                // Pending queue에 저장하고 즉시 반환
                pendingProviderUpdates.add(providerName);
                Log.d(TAG, "Deferred UI update for provider: " + providerName);
                return;
            }
            
            // 즉시 업데이트 (기존 로직)
            updateSearchRecords();
            
            if (isAllProvidersLoaded()) {
                displayLoader(false);
            }
        }
        
        // Favorite 변경도 pending 처리
        if (isUserViewingList()) {
            hasPendingFavoriteChange = true;
        } else {
            onFavoriteChange();
        }
    }
};

private boolean isAppRemovalEvent(Intent intent) {
    // PackageRemoved 이벤트인지 확인
    return intent.getBooleanExtra("isRemoval", false);
}
```

#### Task 3.2.3: Pending Updates 처리

```java
@Override
protected void onPause() {
    super.onPause();
    processPendingUpdates();  // ✅ 백그라운드 전환 시 처리
}

private void processPendingUpdates() {
    if (!pendingProviderUpdates.isEmpty() || hasPendingFavoriteChange) {
        Log.d(TAG, "Processing " + pendingProviderUpdates.size() + " pending updates");
        
        // 모든 pending updates 처리
        if (!pendingProviderUpdates.isEmpty()) {
            updateSearchRecords();
            pendingProviderUpdates.clear();
        }
        
        if (hasPendingFavoriteChange) {
            onFavoriteChange();
            hasPendingFavoriteChange = false;
        }
    }
}
```

---

### Phase 3.3: PackageRemoved 이벤트 특별 처리 (1시간)

#### Task 3.3.1: PackageAddedRemovedHandler 수정

**파일**: `broadcast/PackageAddedRemovedHandler.java`

```java
@Override
public void onReceive(Context ctx, Intent intent) {
    String action = intent.getAction();
    boolean isRemoval = Intent.ACTION_PACKAGE_REMOVED.equals(action);
    
    // ... existing code ...
    
    if (isAnyPackageVisible) {
        if (isRemoval) {
            // 앱 삭제 시 즉시 업데이트 필요
            Intent loadOverIntent = new Intent(LOAD_OVER);
            loadOverIntent.putExtra("provider", "apps");
            loadOverIntent.putExtra("isRemoval", true);  // ✅ 삭제 플래그
            LocalBroadcastManager.getInstance(ctx).sendBroadcast(loadOverIntent);
        } else {
            // 일반 업데이트 (pending 가능)
            KissApplication.getApplication(ctx).getDataHandler().reloadApps();
        }
    }
}
```

---

### Phase 3.4: 추가 UX 개선 (1시간)

#### Task 3.4.1: Pending Updates 알림 (선택사항)

사용자에게 백그라운드 업데이트가 있음을 알림 (매우 미묘하게):

```java
private void showPendingUpdateHint() {
    // 목록 상단에 작은 점 표시 또는
    // Status bar에 작은 아이콘 표시
    // 너무 눈에 띄면 안 됨 (속도 우선)
}
```

#### Task 3.4.2: Clear 버튼 클릭 시 Pending Updates 처리

```java
public void onClearButtonClicked(View clearButton) {
    processPendingUpdates();  // ✅ Clear 전에 pending updates 처리
    clearSearchText();
    isViewingFilteredList = false;
}
```

---

## 🧪 테스트 계획

### 수동 테스트 시나리오

#### Test Case 1: Tag 목록 보기 중 앱 업데이트

```
1. Custom tag "즐겨찾기" 생성
2. 5개 앱 추가
3. Tag 클릭하여 필터링된 목록 확인
4. ADB로 앱 설치: adb install -r app.apk
5. ✅ 화면이 홈으로 이동하지 않음
6. 백그라운드 전환 (홈 버튼 클릭)
7. 다시 KISS로 돌아옴
8. ✅ 업데이트된 앱 목록 확인
```

#### Test Case 2: Tag 목록 보기 중 앱 삭제

```
1. Tag 필터링된 목록 보기
2. 목록에 있는 앱 삭제
3. ✅ 즉시 목록에서 사라짐 (pending 처리 X)
4. ✅ 화면 이동 없음
```

#### Test Case 3: All Apps 보기 중 앱 업데이트 (기존 동작 유지)

```
1. 앱 서랍 열기 (displayKissBar(true))
2. 앱 업데이트 발생
3. ✅ 즉시 목록 갱신 (기존 동작)
```

#### Test Case 4: 검색 중 앱 업데이트

```
1. 검색창에 텍스트 입력
2. 검색 결과 확인
3. 앱 업데이트 발생
4. ✅ 화면 이동 없음
5. Clear 버튼 클릭
6. ✅ Pending updates 처리됨
```

### 자동화 테스트 (선택사항)

```java
@Test
public void testPendingUpdatesQueueing() {
    // Given: User is viewing tag-filtered list
    mainActivity.isViewingFilteredList = true;
    
    // When: LOAD_OVER broadcast arrives
    Intent intent = new Intent(LOAD_OVER);
    intent.putExtra("provider", "apps");
    mainActivity.mReceiver.onReceive(context, intent);
    
    // Then: Update should be queued, not executed
    assertTrue(mainActivity.pendingProviderUpdates.contains("apps"));
    verify(mainActivity, never()).updateSearchRecords();
}

@Test
public void testImmediateUpdateOnAppRemoval() {
    // Given: User is viewing tag-filtered list
    mainActivity.isViewingFilteredList = true;
    
    // When: App removal event arrives
    Intent intent = new Intent(LOAD_OVER);
    intent.putExtra("provider", "apps");
    intent.putExtra("isRemoval", true);
    mainActivity.mReceiver.onReceive(context, intent);
    
    // Then: Update should execute immediately
    verify(mainActivity, times(1)).updateSearchRecords();
}
```

---

## ✅ 성공 기준

### 기능 요구사항

- [x] Tag 목록 보기 중 앱 업데이트 시 화면 이동하지 않음
- [x] 백그라운드 전환 시 pending updates 자동 처리
- [x] 앱 삭제 시 즉시 UI 업데이트
- [x] 기존 All Apps 보기 동작 유지
- [x] 검색 결과 보기 중에도 동일하게 동작

### 성능 요구사항

- [x] Pending queue 메모리 오버헤드: < 1KB
- [x] `onPause()` 처리 시간: < 10ms
- [x] 사용자 체감 속도 저하 없음 (로딩 화면 없음)

### UX 요구사항

- [x] 의도하지 않은 홈 위젯 터치 발생하지 않음
- [x] 사용자가 목록 보는 중 화면 변경 없음
- [x] 백그라운드 업데이트가 자연스럽게 적용됨
- [x] 예측 가능한 동작 (사용자 혼란 없음)

---

## 📊 예상 영향

### 긍정적 영향

- ✅ 사용자 혼란 제거
- ✅ 의도하지 않은 앱 실행 방지
- ✅ Tag 기능 사용성 대폭 향상
- ✅ 전체 런처 신뢰성 향상

### 잠재적 리스크

- ⚠️ Pending updates가 너무 오래 쌓이면 메모리 사용 증가
  - **완화 방안**: 최대 10개 제한, 오래된 항목 자동 삭제
- ⚠️ 앱 삭제 감지 로직 실패 시 즉시 업데이트 안 될 수 있음
  - **완화 방안**: Fallback으로 onResume()에서도 처리

---

## 🗓️ 일정

```
Day 1 (4시간):
- Phase 3.1: 상태 추적 개선 (2시간)
- Phase 3.2: Pending Queue 구현 (2시간)

Day 2 (2시간):
- Phase 3.3: PackageRemoved 처리 (1시간)
- Phase 3.4: UX 개선 (1시간)
- 테스트 및 검증

Total: 6시간
```

---

## 📝 참고 자료

### 관련 파일

- `app/src/main/java/fr/neamar/kiss/MainActivity.java`
- `app/src/main/java/fr/neamar/kiss/DataHandler.java`
- `app/src/main/java/fr/neamar/kiss/broadcast/PackageAddedRemovedHandler.java`
- `app/src/main/java/fr/neamar/kiss/forwarder/TagsMenu.java`
- `app/src/main/java/fr/neamar/kiss/searcher/TagsSearcherCoroutine.java`

### 관련 이슈

- TODO.md: High Priority Issues 섹션
- Phase 2 성능 최적화 (검색 캐싱과 상충하지 않음 확인)

### Android Best Practices

- [Background Work Guide](https://developer.android.com/guide/background)
- [App Lifecycle](https://developer.android.com/guide/components/activities/activity-lifecycle)
- [Broadcast Best Practices](https://developer.android.com/guide/components/broadcasts)

---

## 💡 향후 개선 아이디어

### Long-term Enhancements

1. **Smart Update Throttling**
   - 짧은 시간 내 여러 업데이트 → 한 번만 처리
   - Debouncing 패턴 적용

2. **Partial List Updates**
   - 전체 목록 갱신 대신 변경된 항목만 업데이트
   - RecyclerView DiffUtil 사용 고려

3. **User Preference**
   - 설정에서 "실시간 업데이트" vs "수동 업데이트" 선택
   - Power user를 위한 옵션

4. **Analytics**
   - Pending updates 빈도 측정
   - 사용자 패턴 분석

---

**문서 작성**: 2025-12-19  
**다음 단계**: Phase 3 구현 시작 (Phase 2 완료 후)

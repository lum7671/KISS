# Phase 1 구현 가이드

**작성일**: 2025년 8월 21일  
**목적**: Phase 1 구현을 위한 단계별 코드 변경 가이드

## 🎯 구현 개요

이 문서는 화면 refresh 최적화의 Phase 1을 구현하기 위한 구체적인 코드 변경사항을 제시합니다.

## 📂 파일별 변경사항

### 1. MainActivity.java - 핵심 변경사항

#### 1.1 새로운 enum 및 변수 추가

**위치**: 클래스 상단, 기존 멤버 변수 근처

```java
/**
 * 현재 사용자가 보고 있는 화면 상태를 나타내는 enum
 */
public enum UIState {
    INITIAL,           // 초기 상태 (히스토리 또는 빈 화면)
    FAVORITES_VISIBLE, // 즐겨찾기 바가 보이는 상태
    ALL_APPS,          // 전체 앱 목록 보기
    SEARCH_RESULTS,    // 검색 결과 표시
    HISTORY,           // 히스토리 표시
    MINIMALISTIC      // 미니멀리스틱 모드
}

/**
 * 사용자 의도 enum
 */
private enum UserIntent {
    QUICK_RETURN,  // 빠른 복귀 (앱 전환 후 바로 돌아옴)
    HOME_RETURN,   // 일반적인 홈 복귀
    NEW_TASK,      // 새로운 작업 시작
    UNKNOWN        // 의도 불명
}

// 상태 추적 변수들
private UIState currentUIState = UIState.INITIAL;
private UIState previousUIState = UIState.INITIAL;
private boolean isUserInitiatedStateChange = false;
private boolean hasPendingBackgroundUpdate = false;
private long lastPauseTime = 0;
private long lastLaunchTime = 0;
```

#### 1.2 상태 관리 메서드 추가

**위치**: MainActivity 클래스 내부, 기존 메서드들과 함께

```java
/**
 * UI 상태를 변경하고 기록
 */
private void setUIState(UIState newState, boolean isUserInitiated) {
    if (currentUIState != newState) {
        previousUIState = currentUIState;
        currentUIState = newState;
        isUserInitiatedStateChange = isUserInitiated;
        
        if (BuildConfig.DEBUG) {
            Log.d(TAG, String.format("UI State changed: %s -> %s (user: %b)", 
                previousUIState, currentUIState, isUserInitiated));
        }
    }
}

/**
 * 현재 UI 상태 확인
 */
private UIState getCurrentUIState() {
    return currentUIState;
}

/**
 * 사용자가 의도적으로 상태를 변경했는지 확인
 */
private boolean isUserInitiatedChange() {
    return isUserInitiatedStateChange;
}

/**
 * 현재 상태에서 UI 업데이트가 안전한지 확인
 */
private boolean isSafeToUpdateUI() {
    return currentUIState == UIState.INITIAL || 
           currentUIState == UIState.MINIMALISTIC ||
           isUserInitiatedChange();
}

/**
 * 사용자 의도 분석
 */
private UserIntent analyzeUserIntent(Intent intent) {
    if (intent.hasCategory(Intent.CATEGORY_HOME)) {
        long timeSinceLastLaunch = System.currentTimeMillis() - lastLaunchTime;
        if (timeSinceLastLaunch < 1000) {
            return UserIntent.QUICK_RETURN;
        } else {
            return UserIntent.HOME_RETURN;
        }
    }
    return UserIntent.UNKNOWN;
}

/**
 * Resume 시 즐겨찾기 변경 처리
 */
private void handleFavoriteChangeOnResume() {
    if (isSafeToUpdateUI() || currentUIState == UIState.FAVORITES_VISIBLE) {
        onFavoriteChange();
    } else {
        hasPendingBackgroundUpdate = true;
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Deferring favorite change update - user is viewing " + currentUIState);
        }
    }
}

/**
 * Resume 시 데이터 업데이트 처리
 */
private void handleDataUpdateOnResume() {
    DataHandler dataHandler = KissApplication.getApplication(this).getDataHandler();
    if (dataHandler.shouldUpdateOnResume()) {
        if (isSafeToUpdateUI()) {
            updateSearchRecords();
        } else {
            hasPendingBackgroundUpdate = true;
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Deferring data update - user is actively using " + currentUIState);
            }
        }
    }
}

/**
 * Resume 시 앱 목록 처리
 */
private void handleAppListOnResume() {
    if (isViewingAllApps()) {
        if (!isUserInitiatedChange() && shouldCloseAppListOnResume()) {
            displayKissBar(false, true, false);
        } else {
            setUIState(UIState.ALL_APPS, false);
        }
    }
}

/**
 * Resume 시 앱 목록을 닫아야 하는지 판단
 */
private boolean shouldCloseAppListOnResume() {
    return System.currentTimeMillis() - lastPauseTime > 30000; // 30초 이상
}

/**
 * NewIntent 시 앱 목록 처리
 */
private void handleAppListOnNewIntent(UserIntent userIntent) {
    if (isViewingAllApps()) {
        switch (userIntent) {
            case QUICK_RETURN:
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "Quick return detected - maintaining app list");
                }
                break;
            case HOME_RETURN:
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "Home return detected - closing app list");
                }
                displayKissBar(false, true, true);
                break;
            case UNKNOWN:
            default:
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "Unknown intent - maintaining current state");
                }
                break;
        }
    }
}

/**
 * 백그라운드에서 지연된 업데이트 처리
 */
private void processPendingBackgroundUpdates() {
    if (hasPendingBackgroundUpdate && isSafeToUpdateUI()) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Processing pending background updates");
        }
        
        switch (currentUIState) {
            case INITIAL:
            case HISTORY:
                updateSearchRecords();
                break;
            case FAVORITES_VISIBLE:
                onFavoriteChange();
                break;
            case ALL_APPS:
                // 필요시 앱 목록 업데이트 로직 추가
                break;
        }
        
        hasPendingBackgroundUpdate = false;
    }
}

/**
 * 사용자가 화면을 변경할 때 호출
 */
public void onUserStateChange(UIState newState) {
    setUIState(newState, true);
    processPendingBackgroundUpdates();
}
```

#### 1.3 기존 메서드 수정

**1.3.1 onResume() 메서드 수정**

기존의 onResume() 메서드를 다음과 같이 수정:

```java
@SuppressLint("CommitPrefEdits")
protected void onResume() {
    Trace.beginSection("MainActivity.onResume");
    ProfileManager.getInstance().logActivityLifecycle("MainActivity", "onResume");
    Log.d(TAG, "onResume()");

    // 화면 재구성 최적화: 필요한 경우에만 재구성 수행
    if (shouldRecreateActivity()) {
        super.onResume();
        Log.i(TAG, "Restarting app after setting changes");
        prefs.edit().putBoolean("require-layout-update", false).apply();
        this.recreate();
        Trace.endSection();
        return;
    }

    dismissPopup();

    if (KissApplication.getApplication(this).getDataHandler().allProvidersHaveLoaded) {
        displayLoader(false);
        // 스마트 즐겨찾기 업데이트
        handleFavoriteChangeOnResume();
    }

    // 스마트 데이터 업데이트
    handleDataUpdateOnResume();
    
    displayClearOnInput();

    // 스마트 앱 목록 처리
    handleAppListOnResume();

    forwarderManager.onResume();

    // 기존 shared text 처리 로직 유지
    Intent receivedIntent = getIntent();
    String receivedIntentAction = receivedIntent.getAction();
    String receivedIntentType = receivedIntent.getType();
    if (Intent.ACTION_SEND.equals(receivedIntentAction) && "text/plain".equals(receivedIntentType)) {
        hideKeyboard();
        String sharedText = receivedIntent.getStringExtra(Intent.EXTRA_TEXT);
        if (sharedText != null && !TextUtils.isEmpty(sharedText.trim())) {
            searchEditText.setText(sharedText);
        } else {
            Toast.makeText(this, R.string.shared_text_empty, Toast.LENGTH_SHORT).show();
        }
    }

    super.onResume();
    Trace.endSection();
}
```

**1.3.2 onPause() 메서드 수정**

기존 onPause()에 시간 기록 추가:

```java
@Override
protected void onPause() {
    super.onPause();
    lastPauseTime = System.currentTimeMillis();
    ProfileManager.getInstance().logActivityLifecycle("MainActivity", "onPause");
    forwarderManager.onPause();
}
```

**1.3.3 onNewIntent() 메서드 수정**

기존의 onNewIntent() 메서드를 다음과 같이 수정:

```java
@Override
protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    setIntent(intent);
    lastLaunchTime = System.currentTimeMillis();

    if (BuildConfig.DEBUG) {
        Log.d(TAG, "onNewIntent called - analyzing user intent");
    }

    // 사용자 의도 분석
    UserIntent userIntent = analyzeUserIntent(intent);
    
    // 검색어가 있다면 클리어 (기존 동작 유지)
    if (!TextUtils.isEmpty(searchEditText.getText())) {
        Log.i(TAG, "Clearing search field");
        clearSearchText();
        setUIState(UIState.INITIAL, true);
    }

    // 사용자 의도에 따른 앱 목록 처리
    handleAppListOnNewIntent(userIntent);

    // Close the backButton context menu
    closeContextMenu();
}
```

**1.3.4 displayKissBar() 메서드 오버로드 추가**

기존 메서드는 유지하고 새로운 오버로드 추가:

```java
public void displayKissBar(boolean display) {
    displayKissBar(display, true, true);
}

protected void displayKissBar(boolean display, boolean clearSearchText, boolean userInitiated) {
    // 상태 업데이트
    if (display) {
        setUIState(UIState.ALL_APPS, userInitiated);
    } else {
        setUIState(UIState.INITIAL, userInitiated);
    }
    
    // 기존 로직 호출
    displayKissBar(display, clearSearchText);
}
```

### 2. DataHandler.java - 소프트 업데이트 지원

#### 2.1 새로운 메서드 추가

**위치**: DataHandler 클래스 내부

```java
/**
 * UI 업데이트 없이 데이터만 백그라운드에서 업데이트할지 결정
 */
public boolean shouldUpdateDataOnly() {
    // 현재는 기존 로직과 동일하지만, 향후 확장 가능
    return shouldUpdateOnResume();
}

/**
 * 백그라운드에서 데이터만 업데이트 (UI 업데이트 없음)
 */
public void updateDataInBackground() {
    // TODO: Phase 2에서 구현
    // 현재는 플래그만 설정
    if (BuildConfig.DEBUG) {
        Log.d("DataHandler", "Background data update requested");
    }
}
```

### 3. 상태 감지를 위한 기존 메서드 수정

#### 3.1 showHistory() 메서드 수정

```java
public void showHistory() {
    setUIState(UIState.HISTORY, true);
    runTask(new HistorySearcher(this, false));
    clearButton.setVisibility(View.VISIBLE);
    menuButton.setVisibility(View.INVISIBLE);
}
```

#### 3.2 주요 UI 변경 지점에 상태 업데이트 추가

검색 시작 시:

```java
// updateSearchRecords() 메서드 시작 부분에 추가
if (!query.isEmpty()) {
    setUIState(UIState.SEARCH_RESULTS, true);
}
```

## 📋 구현 체크리스트

### Phase 1 Week 1 구현 목록

#### Day 1-2: 기본 구조 구현

- [ ] UIState enum 정의
- [ ] UserIntent enum 정의
- [ ] 상태 추적 변수 추가
- [ ] 기본 상태 관리 메서드 구현

#### Day 3-4: 핵심 로직 구현

- [ ] onResume() 스마트 처리 구현
- [ ] handleFavoriteChangeOnResume() 구현
- [ ] handleDataUpdateOnResume() 구현
- [ ] handleAppListOnResume() 구현

#### Day 5: Intent 처리 개선

- [ ] onNewIntent() 조건부 처리 구현
- [ ] analyzeUserIntent() 구현
- [ ] handleAppListOnNewIntent() 구현

#### Weekend: 테스트 및 통합

- [ ] 각 시나리오별 테스트
- [ ] 로깅 확인 및 디버깅
- [ ] 성능 영향 측정

## 🧪 테스트 방법

### 1. 개발 중 테스트

각 메서드 구현 후 다음 로그를 확인:

```bash
adb logcat | grep -E "(MainActivity|UIState|user|Deferring)"
```

### 2. 시나리오 테스트

1. **앱 목록 유지 테스트**
   - 앱 목록 열기
   - 다른 앱으로 전환
   - KISS로 복귀
   - 예상: 앱 목록이 유지됨

2. **백그라운드 업데이트 테스트**
   - 즐겨찾기나 검색 결과 보기
   - 백그라운드에서 데이터 변경 발생시키기
   - 예상: 현재 화면 유지, 로그에 "Deferring" 메시지

### 3. 성능 테스트

```bash
# 메모리 사용량 모니터링
adb shell dumpsys meminfo kr.lum7671.kiss

# CPU 사용량 모니터링  
adb shell top | grep kiss
```

## ⚠️ 주의사항

1. **기존 동작 유지**: 기본 사용자 경험은 변경하지 않음
2. **로깅 최소화**: 프로덕션에서는 DEBUG 로깅만 사용
3. **메모리 관리**: 상태 변수들이 메모리 누수를 일으키지 않도록 주의
4. **예외 처리**: enum 값 검증 및 null 체크 필수

## 📈 성공 지표

1. **사용자 경험**
   - 앱 목록/즐겨찾기 보기 중 refresh 시 화면 유지율 > 90%
   - 홈 버튼 재클릭 시 의도하지 않은 초기화 < 10%

2. **성능**
   - 메모리 사용량 증가 < 5%
   - UI 응답성 지연 < 50ms

3. **안정성**
   - 크래시 발생 없음
   - 기존 기능 정상 동작 100%

---

**다음 단계**: 구현 시작 및 Phase 2 설계 문서 작성

# Phase 1: 화면 상태 추적 시스템 상세 설계

**작성일**: 2025년 8월 21일  
**목적**: 사용자 작업 중단 방지를 위한 긴급 수정사항 구현

## 🎯 Phase 1 목표

1. 현재 화면 상태 추적 시스템 구현
2. onResume() 스마트 처리 로직 개발
3. onNewIntent() 조건부 처리 구현

## 📋 1단계: 화면 상태 추적 시스템

### 1.1 화면 상태 Enum 정의

**파일**: `MainActivity.java`에 추가

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
```

### 1.2 상태 추적 변수 추가

**파일**: `MainActivity.java`의 멤버 변수에 추가

```java
public class MainActivity extends Activity {
    // 기존 변수들...
    
    // 현재 UI 상태 추적
    private UIState currentUIState = UIState.INITIAL;
    private UIState previousUIState = UIState.INITIAL;
    
    // 사용자 의도적 상태 변경 플래그
    private boolean isUserInitiatedStateChange = false;
    
    // 백그라운드 업데이트 대기 플래그
    private boolean hasPendingBackgroundUpdate = false;
}
```

### 1.3 상태 변경 관리 메서드

**파일**: `MainActivity.java`에 추가

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
    // 사용자가 활발히 상호작용 중인 상태에서는 업데이트 금지
    return currentUIState == UIState.INITIAL || 
           currentUIState == UIState.MINIMALISTIC ||
           isUserInitiatedChange();
}
```

## 📋 2단계: onResume() 스마트 처리

### 2.1 기존 문제점 분석

```java
// 현재 문제가 되는 코드 (MainActivity.java 라인 668-694)
protected void onResume() {
    // ...
    if (isViewingAllApps()) {
        displayKissBar(false);  // ← 무조건 닫음
    }
    // ...
    if (shouldUpdateOnResume()) {
        updateSearchRecords();  // ← 무조건 업데이트
    }
}
```

### 2.2 개선된 onResume() 로직

**파일**: `MainActivity.java`의 onResume() 메서드 수정

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
        // 즐겨찾기 변경은 백그라운드에서 처리하되 현재 상태 고려
        handleFavoriteChangeOnResume();
    }

    // 스마트 업데이트: 현재 UI 상태를 고려한 업데이트
    handleDataUpdateOnResume();
    
    displayClearOnInput();

    // 스마트 앱 목록 처리: 사용자 의도 고려
    handleAppListOnResume();

    forwarderManager.onResume();

    // Pasting shared text handling
    handleSharedTextIntent();

    super.onResume();
    Trace.endSection();
}

/**
 * Resume 시 즐겨찾기 변경 처리
 */
private void handleFavoriteChangeOnResume() {
    if (isSafeToUpdateUI() || currentUIState == UIState.FAVORITES_VISIBLE) {
        onFavoriteChange();
    } else {
        // 백그라운드에서 데이터만 업데이트, UI는 나중에
        hasPendingBackgroundUpdate = true;
        // TODO: 백그라운드 스레드에서 데이터 업데이트
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
            // 현재 상태를 유지하면서 백그라운드 업데이트만 수행
            hasPendingBackgroundUpdate = true;
            // TODO: 백그라운드에서 데이터만 업데이트
            Log.d(TAG, "Deferring UI update - user is actively using " + currentUIState);
        }
    }
}

/**
 * Resume 시 앱 목록 처리
 */
private void handleAppListOnResume() {
    if (isViewingAllApps()) {
        // 사용자가 의도적으로 앱 목록을 열었다면 유지
        if (!isUserInitiatedChange() && shouldCloseAppListOnResume()) {
            displayKissBar(false);
            setUIState(UIState.INITIAL, false);
        } else {
            // 현재 상태 유지
            setUIState(UIState.ALL_APPS, false);
        }
    }
}

/**
 * Resume 시 앱 목록을 닫아야 하는지 판단
 */
private boolean shouldCloseAppListOnResume() {
    // 예: 오랜 시간 백그라운드에 있었거나, 메모리 부족 등의 상황
    return System.currentTimeMillis() - lastPauseTime > 30000; // 30초 이상
}
```

## 📋 3단계: onNewIntent() 조건부 처리

### 3.1 기존 문제점

```java
// 현재 문제가 되는 코드 (MainActivity.java 라인 765-768)
protected void onNewIntent(Intent intent) {
    // ...
    if (isViewingAllApps()) {
        displayKissBar(false);  // ← 무조건 닫음
    }
}
```

### 3.2 개선된 onNewIntent() 로직

**파일**: `MainActivity.java`의 onNewIntent() 메서드 수정

```java
@Override
protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    setIntent(intent);

    Log.d(TAG, "onNewIntent called - analyzing user intent");

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

/**
 * 사용자 의도 분석
 */
private UserIntent analyzeUserIntent(Intent intent) {
    // Intent의 flags, action, 시간 등을 분석하여 사용자 의도 파악
    if (intent.hasCategory(Intent.CATEGORY_HOME)) {
        long timeSinceLastLaunch = System.currentTimeMillis() - lastLaunchTime;
        if (timeSinceLastLaunch < 1000) {
            return UserIntent.QUICK_RETURN; // 빠른 복귀 (앱 전환 등)
        } else {
            return UserIntent.HOME_RETURN;  // 일반적인 홈 복귀
        }
    }
    return UserIntent.UNKNOWN;
}

/**
 * NewIntent 시 앱 목록 처리
 */
private void handleAppListOnNewIntent(UserIntent userIntent) {
    if (isViewingAllApps()) {
        switch (userIntent) {
            case QUICK_RETURN:
                // 빠른 복귀인 경우 현재 상태 유지
                Log.d(TAG, "Quick return detected - maintaining app list");
                break;
            case HOME_RETURN:
                // 의도적인 홈 복귀인 경우에만 닫기
                Log.d(TAG, "Home return detected - closing app list");
                displayKissBar(false);
                setUIState(UIState.INITIAL, true);
                break;
            case UNKNOWN:
            default:
                // 기본 동작: 현재 상태 유지 (변경)
                Log.d(TAG, "Unknown intent - maintaining current state");
                break;
        }
    }
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
```

## 📋 4단계: 추가 개선사항

### 4.1 displayKissBar() 메서드 개선

**파일**: `MainActivity.java`의 displayKissBar() 메서드 수정

```java
public void displayKissBar(boolean display) {
    displayKissBar(display, true, true); // 기본값: clearSearch=true, userInitiated=true
}

protected void displayKissBar(boolean display, boolean clearSearchText, boolean userInitiated) {
    // 상태 업데이트
    if (display) {
        setUIState(UIState.ALL_APPS, userInitiated);
    } else {
        setUIState(UIState.INITIAL, userInitiated);
    }
    
    // 기존 로직 유지
    dismissPopup();
    // ... 기존 구현 ...
    
    forwarderManager.onDisplayKissBar(display);
}
```

### 4.2 백그라운드 업데이트 처리

**파일**: `MainActivity.java`에 추가

```java
/**
 * 백그라운드에서 지연된 업데이트 처리
 */
private void processPendingBackgroundUpdates() {
    if (hasPendingBackgroundUpdate && isSafeToUpdateUI()) {
        Log.d(TAG, "Processing pending background updates");
        
        // 현재 상태에 맞는 업데이트만 수행
        switch (currentUIState) {
            case INITIAL:
            case HISTORY:
                updateSearchRecords();
                break;
            case FAVORITES_VISIBLE:
                onFavoriteChange();
                break;
            case ALL_APPS:
                // 앱 목록 업데이트
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

## 📋 5단계: 테스트 시나리오

### 5.1 긴급 수정 검증 시나리오

1. **앱 목록 보기 중 다른 앱으로 전환 후 복귀**
   - 기대 결과: 앱 목록이 유지되어야 함

2. **즐겨찾기 보기 중 refresh 발생**
   - 기대 결과: 즐겨찾기 화면이 유지되어야 함

3. **검색 결과 보기 중 백그라운드 업데이트**
   - 기대 결과: 검색 결과가 유지되어야 함

4. **홈 버튼 더블 클릭**
   - 기대 결과: 의도적인 초기화만 수행

### 5.2 성능 검증

1. **메모리 사용량 모니터링**
2. **UI 응답성 측정**
3. **배터리 사용량 비교**

## 📋 6단계: 구현 순서

### Week 1 세부 계획

**Day 1-2**: 화면 상태 enum 및 추적 시스템 구현
**Day 3-4**: onResume() 스마트 처리 로직 구현
**Day 5**: onNewIntent() 조건부 처리 구현
**Weekend**: 통합 테스트 및 버그 수정

## ⚠️ 주의사항 및 리스크

1. **기존 동작 호환성**: 기본 사용자 경험은 변경하지 않음
2. **메모리 누수 방지**: 상태 추적 변수들의 생명주기 관리
3. **과도한 로깅**: DEBUG 모드에서만 상태 로깅 활성화
4. **예외 처리**: 상태 불일치 상황에 대한 복구 로직 필요

---

**다음 단계**: Phase 1 구현 시작 및 Phase 2 설계 문서 작성

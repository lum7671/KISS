# KISS 런처 - 앱 목록 표시 및 스크롤 분석

## 개요

KISS 런처의 앱 목록 표시와 스크롤 시스템은 여러 커스텀 UI 컴포넌트와 어댑터를 통해 구현되어 있습니다. 이 문서는 앱 목록의 렌더링, 스크롤 동작, 성능 최적화 메커니즘을 상세히 분석합니다.

## 핵심 컴포넌트

### 1. UI 계층 구조

```text
MainActivity (Activity)
└── main.xml (Layout)
    └── resultLayout (FrameLayout)
        ├── AnimatedListView (Custom ListView)
        │   └── RecordAdapter (BaseAdapter)
        │       └── Result Items (AppResult, ContactsResult, etc.)
        └── BottomPullEffectView (Edge effect)
```

### 2. 주요 클래스

#### 2.1 AnimatedListView

**위치**: `app/src/main/java/fr/neamar/kiss/ui/AnimatedListView.java`

**상속 관계**: `AnimatedListView` → `BlockableListView` → `ListView`

**핵심 기능**:

- **아이템 애니메이션**: 리스트 아이템 변경 시 부드러운 애니메이션 제공
- **위치 추적**: `ItemInfo` 해시맵으로 각 아이템의 위치를 저장
- **ViewTreeObserver 활용**: `onPreDraw()` 리스너로 레이아웃 후 애니메이션 실행

```java
// 애니메이션 준비 - 현재 아이템 위치 저장
public void prepareChangeAnim() {
    mItemMap.clear();
    int firstVisiblePosition = this.getFirstVisiblePosition();
    int nCount = Math.min(this.getChildCount(), 
                         getAdapter().getCount() - firstVisiblePosition);
    for (int i = 0; i < nCount; i += 1) {
        View child = this.getChildAt(i);
        child.clearAnimation();
        int position = firstVisiblePosition + i;
        long itemId = getAdapter().getItemId(position);
        mItemMap.put(itemId, new ItemInfo(i, child.getTop()));
    }
}

// 애니메이션 실행 - 이전 위치와 새 위치 차이만큼 이동
public void animateChange() {
    observer.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
        @Override
        public boolean onPreDraw() {
            // 레이아웃 후 위치 비교하여 delta 계산
            int delta = topBeforeLayout - topAfterLayout;
            if (delta != 0) {
                child.setTranslationY(delta);
                child.animate()
                    .setDuration(MOVE_DURATION) // 100ms
                    .translationY(0);
            }
            return false;
        }
    });
}
```

**특징**:

- **100ms 애니메이션**: 빠른 전환으로 반응성 유지
- **신규 아이템 스케일 애니메이션**: `scaleY(0 → 1)` 효과
- **첫 번째 아이템 슬라이드**: 위에서 아래로 들어오는 효과

#### 2.2 BlockableListView

**위치**: `app/src/main/java/fr/neamar/kiss/ui/BlockableListView.java`

**핵심 기능**:

- **터치 이벤트 차단**: 키보드 숨김 애니메이션 중 터치 입력 방지
- **상태 플래그**: `touchEventsBlocked` 불린 변수로 제어

```java
@Override
public boolean onTouchEvent(MotionEvent ev) {
    return this.touchEventsBlocked || super.onTouchEvent(ev);
}

public void blockTouchEvents() {
    this.touchEventsBlocked = true;
}

public void unblockTouchEvents() {
    this.touchEventsBlocked = false;
}
```

**사용 사례**: `KeyboardScrollHider`가 키보드를 숨기는 동안 리스트 터치 차단

#### 2.3 RecordAdapter

**위치**: `app/src/main/java/fr/neamar/kiss/adapter/RecordAdapter.java`

**상속**: `BaseAdapter implements SectionIndexer`

**핵심 기능**:

1. **다형성 뷰 타입 관리**:

```java
@Override
public int getViewTypeCount() {
    return 6; // 6가지 결과 타입 지원
}

@Override
public int getItemViewType(int position) {
    if (results.get(position) instanceof AppResult) return 0;
    else if (results.get(position) instanceof SearchResult) return 1;
    else if (results.get(position) instanceof ContactsResult) return 2;
    else if (results.get(position) instanceof SettingsResult) return 3;
    else if (results.get(position) instanceof PhoneResult) return 4;
    else if (results.get(position) instanceof ShortcutsResult) return 5;
    else return -1;
}
```

2. **Fast Scroll 지원**:

```java
// SectionIndexer 구현으로 빠른 스크롤 네비게이션 제공
private final HashMap<String, Integer> alphaIndexer = new HashMap<>();
private String[] sections = new String[0];

public void buildSections() {
    alphaIndexer.clear();
    for (int x = 0; x < size; x++) {
        String s = results.get(x).getSection(); // "A", "B", "C"...
        if (!alphaIndexer.containsKey(s)) {
            alphaIndexer.put(s, x);
        }
    }
    // 섹션 배열 정렬하여 저장
}

@Override
public int getPositionForSection(int sectionIndex) {
    // 섹션 인덱스 → 리스트 위치 변환
    return alphaIndexer.get(sections[sectionIndex]);
}
```

3. **FuzzyScore 통합**:

```java
public void updateResults(@NonNull Context context, 
                          List<Result<?>> results, 
                          boolean isRefresh, 
                          String query) {
    this.results.clear();
    this.results.addAll(results);
    
    // 검색어 정규화 및 fuzzy 점수 생성
    StringNormalizer.Result queryNormalized = 
        StringNormalizer.normalizeWithResult(query, false);
    fuzzyScore = FuzzyFactory.createFuzzyScore(
        context, queryNormalized.codePoints, true);
    
    notifyDataSetChanged();
    
    if (isRefresh) {
        // 새로고침 시 스크롤 위치 유지
        parent.temporarilyDisableTranscriptMode();
    }
}
```

4. **View 재사용**:

```java
@Override
@NonNull
public View getView(int position, View convertView, @NonNull ViewGroup parent) {
    // Result 객체가 뷰 생성 및 재사용 처리
    return results.get(position).display(
        parent.getContext(), convertView, parent, fuzzyScore);
}
```

#### 2.4 AppResult

**위치**: `app/src/main/java/fr/neamar/kiss/result/AppResult.java`

**핵심 기능**:

1. **뷰 생성 및 바인딩**:

```java
@Override
public View display(final Context context, View view, 
                    @NonNull ViewGroup parent, FuzzyScore fuzzyScore) {
    if (view == null) {
        view = inflateFromId(context, R.layout.item_app, parent);
    }

    TextView appName = view.findViewById(R.id.item_app_name);
    
    // Fuzzy 하이라이팅 적용
    displayHighlighted(pojo.normalizedName, pojo.getName(), 
                      fuzzyScore, appName, context);

    TextView tagsView = view.findViewById(R.id.item_app_tag);
    if (pojo.getTags().isEmpty()) {
        tagsView.setVisibility(View.GONE);
    } else if (displayHighlighted(pojo.getNormalizedTags(), 
                                   pojo.getTags(), fuzzyScore, 
                                   tagsView, context) 
               || isTagsVisible(context)) {
        tagsView.setVisibility(View.VISIBLE);
    } else {
        tagsView.setVisibility(View.GONE);
    }

    final ImageView appIcon = view.findViewById(R.id.item_app_icon);
    // 아이콘 로딩은 비동기로 처리 (Coil 사용)
    // ...
    
    return view;
}
```

2. **아이콘 로딩 최적화**:

- **뷰포트 체크**: 화면에 보이는 아이템만 아이콘 로드
- **Coil 이미지 로더**: 메모리 효율적인 이미지 캐싱
- **WeakReference 사용**: 메모리 누수 방지

```java
private boolean isViewInViewport(ImageView view) {
    if (view == null) return true;
    
    ViewGroup parent = (ViewGroup) view.getParent();
    while (parent != null) {
        if (parent instanceof ListView || 
            parent instanceof RecyclerView ||
            parent instanceof ScrollView) {
            
            int[] viewLocation = new int[2];
            view.getLocationOnScreen(viewLocation);
            
            int[] parentLocation = new int[2];
            parent.getLocationOnScreen(parentLocation);
            
            // 뷰가 스크롤 컨테이너 내에 보이는지 확인
            int viewTop = viewLocation[1];
            // ... 위치 계산 로직
        }
        parent = (ViewGroup) parent.getParent();
    }
    return true;
}
```

### 3. 스크롤 동작 관리

#### 3.1 KeyboardScrollHider

**위치**: `app/src/main/java/fr/neamar/kiss/ui/KeyboardScrollHider.java`

**핵심 기능**: 스크롤 중 키보드 자동 숨김 및 리스트 크기 조정

**동작 원리**:

1. **터치 이벤트 감지**:

```java
@Override
public boolean onTouch(View v, MotionEvent event) {
    switch (event.getActionMasked()) {
        case MotionEvent.ACTION_DOWN:
            // 터치 시작 - 초기 상태 저장
            this.offsetYStart = event.getY();
            this.offsetYCurrent = event.getY();
            this.offsetYDiff = 0;
            this.initialWindowPadding = this.getWindowPadding();
            this.listHeightInitial = this.list.getHeight();
            
            // 리스트 높이 고정
            this.setListLayoutHeight(this.listHeightInitial);
            break;
            
        case MotionEvent.ACTION_MOVE:
            // 드래그 중 - 리스트 높이 업데이트
            this.offsetYCurrent = event.getY();
            this.updateListViewHeight();
            break;
            
        case MotionEvent.ACTION_UP:
        case MotionEvent.ACTION_CANCEL:
            // 터치 종료 - 키보드 숨김 처리
            if (wasScrollingDown) {
                this.handler.hideKeyboard();
            }
            break;
    }
}
```

2. **리스트 높이 동적 조정**:

```java
private void updateListViewHeight() {
    // 윈도우 패딩 체크로 키보드 상태 확인
    if (this.getWindowPadding() >= this.initialWindowPadding 
        || this.resizeDone) {
        return; // 키보드 이미 숨겨졌거나 처리 완료
    }

    // 터치 이벤트 차단
    this.list.blockTouchEvents();
    this.list.setVerticalScrollBarEnabled(false);

    int heightContainer = this.listParent.getHeight();
    int offsetYDiff = (int) (this.offsetYCurrent - this.offsetYStart);
    
    // 임계값 이상 드래그 시 당겨지는 효과 적용
    if (offsetYDiff < (this.offsetYDiff - THRESHOLD)) {
        double pullFeedback = Math.sqrt(
            (double) (this.offsetYDiff - offsetYDiff) / THRESHOLD);
        offsetYDiff = this.offsetYDiff - (int) (THRESHOLD * pullFeedback);
    }

    // 새로운 리스트 높이 계산 및 적용
    int listLayoutHeight = ViewGroup.LayoutParams.MATCH_PARENT;
    if ((this.listHeightInitial + offsetYDiff) < heightContainer) {
        listLayoutHeight = this.listHeightInitial + offsetYDiff;
    }
    this.setListLayoutHeight(listLayoutHeight);
    
    // Pull effect 표시
    float distance = ((float) (heightContainer - listLayoutHeight)) 
                     / heightContainer;
    float displacement = 1 - this.lastMotionEvent.getX() 
                         / getWindowWidth();
    this.pullEffect.setPull(distance, displacement, false);
}
```

3. **복구 처리**:

```java
protected void handleResizeDone() {
    if (this.resizeDone) return;

    // 터치 이벤트 차단 해제
    this.list.unblockTouchEvents();

    // Edge pull effect 해제
    this.pullEffect.releasePull();

    // 리스트 높이를 부모에 맞춤
    this.list.setVerticalScrollBarEnabled(this.scrollBarEnabled);
    this.setListLayoutHeight(ViewGroup.LayoutParams.MATCH_PARENT);

    this.resizeDone = true;
}
```

**특징**:

- **부드러운 전환**: 스크롤바 임시 비활성화
- **당겨지는 피드백**: 제곱근 함수로 자연스러운 저항감
- **임계값 24dp**: 민감도 조절

#### 3.2 MainActivity 스크롤 리스너

**위치**: `app/src/main/java/fr/neamar/kiss/MainActivity.java` (Line 493-527)

**성능 추적**:

```java
this.list.setOnScrollListener(new AbsListView.OnScrollListener() {
    private long scrollStartTime = 0;
    private int lastFirstVisibleItem = 0;
    private boolean isScrolling = false;
    
    @Override
    public void onScrollStateChanged(AbsListView view, int scrollState) {
        if (scrollState == SCROLL_STATE_TOUCH_SCROLL) {
            // 스크롤 시작
            scrollStartTime = SystemClock.elapsedRealtime();
            isScrolling = true;
            ActionPerformanceTracker.getInstance().startAction("SCROLL");
        } else if (scrollState == SCROLL_STATE_IDLE && isScrolling) {
            // 스크롤 종료
            long scrollDuration = SystemClock.elapsedRealtime() 
                                  - scrollStartTime;
            ActionPerformanceTracker.getInstance()
                .trackUIInteraction("SCROLL", "LIST_VIEW", scrollDuration);
            ActionPerformanceTracker.getInstance().endAction("SCROLL");
            isScrolling = false;
        }
    }
    
    @Override
    public void onScroll(AbsListView view, int firstVisibleItem, 
                        int visibleItemCount, int totalItemCount) {
        if (isScrolling) {
            int scrollDirection = firstVisibleItem - lastFirstVisibleItem;
            String direction = scrollDirection > 0 ? "DOWN" 
                             : (scrollDirection < 0 ? "UP" : "NONE");
            ActionPerformanceTracker.getInstance()
                .trackScrollAction(direction, visibleItemCount, 
                                  Math.abs(scrollDirection));
            lastFirstVisibleItem = firstVisibleItem;
        }
    }
});
```

**추적 데이터**:

- 스크롤 지속 시간
- 스크롤 방향 (UP/DOWN)
- 보이는 아이템 수
- 스크롤 거리

### 4. 레이아웃 구조

#### 4.1 main.xml

**위치**: `app/src/main/res/layout/main.xml`

```xml
<FrameLayout
    android:id="@+id/resultLayout"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="?attr/listBackgroundColor"
    android:elevation="2dp">

    <fr.neamar.kiss.ui.AnimatedListView
        android:id="@android:id/list"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:layout_gravity="top|center_horizontal"
        android:cacheColorHint="@android:color/transparent"
        android:divider="?attr/dividerDrawable"
        android:dividerHeight="1dp"
        android:stackFromBottom="true"
        android:transcriptMode="alwaysScroll"
        tools:listitem="@layout/item_app" />

    <fr.neamar.kiss.ui.BottomPullEffectView
        android:id="@+id/listEdgeEffect"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:layout_gravity="center_horizontal|bottom" />

</FrameLayout>
```

**주요 속성**:

- **stackFromBottom="true"**: 아래에서 위로 쌓임 (검색 결과 표시에 적합)
- **transcriptMode="alwaysScroll"**: 새 아이템 추가 시 자동 스크롤
- **elevation="2dp"**: 머티리얼 디자인 그림자 효과

#### 4.2 item_app.xml

**위치**: `app/src/main/res/layout/item_app.xml`

```xml
<LinearLayout
    style="@style/ResultItem"
    android:orientation="horizontal">

    <!-- 아이콘 영역 -->
    <FrameLayout style="@style/ResultItemIcon">
        <ImageView
            android:id="@+id/item_app_icon"
            android:layout_width="match_parent"
            android:layout_height="match_parent" />
        
        <!-- 알림 도트 -->
        <ImageView
            android:id="@+id/item_notification_dot"
            android:layout_width="?attr/resultNotificationDotSize"
            android:layout_height="?attr/resultNotificationDotSize"
            android:layout_gravity="top|end"
            android:visibility="gone" />
    </FrameLayout>

    <!-- 텍스트 영역 -->
    <LinearLayout
        android:orientation="vertical"
        android:gravity="center_vertical">
        
        <!-- 앱 이름 -->
        <TextView
            android:id="@+id/item_app_name"
            android:ellipsize="end"
            android:maxLines="1"
            android:shadowColor="?attr/resultShadowColor"
            android:shadowDx="1"
            android:shadowDy="2"
            android:shadowRadius="?attr/textShadowRadius"
            android:textColor="?attr/resultColor"
            android:textSize="?attr/resultTitleSize" />

        <!-- 태그 -->
        <TextView
            android:id="@+id/item_app_tag"
            android:layout_marginTop="-4dp"
            android:ellipsize="end"
            android:textColor="?android:attr/textColorSecondary"
            android:textSize="?attr/resultSubtitleSize" />

    </LinearLayout>
</LinearLayout>
```

**레이아웃 특징**:

- **FrameLayout**: 아이콘 위에 알림 도트 오버레이
- **LinearLayout**: 수평 방향 - 아이콘 + 텍스트
- **그림자 효과**: 가독성 향상을 위한 텍스트 그림자
- **테마 속성**: `?attr/` 참조로 동적 스타일링

## 성능 최적화

### 1. 뷰 재사용 (View Recycling)

```java
@Override
public View getView(int position, View convertView, @NonNull ViewGroup parent) {
    // convertView가 null이면 새로 생성, 아니면 재사용
    return results.get(position).display(
        parent.getContext(), convertView, parent, fuzzyScore);
}
```

**이점**:

- **메모리 절약**: 화면에 보이는 뷰만 유지
- **인플레이션 최소화**: XML 파싱 횟수 감소
- **GC 압력 감소**: 객체 생성/파괴 빈도 감소

### 2. 뷰 타입 최적화

```java
@Override
public int getViewTypeCount() {
    return 6; // 6가지 타입
}

@Override
public int getItemViewType(int position) {
    // 각 Result 타입별로 다른 뷰 타입 반환
}
```

**이점**:

- **타입별 재사용**: 같은 타입끼리만 재사용
- **레이아웃 불일치 방지**: 잘못된 뷰 재사용 방지

### 3. 아이콘 로딩 최적화

```java
// Result.java - 뷰포트 체크
private boolean isViewInViewport(ImageView view) {
    // 화면에 보이는지 확인
}

// 보이는 아이템만 아이콘 로드
if (isViewInViewport(appIcon)) {
    // Coil로 아이콘 비동기 로드
    loadIconAsync(appIcon, pojo);
}
```

**Coil 이미지 로더 장점**:

- **Kotlin First**: 코루틴 네이티브 지원
- **메모리 캐시**: LRU 캐시로 중복 로드 방지
- **자동 취소**: 뷰가 재사용되면 이전 요청 취소
- **경량**: Glide 대비 APK 크기 감소

### 4. 애니메이션 최적화

```java
// AnimatedListView - 100ms 짧은 애니메이션
private static final int MOVE_DURATION = 100;

// 하드웨어 가속 활용
child.animate()
    .setDuration(MOVE_DURATION)
    .translationY(0);  // GPU 가속 가능한 속성
```

**최적화 포인트**:

- **짧은 애니메이션**: 100ms로 반응성 유지
- **GPU 가속**: `translationY`, `scaleY` 속성 사용
- **애니메이션 취소**: `child.clearAnimation()` 호출

### 5. Fast Scroll

```java
// MainActivity.java
if (isViewingAllApps()) {
    list.setFastScrollEnabled(true);  // 전체 앱 목록에서만 활성화
    adapter.buildSections();          // 섹션 인덱스 구축
}
```

**Fast Scroll 사용 조건**:

- **정렬된 리스트**: 알파벳 순 정렬 필수
- **섹션 인덱싱**: `SectionIndexer` 구현
- **전체 앱 목록**: 검색 중엔 비활성화

### 6. Transcript Mode 제어

```java
// RecordAdapter.java
public void updateResults(..., boolean isRefresh, ...) {
    this.results.clear();
    this.results.addAll(results);
    notifyDataSetChanged();
    
    if (isRefresh) {
        // 새로고침 시 스크롤 위치 유지
        parent.temporarilyDisableTranscriptMode();
    }
}
```

**목적**:

- **자동 스크롤 방지**: 사용자가 중간을 보고 있을 때
- **UX 개선**: 의도하지 않은 스크롤 방지

## 데이터 흐름

### 검색 → 결과 표시 흐름

```text
1. SearchEditText.afterTextChanged()
   ↓
2. MainActivity.updateSearchRecords(text)
   ↓
3. Searcher.executeAsync() (Coroutines)
   ↓ [백그라운드 스레드]
4. DataHandler.requestResults(query)
   ↓
5. Provider.getResults(query)
   ↓ [메인 스레드]
6. MainActivity.updateRecordList(results)
   ↓
7. adapter.prepareChangeAnim()      // 현재 위치 저장
   ↓
8. adapter.updateResults(results)   // 데이터 갱신
   ↓
9. adapter.notifyDataSetChanged()
   ↓
10. adapter.animateChange()         // 애니메이션 실행
    ↓
11. AnimatedListView.onPreDraw()
    ↓
12. Result.display() for each item  // 각 아이템 렌더링
```

### 스크롤 이벤트 흐름

```text
1. User Touch
   ↓
2. KeyboardScrollHider.onTouch()
   ↓ [ACTION_MOVE]
3. updateListViewHeight()
   ↓
4. BlockableListView.blockTouchEvents()
   ↓
5. setListLayoutHeight(newHeight)
   ↓
6. BottomPullEffectView.setPull()
   ↓ [ACTION_UP && wasScrollingDown]
7. KeyboardHandler.hideKeyboard()
   ↓
8. handleResizeDone()
   ↓
9. BlockableListView.unblockTouchEvents()
   ↓
10. MainActivity.OnScrollListener
    ↓
11. ActionPerformanceTracker.trackScrollAction()
```

## 주요 설정

### XML 속성

```xml
<!-- AnimatedListView -->
android:stackFromBottom="true"     <!-- 아래에서 위로 쌓기 -->
android:transcriptMode="alwaysScroll"  <!-- 자동 스크롤 -->
android:divider="?attr/dividerDrawable"  <!-- 구분선 -->
android:dividerHeight="1dp"
android:cacheColorHint="@android:color/transparent"  <!-- 스크롤 최적화 -->
```

### 코드 설정

```java
// MainActivity.onCreate()
list.setFastScrollEnabled(true);           // Fast scroll
list.setVerticalScrollBarEnabled(true);    // 스크롤바
list.setOnScrollListener(...);             // 스크롤 이벤트
list.setOnItemClickListener(...);          // 클릭 이벤트
list.setOnItemLongClickListener(...);      // 롱 클릭 이벤트
```

## 알려진 이슈 및 해결책

### 이슈 #890: IndexOutOfBoundsException

**증상**: Android가 존재하지 않는 아이템 요청 (예: 22개 리스트에서 24번째 아이템)

**해결**:

```java
@Override
public long getItemId(int position) {
    // 범위 체크로 안전성 확보
    return position < results.size() 
        ? results.get(position).getUniqueId() 
        : -1;
}
```

### 이슈 #1005: Fast Scroll 마지막 섹션

**증상**: "A"로 시작하는 앱이 화면을 넘으면 섹션 인덱스 오류

**해결**:

```java
@Override
public int getSectionForPosition(int position) {
    for (int i = 0; i < sections.length; i++) {
        if (alphaIndexer.get(sections[i]) > position) {
            return i - 1;
        }
    }
    // 마지막 두 번째 섹션 반환
    return sections.length - 2;
}
```

### 메모리 누수 방지

**WeakReference 사용**:

```java
// Result.java
WeakReference<ImageView> iconRef = new WeakReference<>(appIcon);

// 비동기 작업 완료 후
ImageView icon = iconRef.get();
if (icon != null) {
    icon.setImageDrawable(loadedIcon);
}
```

## 테스트 포인트

### 1. 기본 동작 테스트

- [ ] 검색 시 결과 즉시 표시
- [ ] 스크롤 부드러움 (60fps 목표)
- [ ] 아이템 클릭/롱 클릭 정상 동작
- [ ] Fast scroll 정확도

### 2. 애니메이션 테스트

- [ ] 새 결과 추가 시 애니메이션
- [ ] 기존 아이템 이동 애니메이션
- [ ] 애니메이션 중 터치 가능 여부

### 3. 키보드 상호작용

- [ ] 스크롤 다운 시 키보드 숨김
- [ ] 키보드 숨김 중 리스트 크기 조정
- [ ] Pull effect 표시

### 4. 성능 테스트

- [ ] 1000개 이상 앱 목록 스크롤
- [ ] 빠른 타이핑 시 반응성
- [ ] 메모리 사용량 (LeakCanary)
- [ ] 배터리 소모 (Profile build)

### 5. 엣지 케이스

- [ ] 빈 리스트 처리
- [ ] 단일 아이템
- [ ] 화면 회전
- [ ] 멀티윈도우 모드

## 🚨 실제 성능 병목 지점 분석 (에뮬레이터 스크롤 끊김 원인)

### 발견된 주요 문제점

#### 1. **메인 스레드에서 동기적 아이콘 로딩** ⚠️ 심각

**위치**: `Result.java` → `setAsyncDrawable()` → `SetImageCoroutine.kt`

**문제**:

```kotlin
// SetImageCoroutine.kt - loadDrawable()
private fun loadDrawable(...): Drawable? {
    var drawable = result.getDrawable(imageView.context)
    
    // 🔴 문제: null이면 재시도를 **백그라운드 스레드에서** 동기적으로 수행
    var retryCount = 0
    while (drawable == null && retryCount < 3) {
        retryCount++
        Thread.sleep((100 * retryCount).toLong()) // 최대 600ms 대기!
        drawable = result.getDrawable(imageView.context)
    }
}
```

**영향**:

- 백그라운드 스레드를 블록하지만, 아이콘 로딩 자체가 무거움
- 여러 아이템을 스크롤하면 동시에 여러 개의 재시도 발생
- 에뮬레이터에서는 디스크 I/O가 느려 더 심각

**해결책**:

```kotlin
// 재시도 로직 제거하고 캐시 미스 시 즉시 반환
private fun loadDrawable(...): Drawable? {
    return try {
        result.getDrawable(imageView.context)
    } catch (e: Exception) {
        null
    }
}
```

#### 2. **뷰포트 체크 후 지연 재시도** ⚠️ 중간

**위치**: `Result.java` → `setAsyncDrawable()`

**문제**:

```java
if (checkViewport && !isViewInViewport(view)) {
    view.setImageResource(resId);
    view.setTag(null);
    // 🔴 문제: view.post()로 다시 시도 - 무한 재귀 가능성
    view.post(() -> {
        if (isViewInViewport(view)) {
            setAsyncDrawable(view, resId, false); // 재귀 호출
        }
    });
    return;
}
```

**영향**:

- 빠르게 스크롤하면 `view.post()` 큐에 작업이 쌓임
- 메인 스레드 큐 포화 → 프레임 드랍
- 뷰가 재사용되면서 불필요한 로딩 중복 발생

**해결책**:

```java
// 뷰포트 체크를 더 관대하게 하거나, 지연 재시도 제거
if (checkViewport && !isViewInViewport(view)) {
    view.setImageResource(resId);
    view.setTag(null);
    return; // 재시도 하지 않음 - 스크롤 멈추면 자연스럽게 로드됨
}
```

#### 3. **AnimatedListView의 모든 아이템 애니메이션** ⚠️ 중간

**위치**: `AnimatedListView.java` → `animateChange()`

**문제**:

```java
public void animateChange() {
    // ViewTreeObserver.onPreDraw() 내부
    int nCount = Math.min(listView.getChildCount(), 
                         getAdapter().getCount() - firstVisiblePosition);
    
    // 🔴 문제: 보이는 모든 아이템을 순회하며 애니메이션 설정
    for (int i = 0; i < nCount; i += 1) {
        // ... 애니메이션 계산 및 적용
        child.animate()
            .setDuration(MOVE_DURATION)
            .translationY(0);
    }
}
```

**영향**:

- 검색 타이핑할 때마다 `animateChange()` 호출
- 10개 아이템 보이면 10개 애니메이션 동시 시작
- 에뮬레이터에서 GPU 가속이 약하면 프레임 드랍

**해결책**:

```java
// 1. 애니메이션 비활성화 옵션 추가
private boolean animationsEnabled = true;

public void setAnimationsEnabled(boolean enabled) {
    this.animationsEnabled = enabled;
}

// 2. 타이핑 중에는 애니메이션 비활성화
searchEditText.addTextChangedListener(new TextWatcher() {
    public void beforeTextChanged(...) {
        list.setAnimationsEnabled(false); // 타이핑 시작
    }
    
    public void afterTextChanged(...) {
        // 300ms 후 애니메이션 재활성화
        handler.postDelayed(() -> list.setAnimationsEnabled(true), 300);
    }
});
```

#### 4. **SharedPreferences 동기 접근 (알림 도트)** ⚠️ 낮음

**위치**: `AppResult.java` → `display()`

**문제**:

```java
public View display(...) {
    // 🔴 문제: 모든 아이템 렌더링마다 SharedPreferences 읽기
    SharedPreferences notificationPrefs = context.getSharedPreferences(
        NotificationListener.NOTIFICATION_PREFERENCES_NAME, 
        Context.MODE_PRIVATE);
    ImageView notificationView = view.findViewById(R.id.item_notification_dot);
    notificationView.setVisibility(
        notificationPrefs.contains(packageKey) ? View.VISIBLE : View.GONE);
}
```

**영향**:

- `getSharedPreferences()`는 첫 호출 시 파일 I/O
- 캐싱되지만 `contains()` 호출은 Map lookup
- 10개 아이템 × 빠른 스크롤 = 많은 호출

**해결책**:

```java
// AppResult에 알림 상태 캐싱
private static final Map<String, Boolean> notificationCache = 
    new ConcurrentHashMap<>();

public View display(...) {
    // 캐시에서 먼저 확인
    Boolean hasNotification = notificationCache.get(packageKey);
    if (hasNotification == null) {
        hasNotification = notificationPrefs.contains(packageKey);
        notificationCache.put(packageKey, hasNotification);
    }
    notificationView.setVisibility(
        hasNotification ? View.VISIBLE : View.GONE);
}
```

#### 5. **매 프레임마다 FuzzyScore 계산** ⚠️ 낮음

**위치**: `RecordAdapter.java` → `getView()` → `AppResult.display()`

**문제**:

```java
@Override
public View getView(int position, View convertView, ViewGroup parent) {
    // 🔴 문제: 뷰가 재사용될 때마다 FuzzyScore 하이라이팅 재계산
    return results.get(position).display(
        parent.getContext(), convertView, parent, fuzzyScore);
}

// AppResult.java
displayHighlighted(pojo.normalizedName, pojo.getName(), 
                   fuzzyScore, appName, context);
```

**영향**:

- 스크롤할 때 화면 밖으로 나간 뷰가 재사용되며 다시 계산
- `displayHighlighted()` 내부에서 문자열 처리 및 SpannableString 생성
- CPU 사용량 증가

**해결책**:

```java
// AppResult에 하이라이팅 결과 캐싱
private SpannableString cachedHighlightedName = null;
private FuzzyScore lastFuzzyScore = null;

public View display(...) {
    if (cachedHighlightedName == null || 
        !fuzzyScore.equals(lastFuzzyScore)) {
        cachedHighlightedName = createHighlightedText(...);
        lastFuzzyScore = fuzzyScore;
    }
    appName.setText(cachedHighlightedName);
}
```

### 우선순위별 개선 계획

#### 🔥 즉시 적용 (High Impact, Low Effort)

##### 1. 아이콘 로딩 재시도 로직 제거

```kotlin
// SetImageCoroutine.kt 수정
private fun loadDrawable(...): Drawable? {
    return try {
        result.getDrawable(imageView.context) // 1회만 시도
    } catch (e: Exception) {
        null
    }
}
```

##### 2. 뷰포트 체크 후 재시도 제거

```java
// Result.java 수정
if (checkViewport && !isViewInViewport(view)) {
    view.setImageResource(resId);
    view.setTag(null);
    return; // view.post() 제거
}
```

##### 3. 타이핑 중 애니메이션 비활성화

```java
// MainActivity.java - TextWatcher
private Handler animHandler = new Handler();
private Runnable enableAnimRunnable = () -> list.setAnimationsEnabled(true);

beforeTextChanged(...) {
    list.setAnimationsEnabled(false);
    animHandler.removeCallbacks(enableAnimRunnable);
}

afterTextChanged(...) {
    animHandler.postDelayed(enableAnimRunnable, 300);
}
```

#### ⚡ 단기 개선 (Medium Impact, Medium Effort)

##### 4. 알림 도트 상태 캐싱

```java
// AppResult.java
private static final Map<String, Boolean> sNotificationCache = 
    new ConcurrentHashMap<>();
    
static void updateNotificationCache(String packageKey, boolean hasNotification) {
    sNotificationCache.put(packageKey, hasNotification);
}

static void clearNotificationCache() {
    sNotificationCache.clear();
}

public View display(...) {
    Boolean cached = sNotificationCache.get(packageKey);
    if (cached == null) {
        cached = notificationPrefs.contains(packageKey);
        sNotificationCache.put(packageKey, cached);
    }
    notificationView.setVisibility(cached ? View.VISIBLE : View.GONE);
}
```

##### 5. FuzzyScore 결과 캐싱

```java
// Result.java 베이스 클래스에 추가
protected transient SpannableString cachedHighlight = null;
protected transient String lastQuery = null;

protected boolean displayHighlighted(..., String query) {
    if (cachedHighlight != null && query.equals(lastQuery)) {
        textView.setText(cachedHighlight);
        return true;
    }
    
    // ... 기존 로직
    cachedHighlight = result;
    lastQuery = query;
}
```

#### 🎯 장기 개선 (High Impact, High Effort)

##### 6. 아이콘 로딩 파이프라인 최적화

- LRU 메모리 캐시 크기 증가 (현재 기본값 확인 필요)
- 디스크 캐시 추가 (Coil 설정)
- 프리페칭: 다음 3개 아이템 미리 로드

```kotlin
// IconsHandler에 Coil 설정 추가
val imageLoader = ImageLoader.Builder(context)
    .memoryCache {
        MemoryCache.Builder(context)
            .maxSizePercent(0.25) // 메모리의 25% 사용
            .build()
    }
    .diskCache {
        DiskCache.Builder()
            .directory(context.cacheDir.resolve("image_cache"))
            .maxSizeBytes(50 * 1024 * 1024) // 50MB
            .build()
    }
    .build()
```

##### 7. 스크롤 리스너 최적화

```java
// MainActivity - 스크롤 추적 디바운싱
private static final int SCROLL_TRACKING_DEBOUNCE = 100; // ms
private Handler scrollHandler = new Handler();
private Runnable scrollTrackingRunnable = null;

@Override
public void onScroll(...) {
    if (scrollTrackingRunnable != null) {
        scrollHandler.removeCallbacks(scrollTrackingRunnable);
    }
    
    scrollTrackingRunnable = () -> {
        ActionPerformanceTracker.getInstance().trackScrollAction(...);
    };
    
    scrollHandler.postDelayed(scrollTrackingRunnable, 
                             SCROLL_TRACKING_DEBOUNCE);
}
```

### 에뮬레이터 전용 최적화

에뮬레이터 환경 감지 및 성능 모드 적용:

```java
// MainActivity.onCreate()
if (isEmulator()) {
    // 에뮬레이터에서는 애니메이션 완전 비활성화
    list.setAnimationsEnabled(false);
    
    // 아이콘 해상도 낮추기
    iconSize = ICON_SIZE_SMALL;
    
    // 성능 추적 비활성화
    ActionPerformanceTracker.getInstance().setEnabled(false);
}

private boolean isEmulator() {
    return Build.FINGERPRINT.contains("generic")
        || Build.FINGERPRINT.contains("unknown")
        || Build.MODEL.contains("google_sdk")
        || Build.MODEL.contains("Emulator")
        || Build.MODEL.contains("Android SDK built for x86")
        || Build.MANUFACTURER.contains("Genymotion")
        || (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"));
}
```

### 측정 가능한 성능 지표

개선 전후 비교를 위한 지표:

```java
// ProfileManager에 추가
public class ScrollPerformanceMetrics {
    private long frameCount = 0;
    private long droppedFrames = 0;
    private List<Long> frameTimes = new ArrayList<>();
    
    public void onFrame(long frameTimeNanos) {
        frameCount++;
        long frameTimeMs = frameTimeNanos / 1_000_000;
        
        if (frameTimeMs > 16) { // 60fps = 16ms/frame
            droppedFrames++;
        }
        
        frameTimes.add(frameTimeMs);
    }
    
    public String getReport() {
        double avgFrameTime = frameTimes.stream()
            .mapToLong(Long::longValue)
            .average()
            .orElse(0.0);
        
        double fps = 1000.0 / avgFrameTime;
        double dropRate = (double) droppedFrames / frameCount * 100;
        
        return String.format(
            "FPS: %.1f, Dropped: %.1f%%, Avg: %.1fms",
            fps, dropRate, avgFrameTime
        );
    }
}
```

### 테스트 시나리오

개선 효과 확인을 위한 테스트:

1. **빠른 스크롤 테스트**
   - 전체 앱 목록 (100+ 앱)
   - 상단에서 하단까지 2초 안에 스크롤
   - 측정: FPS, 프레임 드랍률

2. **빠른 타이핑 테스트**
   - "google chrome" 빠르게 타이핑
   - 각 키 입력마다 결과 업데이트
   - 측정: 입력 지연, 프레임 드랍

3. **메모리 부하 테스트**
   - 500+ 앱 설치 상태
   - 10분간 무작위 스크롤 및 검색
   - 측정: 메모리 사용량, GC 빈도

4. **에뮬레이터 스트레스 테스트**
   - RAM 512MB, CPU 1코어 제한
   - 위 3가지 테스트 반복
   - 측정: 평균 FPS, 최악 프레임 시간

## 개선 제안

### 1. RecyclerView 마이그레이션

**현재**: `ListView` + `BaseAdapter`
**제안**: `RecyclerView` + `ListAdapter` + `DiffUtil`

**이점**:

- **자동 애니메이션**: DiffUtil로 변경 감지 및 애니메이션
- **더 나은 성능**: ViewHolder 패턴 강제
- **유연성**: ItemDecoration, LayoutManager 커스터마이징

**마이그레이션 계획**:

```kotlin
class AppListAdapter : ListAdapter<Result, ResultViewHolder>(ResultDiffCallback()) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ResultViewHolder {
        // 6가지 ViewHolder 타입 생성
    }
    
    override fun onBindViewHolder(holder: ResultViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}

class ResultDiffCallback : DiffUtil.ItemCallback<Result>() {
    override fun areItemsTheSame(oldItem: Result, newItem: Result) =
        oldItem.getUniqueId() == newItem.getUniqueId()
    
    override fun areContentsTheSame(oldItem: Result, newItem: Result) =
        oldItem == newItem
}
```

### 2. 이미지 로딩 최적화

**현재**: 뷰포트 수동 체크
**제안**: Coil의 자동 취소 및 우선순위 활용

```kotlin
imageLoader.enqueue(
    ImageRequest.Builder(context)
        .data(appPojo.getIcon())
        .target(appIcon)
        .placeholder(R.drawable.ic_placeholder)
        .crossfade(true)
        .lifecycle(lifecycleOwner)  // 자동 취소
        .build()
)
```

### 3. 프리페칭 (Prefetching)

**목적**: 스크롤 전에 미리 데이터/이미지 로드

```kotlin
class PrefetchingScrollListener : RecyclerView.OnScrollListener() {
    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
        val layoutManager = recyclerView.layoutManager as LinearLayoutManager
        val lastVisiblePosition = layoutManager.findLastVisibleItemPosition()
        
        // 다음 10개 아이템 프리페치
        for (i in lastVisiblePosition + 1..lastVisiblePosition + 10) {
            prefetchItem(i)
        }
    }
}
```

### 4. 가상 스크롤 (Paging)

**대규모 데이터셋**: 1000+ 앱

```kotlin
class AppPagingSource : PagingSource<Int, AppPojo>() {
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, AppPojo> {
        val page = params.key ?: 0
        val pageSize = params.loadSize
        
        val apps = dataHandler.getApps(offset = page * pageSize, limit = pageSize)
        
        return LoadResult.Page(
            data = apps,
            prevKey = if (page == 0) null else page - 1,
            nextKey = if (apps.isEmpty()) null else page + 1
        )
    }
}
```

### 5. 접근성 개선

**TalkBack 지원**:

```xml
<TextView
    android:id="@+id/item_app_name"
    android:contentDescription="@string/app_name_with_tags"
    android:importantForAccessibility="yes" />
```

**키보드 네비게이션**:

```java
list.setOnKeyListener((v, keyCode, event) -> {
    if (event.getAction() == KeyEvent.ACTION_DOWN) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
                // 커스텀 네비게이션
                return true;
        }
    }
    return false;
});
```

## 참고 자료

### 관련 파일

- `app/src/main/java/fr/neamar/kiss/ui/AnimatedListView.java`
- `app/src/main/java/fr/neamar/kiss/ui/BlockableListView.java`
- `app/src/main/java/fr/neamar/kiss/ui/KeyboardScrollHider.java`
- `app/src/main/java/fr/neamar/kiss/adapter/RecordAdapter.java`
- `app/src/main/java/fr/neamar/kiss/result/AppResult.java`
- `app/src/main/java/fr/neamar/kiss/MainActivity.java`
- `app/src/main/res/layout/main.xml`
- `app/src/main/res/layout/item_app.xml`

### 관련 문서

- [AsyncTask to Coroutines Migration](asynctask-to-coroutines-migration.md)
- [Icon Refresh Optimization Analysis](icon-refresh-optimization-analysis.md)
- [Optimization Analysis](optimization-analysis.md)

### Android 공식 문서

- [RecyclerView](https://developer.android.com/guide/topics/ui/layout/recyclerview)
- [DiffUtil](https://developer.android.com/reference/androidx/recyclerview/widget/DiffUtil)
- [ViewHolder Pattern](https://developer.android.com/reference/androidx/recyclerview/widget/RecyclerView.ViewHolder)
- [Paging 3](https://developer.android.com/topic/libraries/architecture/paging/v3-overview)

---

**작성일**: 2025-10-17  
**KISS 버전**: v4.1.7  
**분석자**: GitHub Copilot

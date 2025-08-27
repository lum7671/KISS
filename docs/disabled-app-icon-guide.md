# 최근 구현/수정 내역 (2025-08-27 기준)

## 1. suspended/disabled 앱 아이콘 회색(그레이) 처리 및 캐시 무효화 개선

- Android 7.0+에서 ApplicationInfo.flags & FLAG_SUSPENDED를 활용하여 suspended(휴면) 상태를 robust하게 감지하도록 개선
- AppPojo에 suspended 필드 및 isSuspended() 추가, isDisabled()는 disabled || suspended 반환
- AppResult, ShortcutsResult 등에서 DrawableUtils.setDisabled 호출 시 isDisabled, isSuspended 모두 전달하도록 시그니처 및 호출부 통일
- DrawableUtils.setDisabled(Drawable, boolean, boolean): suspended/disabled 중 하나라도 true면 ColorMatrixColorFilter(흑백) + alpha(반투명) 적용
- 모든 관련 호출부(앱/단축키/캐시 등)에서 새로운 setDisabled 시그니처로 통일
- 앱 상태 변경 시 캐시 무효화(invalidate) 및 항상 fresh Drawable에 필터 적용하도록 개선
- debug/logging 코드(모든 Log.d/w/e 등) 전면 제거 및 소스 정리
- 실제 suspended 앱(ADB로 suspend) 및 disabled 앱 모두 회색/반투명 아이콘 정상 동작 확인

---

# 비활성화(Disabled) 및 Frozen(휴면) 앱 아이콘 처리 설계 및 현황

## 목적

KISS 런처에서 비활성화(disabled) 또는 frozen(휴면) 상태의 앱을 앱 목록에 표시할 때, 아이콘을 회색(그레이) 톤으로 변환하여 시각적으로 구분되도록 처리하는 것이 목표다.
단, 실제 구현상 disabled와 frozen 앱의 목록 노출/표시 방식에는 차이가 있다.

## Disabled/Frozen 앱의 목록 노출 및 아이콘 처리 현황

### 1. Disabled 앱

- Android 시스템에서 앱이 "비활성화(disabled)" 상태(`ApplicationInfo.enabled == false`)가 되면, KISS 런처의 기본 구현에서는 **앱 목록에서 아예 제외(숨김)** 된다.
  - 관련 코드: `LoadAppPojosCoroutine.kt`에서 enabled==false인 앱은 continue로 스킵됨
  - 즉, disabled 앱은 회색 아이콘으로 표시되지 않고, **목록에 보이지 않음**
- 만약 disabled 앱도 항상 목록에 표시하고 싶다면, 해당 부분의 필터링 로직을 수정해야 함

### 2. Frozen(휴면) 앱

- "Frozen"(앱프리즈/휴면) 상태는 Android 표준 API에서는 별도 상태로 구분되지 않음
- 일반적으로 frozen 앱은 enabled=true로 남아 있으므로, KISS 런처에서는 **목록에 계속 표시**됨
- 단, 별도의 회색/비활성화 아이콘 처리는 적용되지 않음 (isDisabled()가 false)
- 일부 서드파티 툴(예: IceBox, Island 등)이 frozen 처리를 enabled=false로 구현하면, disabled와 동일하게 목록에서 숨겨짐
- 시스템적으로 enabled=true이나 frozen(실행 불가) 상태를 감지하려면 별도 로직이 필요함 (현재 미구현)

### 3. 요약

- **disabled 앱**: KISS 기본 동작에서는 목록에서 숨김(아이콘 회색 처리 X)
- **frozen 앱**: enabled=true면 목록에 보임(아이콘 회색 처리 X), enabled=false면 숨김
- **목표**: disabled/frozen 앱도 항상 목록에 표시하고, 회색/반투명 아이콘으로 구분하고 싶다면, 앱 로딩/필터링 및 아이콘 처리 로직을 모두 개선해야 함

## 관련 소스 구조 및 흐름

### 1. 주요 컴포넌트

- **Pojo/AppPojo/ShortcutPojo**: 각 앱/단축키의 상태(`isDisabled()`)를 제공
- **AppResult, ShortcutsResult**: 아이콘을 표시할 때 `DrawableUtils.setDisabled()` 호출
- **DrawableUtils**: 실제로 Drawable에 ColorMatrixColorFilter를 적용하여 회색(그레이) 톤으로 변환

### 1-1. 아이콘 이미지 캐싱 구조

- **IconCacheManager**: 아이콘 이미지는 고성능 캐싱을 위해 LruCache 기반 3단계(자주 사용, 최근 사용, 전체 메모리)로 관리됨
  - frequentCache(1단계, 소용량, 빠름), recentCache(2단계, 중간 크기), memoryCache(3단계, 대용량)
- **Glide**: 디스크/메모리 캐시를 함께 활용하여 이미지 재생성 최소화
- 각 캐시는 Drawable(아이콘 객체)을 키-값으로 저장하며, 사용 빈도도 추적
- 이 구조 덕분에 아이콘 로딩이 빠르고, 불필요한 이미지 재생성이 최소화됨

#### 참고 소스

- `/app/src/main/java/fr/neamar/kiss/utils/IconCacheManager.java` (Glide + LruCache + 3단계 캐싱)

### 2. 주요 메서드 및 처리 흐름

- `AppPojo.isDisabled()`, `ShortcutPojo.isDisabled()` → 각 객체의 disabled 상태 반환
- `AppResult.getDrawable(Context)` 등에서
  - `DrawableUtils.setDisabled(icon, this.pojo.isDisabled())` 호출
  - true일 경우 ColorMatrix(흑백) 필터 적용
- `DrawableUtils.setDisabled(Drawable drawable, boolean disabled)`
  - disabled==true면 ColorMatrixColorFilter(무채색) 적용
  - false면 clearColorFilter()

### 3. 실제 적용 예시

- `/app/src/main/java/fr/neamar/kiss/result/AppResult.java` (line 476)
- `/app/src/main/java/fr/neamar/kiss/result/ShortcutsResult.java` (line 156, 200)

### 4. 한계 및 개선 포인트 (현행 동작 기준)
  
- 회색(그레이) 톤 변환에 투명도(Alpha) 조정을 추가하여 시각적 구분을 더 강화
  - 단순 흑백 변환만으로 부족할 경우, alpha 값을 낮춰 비활성화 상태를 더욱 명확히 표현
  - (disabled 앱이 목록에 표시될 경우) 캐시된 아이콘 이미지가 앱의 활성/비활성 상태 변경 시 즉시 업데이트되지 않음
    - 예) 활성화 상태에서 캐시된 아이콘이 남아 있으면, 앱이 비활성화되어도 회색 처리가 바로 반영되지 않을 수 있음
    - 반대로, 비활성화 상태에서 활성화로 바뀌어도 캐시가 남아 있으면 계속 회색으로 보일 수 있음
    - 개선 방향: 앱의 enabled/disabled 상태가 바뀔 때 해당 앱의 아이콘 캐시를 명시적으로 삭제(invalidate)하거나,
      매번 아이콘을 그릴 때 원본 Drawable을 복제 후 ColorFilter를 적용하는 방식 필요
    - 관련 코드 개선 및 invalidate 로직 추가 필요
- 아이콘 캐시에 lifetime(유효 기간, 만료 시간) 적용 검토
  - 캐시 저장 시 timestamp(저장 시간)를 함께 기록하고, 일정 시간이 지나면 자동으로 캐시를 무효화
  - 앱 상태 변화(활성/비활성 등)가 없어도, 일정 주기로 아이콘이 새로고침되어 UI 일관성 및 메모리 관리에 도움
  - 만료 시간(예: 1분, 5분, 10분 등)은 옵션으로 조정 가능
  - 코드 구조상 LruCache, Glide 등과의 연동 및 성능 영향도 함께 고려 필요
- 자주 사용하는 앱(히스토리/사용빈도 기반)은 캐시에 고정 슬롯 또는 우선 캐시로 관리
  - SQLite 등에서 관리하는 자주 사용하는 앱 목록을 frequentCache 등 캐시의 고정 영역에 우선적으로 유지
  - LRU 캐시에서 자동으로 밀려나지 않도록 strong reference 또는 별도 캐시 영역 활용
  - 자주 노출되는 앱의 아이콘은 항상 빠르게 표시되어 UX 및 성능 모두 향상
  - DB(히스토리/사용빈도)와 연동해 캐시 관리 로직 개선 필요
- (우선순위 낮음) 캐시된 아이콘의 disabled 상태를 하루 1회 등 주기적으로 일괄 검증/무효화
  - 매번 검증하면 성능·배터리 소모가 크므로, 앱 실행 시 또는 하루 1회 등으로 제한
  - 캐시된 아이콘의 앱 상태를 일괄 점검하여, disabled 상태가 바뀐 앱의 캐시만 무효화
  - 일반적으로는 필요 없으나, 캐시 업데이트가 잘 안될 때 보완책으로 활용 가능
  
## TODO(구현 우선순위 및 향후 개선 방향)

1. **앱의 활성/비활성 상태 변경 시 캐시 무효화(invalidate) 및 즉시 반영**

- (disabled 앱이 목록에 표시될 경우) 앱의 enabled/disabled 상태가 바뀔 때 해당 앱의 아이콘 캐시를 명시적으로 삭제(invalidate)
- 또는 매번 아이콘을 그릴 때 원본 Drawable을 복제 후 ColorFilter를 적용하는 방식
- 관련 코드 개선 및 invalidate 로직 추가 필요

2. **회색(그레이) 톤 변환에 투명도(Alpha) 조정 추가**

- 단순 흑백 변환만으로 부족할 경우, alpha 값을 낮춰 비활성화 상태를 더욱 명확히 표현

3. **앱 목록 필터링 로직 개선(옵션화)**

- disabled/frozen 앱도 항상 목록에 표시할지, 숨길지 옵션화
- 표시할 경우, isDisabled/isFrozen 등 상태값을 기반으로 회색/반투명 아이콘 처리
- frozen(휴면) 상태 감지를 위한 별도 로직 필요할 수 있음

3. **자주 사용하는 앱(히스토리/사용빈도 기반)은 캐시에 고정 슬롯 또는 우선 캐시로 관리**
   - SQLite 등에서 관리하는 자주 사용하는 앱 목록을 frequentCache 등 캐시의 고정 영역에 우선적으로 유지
   - LRU 캐시에서 자동으로 밀려나지 않도록 strong reference 또는 별도 캐시 영역 활용
   - 자주 노출되는 앱의 아이콘은 항상 빠르게 표시되어 UX 및 성능 모두 향상
   - DB(히스토리/사용빈도)와 연동해 캐시 관리 로직 개선 필요
4. **아이콘 캐시에 lifetime(유효 기간, 만료 시간) 적용**
   - 캐시 저장 시 timestamp(저장 시간)를 함께 기록하고, 일정 시간이 지나면 자동으로 캐시를 무효화
   - 앱 상태 변화(활성/비활성 등)가 없어도, 일정 주기로 아이콘이 새로고침되어 UI 일관성 및 메모리 관리에 도움
   - 만료 시간(예: 1분, 5분, 10분 등)은 옵션으로 조정 가능
   - 코드 구조상 LruCache, Glide 등과의 연동 및 성능 영향도 함께 고려 필요
5. **(우선순위 낮음) 캐시된 아이콘의 disabled 상태를 하루 1회 등 주기적으로 일괄 검증/무효화**
   - 매번 검증하면 성능·배터리 소모가 크므로, 앱 실행 시 또는 하루 1회 등으로 제한
   - 캐시된 아이콘의 앱 상태를 일괄 점검하여, disabled 상태가 바뀐 앱의 캐시만 무효화
   - 일반적으로는 필요 없으나, 캐시 업데이트가 잘 안될 때 보완책으로 활용 가능

## 참고

- `DrawableUtils.java` 내 DISABLED_COLOR_FILTER, setDisabled() 구현 참고
- AppPojo, ShortcutPojo, AppResult, ShortcutsResult 등에서 isDisabled() 및 setDisabled() 호출 흐름 참고

---

(2025-08-27 기준, 자동 생성 / 2025-08-27 disabled/frozen 앱 목록 노출/표시 현황 및 한계 추가)

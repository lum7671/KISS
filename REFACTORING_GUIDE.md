# KISS 프로젝트 리팩터링 가이드

## 개요

이 문서는 KISS Android 런처 프로젝트의 리팩터링 계획과 구현 가이드를 제공합니다.

## 리팩터링 목표

1. **단일 책임 원칙** 적용으로 코드 가독성 향상
2. **유지보수성** 개선을 통한 버그 수정 및 기능 추가 용이성 확보
3. **테스트 가능성** 향상으로 품질 보장
4. **성능 최적화** 코드의 체계적 관리

## 현재 상태 분석

### 주요 문제점

| 클래스 | 라인 수 | 주요 문제 | 우선순위 |
|--------|---------|----------|----------|
| MainActivity.java | 1,247줄 | God Object, 너무 많은 책임 | 높음 |
| DataHandler.java | 1,175줄 | Provider 관리, 캐싱, 검색 로직 혼재 | 높음 |
| Result.java | 559줄 | 모든 결과 유형 처리 로직 | 중간 |
| PackageManagerUtils.java | 333줄 | 관련 없는 기능들의 집합 | 낮음 |

## 리팩터링 실행 계획

### Phase 1: 기반 구조 개선 ✅

#### 1.1 DTO/VO 클래스들 불변성 확보
- `ImmutablePojo` 기본 클래스 생성
- `ImmutableAppPojo` Builder 패턴 적용
- 불변 객체로 Thread-Safety 확보

#### 1.2 Interface 정의 및 의존성 주입 준비  
- `Repository<T, ID>` 기본 인터페이스
- `PojoRepository<T>` 전용 인터페이스
- `CachedPojoRepository<T>` 구현체

#### 1.3 유틸리티 클래스들 세분화
- `PackageManagerUtils` → 기능별 분리:
  - `IntentResolverUtils` - Intent 해결
  - `ApplicationInfoUtils` - 앱 정보 조회
  - `ComponentUtils` - 컴포넌트 관리

### Phase 2: 핵심 로직 분리 ✅

#### 2.1 MainActivity에서 Controller들 분리
- `SearchController` - 검색 기능 전담
- `UIController` - UI 상태 및 애니메이션 관리
- `LifecycleController` - 생명주기 및 시스템 이벤트 처리

#### 2.2 Repository 패턴 적용
- `CachedPojoRepository` 캐시 기능 구현
- 태그별 캐시 최적화
- 메모리 효율성 개선

### Phase 3: 디자인 패턴 적용 ✅

#### 3.1 Command 패턴으로 사용자 액션 처리
- `UserAction` 인터페이스 정의
- `LaunchAppAction` 구체적인 액션 구현
- `ActionManager` 중앙 집중식 액션 관리

#### 3.2 Observer 패턴으로 이벤트 처리
- 각 컨트롤러별 리스너 인터페이스
- 느슨한 결합으로 확장성 확보

## 리팩터링된 구조

### 새로운 패키지 구조

```
fr.neamar.kiss/
├── command/                    # Command 패턴
│   ├── UserAction.java
│   ├── ActionManager.java
│   └── actions/
│       └── LaunchAppAction.java
├── controller/                 # 컨트롤러들
│   ├── SearchController.java
│   ├── UIController.java
│   └── LifecycleController.java
├── repository/                 # Repository 패턴
│   ├── Repository.java
│   ├── PojoRepository.java
│   └── cache/
│       └── CachedPojoRepository.java
├── pojo/
│   └── immutable/             # 불변 객체들
│       ├── ImmutablePojo.java
│       └── ImmutableAppPojo.java
├── utils/
│   └── pm/                    # 패키지 매니저 유틸리티
│       ├── IntentResolverUtils.java
│       ├── ApplicationInfoUtils.java
│       └── ComponentUtils.java
└── refactored/                # 리팩터링된 클래스들
    └── RefactoredMainActivity.java
```

### 리팩터링 적용 예시

#### Before: 기존 MainActivity
```java
public class MainActivity extends Activity implements QueryInterface {
    // 1,247줄의 거대한 클래스
    // 검색, UI, 생명주기, 이벤트 처리 모든 것을 담당
}
```

#### After: 리팩터링된 MainActivity  
```java
public class RefactoredMainActivity extends Activity implements QueryInterface {
    private SearchController searchController;      // 검색 전담
    private UIController uiController;              // UI 전담
    private LifecycleController lifecycleController; // 생명주기 전담
    
    // 각 컨트롤러가 자신의 책임만 수행
}
```

## 적용 방법

### 1. 점진적 마이그레이션

기존 코드를 한번에 교체하지 않고 점진적으로 마이그레이션:

```java
// 1단계: 새 컨트롤러 도입
private SearchController searchController;

// 2단계: 기존 메서드를 컨트롤러로 위임
public void performSearch(String query) {
    // 기존 로직 (deprecated)
    // searchController.performSearch(query); // 새 로직
}

// 3단계: 기존 로직 완전 제거
```

### 2. 테스트 주도 리팩터링

```java
// 리팩터링 전후 동작 일치 확인
@Test
public void testSearchFunctionality() {
    // Given
    String query = "test app";
    
    // When - 기존 방식
    MainActivity oldActivity = new MainActivity();
    List<Result> oldResults = oldActivity.search(query);
    
    // When - 새 방식  
    SearchController controller = new SearchController(mockInterface);
    List<Result> newResults = controller.performSearch(query);
    
    // Then
    assertEquals(oldResults, newResults);
}
```

## 기대 효과

### 1. 코드 품질 개선
- **복잡도 감소**: 큰 클래스들이 작은 단위로 분해
- **가독성 향상**: 각 클래스의 역할이 명확
- **중복 제거**: 공통 로직의 재사용성 증가

### 2. 유지보수성 향상
- **변경 영향 최소화**: 수정 시 관련 클래스만 영향
- **버그 추적 용이**: 문제 발생 지점을 빠르게 특정
- **기능 추가 간편**: 새로운 Controller나 Action 추가만으로 확장

### 3. 테스트 용이성
- **단위 테스트**: 각 컨트롤러별 독립적 테스트 가능
- **Mock 객체**: 의존성 주입으로 Mock 테스트 지원
- **통합 테스트**: 컨트롤러 간 상호작용 테스트

### 4. 성능 최적화
- **메모리 관리**: 컨트롤러별 리소스 관리
- **캐시 효율성**: Repository 패턴의 체계적 캐싱
- **비동기 처리**: 각 컨트롤러의 독립적 스레드 관리

## 마이그레이션 체크리스트

### Phase 1 완료 확인
- [ ] `ImmutablePojo` 클래스 생성
- [ ] `Repository` 인터페이스 정의
- [ ] 유틸리티 클래스 분리

### Phase 2 완료 확인  
- [ ] `SearchController` 동작 검증
- [ ] `UIController` 애니메이션 테스트
- [ ] `LifecycleController` 이벤트 처리 확인

### Phase 3 완료 확인
- [ ] `Command 패턴` 액션 실행 테스트
- [ ] `Observer 패턴` 이벤트 전파 확인
- [ ] 전체 통합 테스트 통과

## 롤백 계획

리팩터링 실패 시 원상복구를 위한 계획:

1. **브랜치 관리**: 각 Phase별 별도 브랜치 생성
2. **백업 보관**: 원본 클래스들의 백업 유지
3. **단계별 롤백**: 문제 발생 시 이전 단계로 복구
4. **A/B 테스트**: 신구 버전 병행 운영으로 안정성 확인

## 결론

이 리팩터링을 통해 KISS 프로젝트는:
- **1,247줄의 MainActivity → 여러 100줄 내외의 컨트롤러들**로 분해
- **단일 책임 원칙** 적용으로 코드 품질 향상
- **확장 가능한 구조**로 미래 기능 추가 대비
- **테스트 친화적 구조**로 안정성 보장

성능 최적화 코드는 유지하면서도 구조적 개선을 달성할 수 있습니다.

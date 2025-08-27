
# KISS 프로젝트의 Shizuku 연동 구조 및 구현 상세

이 문서는 KISS 프로젝트에 실제로 구현된 Shizuku 연동 기능의 구조와 동작 방식을 소스코드 기반으로 구체적으로 정리합니다.

## 1. 주요 소스 위치

- **핸들러 구현:**
 	- `app/src/main/java/fr/neamar/kiss/ShizukuHandler.java` : Shizuku API를 직접 다루는 핵심 클래스
 	- `app/src/main/java/fr/neamar/kiss/RootHandler.java` : Shizuku와 루트 권한을 통합 관리
- **설정 UI:**
 	- `app/src/main/java/fr/neamar/kiss/preference/ShizukuModeSwitch.java` : Shizuku 모드 토글 및 권한 요청 UI
- **실제 사용 예:**
 	- `app/src/main/java/fr/neamar/kiss/result/AppResult.java` : 앱 절전(hibernate) 등 고급 기능에서 Shizuku 활용

## 2. 의존성 및 빌드 설정

- `app/build.gradle`에 아래와 같이 명시:
 ```gradle
 implementation 'dev.rikka.shizuku:api:13.1.5'
 implementation 'dev.rikka.shizuku:provider:13.1.5'
 ```

## 3. 주요 구현 흐름

### 3.1 ShizukuHandler: Shizuku API 래퍼

- **권한 및 서비스 상태 관리**
 	- Shizuku 서비스 설치/활성화/권한 여부를 감지 (`isShizukuAvailable`, `isShizukuActivated`, `hasShizukuPermission`)
 	- 리스너 등록/해제 및 상태 캐싱
- **권한 요청**
 	- `requestShizukuPermission()`에서 Shizuku 권한을 동적으로 요청
- **앱 강제 종료(절전)**
 	- `hibernateApp(String packageName)`
  → Shizuku를 통해 시스템 서비스(ActivityManager)에 접근, `forceStopPackage` 리플렉션 호출로 앱 종료

### 3.2 RootHandler: Shizuku와 루트 권한 통합

- ShizukuHandler를 내부적으로 보유
- Shizuku가 활성화/가용/권한이 있으면 우선적으로 Shizuku를 사용, 아니면 루트 권한 시도
- 앱 절전 등 고급 기능에서 Shizuku 우선 사용

### 3.3 UI 연동 및 사용자 경험

- **설정 스위치:**
 	- `ShizukuModeSwitch`에서 Shizuku 상태 확인, 권한 없으면 안내 및 요청 다이얼로그 표시
- **실제 기능 호출:**
 	- 예) 앱 롱프레스 메뉴에서 '절전' 선택 시, Shizuku를 통한 강제 종료 시도 (`AppResult.java`)

## 4. 예시: 앱 절전 기능 동작 흐름

1. 사용자가 앱 절전(hibernate) 기능 실행
2. `AppResult.java` → `RootHandler.hibernateApp()` 호출
3. Shizuku 활성화/가용/권한 체크
4. 조건 만족 시 `ShizukuHandler.hibernateApp()` → 시스템 서비스 접근 → 앱 종료

## 5. 기타 참고

- Shizuku 미설치/비활성/권한 거부 시, 적절한 안내 및 대체 동작(루트, 실패 안내 등) 제공
- Shizuku 리스너는 메모리 누수 방지를 위해 destroy 시점에 해제

---
이 문서는 실제 KISS 프로젝트의 소스 구조와 흐름을 바탕으로 작성되었습니다. 특정 기능의 상세 구현이 궁금하다면 파일명과 메서드명을 알려주시면 더 깊이 분석해드릴 수 있습니다.

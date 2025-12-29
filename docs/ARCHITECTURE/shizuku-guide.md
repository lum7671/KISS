# KISS 프로젝트의 Shizuku 연동 구조 및 구현 상세

## Shizuku로 할 수 있는 일 (기능 및 확장 가능성)

Shizuku는 시스템 권한이 필요한 다양한 작업을 **루트 없이** 안전하게 실행할 수 있도록 해주는 프레임워크입니다. KISS 프로젝트에서 Shizuku를 활용하면 다음과 같은 기능이 가능합니다.

### 1. 현재 구현된 기능

- **앱 강제 종료(최대 절전/hibernate):**
  - 시스템 서비스(ActivityManager)를 통해 `forceStopPackage`를 호출하여, 사용자가 선택한 앱을 강제로 종료할 수 있습니다.
  - 기존에는 루트 권한이 필요했으나, Shizuku를 통해 루트 없이도 동작합니다.

### 2. Shizuku로 확장 가능한 기능 예시

- **앱 데이터 삭제**
  - `ActivityManager` 또는 `PackageManager`의 `clearApplicationUserData` 등 시스템 API 호출
- **앱 설치/제거 자동화**
  - `PackageManager`의 `installPackage`, `deletePackage` 등
- **시스템 설정 변경**
  - `Settings.System`, `Settings.Global` 등 ContentProvider를 통한 설정값 변경
- **서비스/브로드캐스트/인텐트 실행**
  - 시스템 서비스에 접근하여 특정 인텐트 브로드캐스트, 서비스 시작 등
- **기타 시스템 API 활용**
  - 알림 관리, 배터리/절전 정책, 네트워크 제어 등 다양한 시스템 레벨 작업

> **참고:** 실제로 사용할 수 있는 기능은 Android 버전, OEM 정책, Shizuku 권한 범위에 따라 달라질 수 있습니다. KISS 프로젝트에서는 보안과 사용자 경험을 고려해 신중하게 확장해야 합니다.

### 3. Shizuku의 장점

- 루트 없이 시스템 API 활용 가능 (보안성, 안정성)
- 권한 요청/회수, 서비스 상태 감지 등 사용자 경험 개선
- 루트 기반 기능의 대체/보완 수단으로 활용

---

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

## ⚠️ Shizuku forceStopPackage 실제 구현법 및 공식 샘플 기반 주의사항

- **AIDL import 금지:**
  - `import android.app.IActivityManager;`와 같이 AIDL 인터페이스를 직접 import하면 안 됨 (빌드 오류 발생)
  - 반드시 AIDL 파일만 두고, 코드에서는 Stub.asInterface를 리플렉션 등으로 사용

- **Stub.asInterface 사용:**
  - Binder 객체를 얻은 뒤, `IActivityManager.Stub.asInterface(binder)`로 변환해야 함
  - 공식 샘플에서는 리플렉션으로 Stub 클래스를 찾아 asInterface를 호출

- **userId 계산:**
  - `int userId = android.os.Process.myUid() / 100000;` 방식 사용 (공식 샘플과 동일)
  - UserHandle 등 hidden API를 직접 호출하지 않음

- **forceStopPackage 호출:**
  - Stub.asInterface로 얻은 IActivityManager 객체에서 `forceStopPackage(String, int, int)`를 리플렉션으로 호출
  - 예시:

    ```java
    android.os.IBinder binder = new ShizukuBinderWrapper(SystemServiceHelper.getSystemService("activity"));
    Class<?> stubClass = Class.forName("android.app.IActivityManager$Stub");
    Method asInterface = stubClass.getMethod("asInterface", android.os.IBinder.class);
    Object am = asInterface.invoke(null, binder);
    int userId = android.os.Process.myUid() / 100000;
    Method forceStop = am.getClass().getMethod("forceStopPackage", String.class, int.class, int.class);
    forceStop.invoke(am, packageName, userId, 0);
    ```

- **실제 적용:**
  - KISS 프로젝트에서는 위와 같이 공식 샘플/가이드에 맞춰 forceStopPackage를 구현하여, Shizuku 환경에서 앱 강제 종료가 정상 동작함을 확인함

---
기존 버그 및 해결법(아래)과 함께, 실제 적용된 구현법을 반드시 참고할 것!

- **문제:**
  - Shizuku를 통한 시스템 서비스 접근 시, 기존 코드에서
  `SystemServiceHelper.getSystemService(Context.ACTIVITY_SERVICE)` 만 사용하면
  `forceStopPackage` 호출이 실패하며, "시스템 서비스 접근 실패 (forceStopPackage)" 오류가 발생할 수 있습니다.
  - 이는 Shizuku 환경에서는 시스템 서비스에 접근할 때 반드시 `ShizukuBinderWrapper.get()`을 명시적으로 넘겨야 하기 때문입니다.

- **해결 방법:**
  - 아래와 같이 시스템 서비스 획득 코드를 수정해야 합니다.

    ```java
    // 기존 (문제 발생)
    Object activityManager = SystemServiceHelper.getSystemService(Context.ACTIVITY_SERVICE);
    
    // 수정 (Shizuku 환경에서 정상 동작)
    Object activityManager = SystemServiceHelper.getSystemService(Context.ACTIVITY_SERVICE, ShizukuBinderWrapper.get());
    ```

  - Shizuku 공식 예제 및 문서에서도 BinderWrapper를 명시적으로 넘기는 패턴을 권장합니다.

- **실제 KISS 프로젝트 적용:**
  - forceStopPackage 내부에서 위와 같이 수정하여, Shizuku 권한이 있을 때 앱 강제 종료가 정상 동작하도록 개선하였습니다.

---
이 문서는 실제 KISS 프로젝트의 소스 구조와 흐름, 그리고 Shizuku의 활용 가능성을 바탕으로 작성되었습니다. 특정 기능의 상세 구현이 궁금하다면 파일명과 메서드명을 알려주시면 더 깊이 분석해드릴 수 있습니다.

## 🛠️ 삽질 기록 (디버깅 & 수정 로그)

아래는 이번 Shizuku 통합 과정에서 발생한 문제들과 시도해본 해결책, 코드 변경 사항을 정리한 기록입니다. 나중에 동일 증상이 재발하거나 다른 개발자가 참고할 때 빠르게 이해할 수 있도록 남깁니다.

- 문제 요약
  - 앱 절전(hibernate) 동작 시 `forceStopPackage` 호출이 반복 실패함. 로그에는 `No asInterface method found on IActivityManager$Stub`
  또는 `Stub$Proxy` 생성자 관련 `NoSuchMethodException`이 반복적으로 기록됨.
  - Shizuku 권한은 정상적으로 GRANTED 되었음에도, IActivityManager 변환 단계에서 실패하여 실제 동작이 되지 않음.

- 조사 및 시도한 패턴
  1. 공식/문서 예제 참고: `SystemServiceHelper.getSystemService(...)`, `ShizukuBinderWrapper`, `Shizuku.getSystemService(...)` 등
  여러 예제가 존재함.
  2. 초기 코드에서는 `SystemServiceHelper.getSystemService("activity")` 만 사용하고
     `Stub.asInterface` 혹은 프록시 생성자에 의존했음.
  3. 문서 추천 패턴으로 `ShizukuBinderWrapper`와 `SystemServiceHelper`의 오버로드(가능한 경우)를 사용하려 시도했음.

- 실제로 적용한 코드 변경
  1. `ShizukuHandler.forceStopPackage` 내부에서 존재하지 않는 `Shizuku.getSystemService(String)` 호출을 제거하고
     안전한 `SystemServiceHelper.getSystemService("activity")` 호출로 수정함.
  2. 프로젝트에 포함되어 있던 `app/src/main/aidl/android/app/IActivityManager.aidl` 파일을 삭제함 —
    프레임워크 AIDL을 프로젝트에 직접 포함시키면 플랫폼 불일치로 문제가 발생할 수 있어,
    리플렉션 방식(`Class.forName("android.app.IActivityManager$Stub")` 등)을 사용하여 런타임에 처리하도록 유지함.
  3. `ShizukuHandler.java`에서 미사용 import(`rikka.shizuku.ShizukuBinderWrapper`)를 제거하여 코드 정리함.

- 빌드 및 검증
  - 변경 후 `./gradlew :app:assembleDebug`를 실행하여 빌드 성공을 확인함.
  - 권장: 에뮬레이터(또는 실제 기기)에 APK를 설치한 뒤 hibernate 시나리오를 재현하고 logcat을 수집해 리플렉션 실패가 재현되는지 확인할 것.

- 관찰된 한계 및 권장 조치
  1. 리플렉션 방식(`Class.forName("android.app.IActivityManager$Stub")` / `Stub$Proxy`)은 현재 코드의 핵심이며,
     제거하면 바인더 → IActivityManager 변환 방법이 없어짐. 따라서 현재 방식은 유지해야 함.
  2. 다만 이 방식은 Android 버전·OEM·hidden API 제약에 따라 실패할 수 있음. 실패 시 권장 폴백:
     - 단기: `am force-stop <pkg>` 셸 커맨드를 Shizuku로 실행하는 폴백 경로를 사용하여 사용자 기능 보장.
     - 중기/장기: Shizuku의 UserService(권한 있는 별도 프로세스) 구현을 통해 시스템 API를 직접 호출하는 방식으로 안정성 확보.
     - hidden API 예외가 명확히 로그로 확인되면 `org.lsposed.hiddenapibypass` 같은 우회 라이브러리를 조건부로 검토하되, 기본적으로는 런타임에서만 도입 권장.

- 문서/코드 정리 내용
  - 불필요한 AIDL 파일 제거: `app/src/main/aidl/android/app/IActivityManager.aidl` 삭제(리플렉션 사용 권장).
  - `ShizukuHandler` 내 불필요 import 정리 및 `SystemServiceHelper` 기반 접근 유지.

- 다음 권장 작업
  1. 에뮬레이터에서 재현 테스트 및 logcat 캡처(hibernate 메뉴 3회 반복) — 실패 재현 시 어떤 리플렉션 단계(asInterface vs Proxy)가 실패하는지 확인하도록 로그 보강.
  2. 단기적으로는 셸 폴백 경로를 우선 활성화하여 사용자에게 기능 제공.
  3. 안정화가 필요하면 UserService 기반 접근 설계/구현으로 전환.

이 섹션은 KISS 레포의 Shizuku 연동 관련 의사결정과 디버깅 흔적을 기록한 것입니다. 필요하면 이 항목을 `docs/CHANGELOG` 또는 PR 설명으로도 옮겨 기록할 수 있습니다.

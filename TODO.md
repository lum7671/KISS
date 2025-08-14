# KISS Launcher TODO List

## 📅 Updated: 2025-08-14

## ✅ 완료된 작업들

### 주요 버그 수정

- [x] **MainActivity AppCompatActivity 호환성 문제 해결**
  - AppCompatActivity → Activity로 되돌림
  - Theme.AppCompat 테마 충돌 문제 해결
  - onBackPressed() 메서드 구현 복구

- [x] **About 메뉴 크래시 수정**
  - SettingsActivity.fixSummaries()에서 NullPointerException 수정
  - findPreference() 결과에 null check 추가

- [x] **아이콘 로딩 문제 해결**
  - AsyncSetImage: Runnable → AsyncTask로 되돌림
  - 아이콘이 표시되지 않는 문제 완전 해결
  - 원래 AsyncTask.SERIAL_EXECUTOR 방식 복구

### 성공적으로 현대화된 부분

- [x] **Handler 생성자 현대화**
  - `new Handler()` → `new Handler(Looper.getMainLooper())`
  - UI 스레드 안전성 향상

- [x] **Searcher 클래스 현대화**
  - AsyncTask → Runnable + ExecutorService 변환
  - 백그라운드 작업 스레딩 개선

- [x] **빌드 시스템 현대화**
  - Gradle 8.13으로 업그레이드
  - AndroidX 라이브러리 통합 (appcompat, fragment, preference)
  - lint-baseline.xml 추가로 경고 관리

## 🔄 진행 중 / 알려진 이슈

### Deprecated API 경고 (100개)

현재 빌드는 성공하지만 100개의 deprecated API 경고가 남아있음.
**우선순위: 낮음** (기능적으로 문제없음)

#### PreferenceManager 관련 (가장 많음)

- [ ] `android.preference.PreferenceManager` → `androidx.preference.PreferenceManager`
- 파일들: MainActivity.java, DataHandler.java, SettingsActivity.java, 기타 다수
- 참고: 일부는 이미 androidx로 변경되었으나 일관성 부족

#### AsyncTask 관련

- [ ] `SaveSingleOreoShortcutAsync.java` - AsyncTask → ExecutorService 변환
- [ ] `SaveAllOreoShortcutsAsync.java` - AsyncTask → ExecutorService 변환
- 참고: Result.java의 AsyncSetImage는 의도적으로 AsyncTask 유지 (아이콘 로딩 안정성)

#### 기타 Deprecated APIs

- [ ] `getParcelableExtra()` → `getParcelableExtra(Class)`
- [ ] `startActivityForResult()` → Activity Result API
- [ ] `Resources.getColor()` → `ContextCompat.getColor()`
- [ ] `Resources.getDrawable()` → `ContextCompat.getDrawable()`
- [ ] System UI Visibility 관련 → WindowInsetsController

## 🎯 향후 개선 계획

### 단기 (선택사항)

- [ ] **PreferenceManager 통일**
  - 모든 파일에서 androidx.preference 사용하도록 통일
  - 예상 작업량: 중간

- [ ] **Shortcut AsyncTask 현대화**
  - SaveSingleOreoShortcutAsync, SaveAllOreoShortcutsAsync 현대화
  - Searcher.java와 동일한 패턴 적용

### 중기 (필요시)

- [ ] **Activity Result API 적용**
  - startActivityForResult() 대체
  - 더 안전한 액티비티 간 통신

- [ ] **WindowInsetsController 적용**
  - System UI Visibility 현대화
  - Android 11+ 호환성 향상

### 장기 (선택사항)

- [ ] **완전한 androidx 마이그레이션**
  - 남은 모든 deprecated API 제거
  - Material Design 3 적용 검토

## 🚀 현재 상태

### ✅ 정상 작동하는 기능들

- 앱 실행 및 런처 기능
- 아이콘 표시
- About 메뉴 접근
- 설정 화면
- 검색 기능
- 백그라운드 작업 처리

### 📊 프로젝트 통계

- **빌드 상태**: ✅ 성공
- **크래시**: ❌ 없음
- **Deprecated 경고**: 100개 (기능에 영향 없음)
- **APK 크기**: ~2.3MB (AndroidX 라이브러리 추가로 인한 증가)

## 📝 개발 노트

### AsyncSetImage 관련 중요 사항

```text
커밋 cf8b384a에서 AsyncSetImage를 Runnable로 변경했으나 아이콘 로딩 실패 발생.
원인: ExecutorService 구현에서 스레드 관리 및 UI 업데이트 타이밍 문제
해결: AsyncTask 방식으로 되돌림 (deprecated이지만 안정적)
```

### 테마 호환성 이슈

```text
AppCompatActivity 사용 시 Theme.AppCompat 필요하나 기존 Theme.Holo와 충돌
해결: MainActivity를 Activity로 유지하여 호환성 확보
```

### AndroidX 마이그레이션 상태

```text
부분적 마이그레이션 완료:
- androidx.appcompat:appcompat
- androidx.fragment:fragment  
- androidx.preference:preference

하지만 일부 코드에서는 여전히 android.preference 사용 (혼재 상태)
```

## 🔧 개발 환경

- **Gradle**: 8.13
- **Compile SDK**: 35
- **Min SDK**: 33
- **Target SDK**: 35
- **언어**: Java + Kotlin (소량)

---

## 💡 마지막 업데이트

**날짜**: 2025-08-14  
**상태**: 🎉 안정적 - 모든 주요 기능 정상 작동  
**다음 작업**: 필요에 따라 deprecated API 점진적 해결

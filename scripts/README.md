# 개발 스크립트

이 폴더에는 KISS 런처 개발에 유용한 스크립트들이 들어있습니다.

## 빌드 스크립트

- `build_profile_apk.sh` - 프로파일링용 APK 빌드
- `build_release_apk.sh` - 릴리즈용 APK 빌드

## 테스트 스크립트

- `install_and_test.sh` - APK 설치 및 테스트 자동화
- `run_emulator.sh` - 에뮬레이터 실행

## 사용법

모든 스크립트는 프로젝트 루트 디렉토리에서 실행하세요:

```bash
# 예: 릴리즈 APK 빌드
./scripts/build_release_apk.sh

# 예: 에뮬레이터 실행
./scripts/run_emulator.sh
```

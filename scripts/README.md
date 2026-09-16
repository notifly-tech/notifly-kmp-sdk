# 스크립트 안내

이 폴더는 KMP SDK의 릴리스 준비, 빌드 산출물 검증, 자동화 검증에 사용하는 스크립트를 모아둡니다. 앱에서 실행되는 SDK 기능 코드는 아닙니다.

아래 명령은 모두 저장소 루트에서 실행합니다. `smoke`는 빌드한 패키지를 별도 소비자 코드에서 실제로 사용할 수 있는지 확인하는 기본 검증을 뜻합니다.

## 스크립트 목록

### 릴리스 준비

| 파일 | 역할 | 자동 실행 위치 |
| --- | --- | --- |
| [prepare_release.sh](prepare_release.sh) | XCFramework를 빌드하고 ZIP과 SHA-256 체크섬 파일을 만듭니다. `SKIP_BUILD=true`이면 기존 빌드 결과를 사용합니다. 업로드나 공개 패키지 배포는 하지 않습니다. | CI, 릴리스 워크플로 |
| [update_release_manifests.rb](update_release_manifests.rb) | 버전과 ZIP 체크섬을 받아 `Package.swift`의 다운로드 주소·체크섬, `NotiflyKMP.podspec`의 버전·체크섬을 수정합니다. | 릴리스 워크플로에서 새 태그를 준비할 때 |

### 빌드 산출물 검증

| 파일 | 역할 | 자동 실행 위치 |
| --- | --- | --- |
| [smoke-maven-local.sh](smoke-maven-local.sh) | 로컬 Maven에 설치한 KMP 라이브러리를 별도 JVM 프로젝트에서 가져와 실행합니다. Kotlin 1.8.10 소비자와의 호환성 및 공개 API 동작을 확인합니다. 테스트 본문은 `smoke-tests/maven-consumer`에 있습니다. | CI, 릴리스 워크플로 |
| [smoke-js-package.mjs](smoke-js-package.mjs) | 빌드한 npm tarball을 임시 프로젝트에 설치합니다. 라이선스·타입 선언·소스맵 포함 여부와 Node.js에서의 공개 API 동작을 확인하고 임시 프로젝트를 삭제합니다. | CI, 릴리스 워크플로 |
| [smoke-apple-artifacts.sh](smoke-apple-artifacts.sh) | Apple 산출물 검증의 진입점입니다. Swift 소비자 테스트를 호출한 뒤 임시 디렉터리에서 `pod lib lint`로 CocoaPods 패키지를 검사합니다. | CI, 릴리스 워크플로 |
| [smoke-swift-consumer.sh](smoke-swift-consumer.sh) | 빌드된 XCFramework에 Swift 테스트 프로그램을 연결해 컴파일하고 iPhone 시뮬레이터에서 실행합니다. 임시 파일과 자신이 부팅한 시뮬레이터를 정리하며, 이미 켜져 있던 시뮬레이터는 종료하지 않습니다. | `smoke-apple-artifacts.sh`에서 호출 |
| [smoke-swift-consumer.swift](smoke-swift-consumer.swift) | Swift 소비자 테스트 본문입니다. 사용자 ID 전환 API와 팝업의 `static` 결과, 종료 후 오류, 취소 및 콜백 중복 여부를 확인합니다. 독립 실행 스크립트가 아니라 위 셸 스크립트가 컴파일하는 소스입니다. | `smoke-swift-consumer.sh`에서 사용 |
| [smoke-host-packaging.sh](smoke-host-packaging.sh) | 플랫폼 SDK가 KMP를 다른 패키지 이름·버전·Apple 프레임워크 이름으로 빌드할 수 있는지 확인합니다. Maven·npm·Apple 산출물과 Apple 동적 프레임워크 형식을 검사합니다. 공개 저장소에 배포하지 않습니다. | CI |

Swift·JS·JVM 소비자 테스트는 실제 stage/prod Render API를 호출하는 E2E 테스트가 아닙니다. 소비자 테스트의 5초 대기 제한도 SDK 자체의 요청 타임아웃 설정과 별개입니다.

### 자동화 자체의 검증

| 파일 | 역할 | 자동 실행 위치 |
| --- | --- | --- |
| [test-release-contracts.rb](test-release-contracts.rb) | 워크플로와 설정 파일을 읽어 릴리스 규칙을 검사합니다. 패키지 이름, 필수 검증 단계, 체크섬 일치, 플랫폼 SDK 업데이트 연결, 이 저장소에서 직접 npm·CocoaPods·JitPack 배포를 하지 않는 규칙 등을 확인합니다. | CI |
| [test-sdk-bump-workflow.rb](test-sdk-bump-workflow.rb) | 임시 Git 저장소와 가짜 GitHub CLI로 SDK의 KMP 서브모듈 업데이트 워크플로를 테스트합니다. 유효한 릴리스 태그·대상 저장소만 허용하고 서브모듈 참조만 변경하는지 확인합니다. 실제 SDK 저장소 수정이나 PR 생성은 하지 않습니다. | CI |
| [test-swift-consumer-cleanup.rb](test-swift-consumer-cleanup.rb) | 가짜 `xcrun`으로 Swift 실행 스크립트의 시뮬레이터 정리 동작을 검사합니다. 정상 종료, 부팅 실패, 부팅 대기 실패, 이미 부팅된 기기 사용을 검증합니다. 실제 시뮬레이터를 부팅하지 않습니다. | 자동 연결 없음. 수동 실행용 |

## 실행 준비

- JVM 빌드: JDK 17 및 저장소의 Gradle Wrapper.
- JS 패키지 검증: Node.js 22와 npm. 임시 프로젝트 안에서 `npm install`을 사용합니다.
- Ruby 검증: Ruby와 `yaml`, `minitest` 등의 사용 가능한 라이브러리. SDK 업데이트 테스트에는 Git도 필요합니다.
- Apple 검증: macOS, Xcode 16 이상, CocoaPods, 사용 가능한 iPhone 시뮬레이터. 현재 Swift 소비자 실행 스크립트는 `arm64` 시뮬레이터 바이너리를 만듭니다.
- 최초 빌드와 의존성 설치에는 네트워크 접근이 필요할 수 있습니다.

## 로컬 실행 예시

### 자동화 검증만 실행

```bash
ruby scripts/test-release-contracts.rb
ruby scripts/test-sdk-bump-workflow.rb
ruby scripts/test-swift-consumer-cleanup.rb
```

위 명령은 실제 릴리스나 SDK 업데이트 PR을 만들지 않습니다.

### 플랫폼 패키지를 빌드하고 소비자 테스트 실행

```bash
KMP_SCRIPT_VERSION=0.1.0-alpha.1

VERSION="$KMP_SCRIPT_VERSION" ./gradlew \
  publishToMavenLocal \
  packJsPackage \
  assembleNotiflyKMPReleaseXCFramework \
  --no-daemon

VERSION="$KMP_SCRIPT_VERSION" scripts/smoke-maven-local.sh
node scripts/smoke-js-package.mjs "build/packages/notifly-kmp-sdk-$KMP_SCRIPT_VERSION.tgz"
scripts/smoke-apple-artifacts.sh
```

`publishToMavenLocal`은 개발 머신의 로컬 Maven 저장소에 산출물을 설치합니다. 공개 Maven 저장소에 배포하는 명령은 아닙니다. Maven 소비자에 전달하는 `VERSION`은 빌드할 때 사용한 버전과 같아야 합니다.

Swift 소비자만 확인하려면 XCFramework가 빌드된 상태에서 `scripts/smoke-swift-consumer.sh`를 실행합니다. `smoke-apple-artifacts.sh`에 이미 포함되어 있으므로 둘을 연달아 실행할 필요는 없습니다.

### 플랫폼 SDK용 이름으로 재포장 검증

```bash
scripts/smoke-host-packaging.sh
```

이 검증은 테스트용 버전 `9.8.7`과 패키지 이름 `core`, `notifly-core-sdk`, `NotiflyCore`를 사용합니다. 실제 SDK 릴리스 버전을 변경하지는 않습니다.

주의: 내부에서 Gradle `clean`을 실행해 현재 빌드 산출물을 지우고 테스트용 이름으로 다시 만듭니다. 다른 빌드와 동시에 실행하지 말고, 기본 이름의 산출물이 필요한 검증을 먼저 끝내거나 이후 다시 빌드하세요.

### 릴리스 ZIP 준비

```bash
KMP_SCRIPT_VERSION=0.1.0-alpha.1
VERSION="$KMP_SCRIPT_VERSION" scripts/prepare_release.sh "$KMP_SCRIPT_VERSION"
```

결과는 `build/release/<version>/NotiflyKMP.xcframework.zip`과 `.zip.sha256`에 생성됩니다. 해당 버전으로 빌드한 XCFramework가 이미 있다면 `SKIP_BUILD=true`를 추가할 수 있습니다.

매니페스트 업데이트는 보통 릴리스 워크플로가 담당합니다. 수동으로 수행하면 다음 명령이 저장소의 `Package.swift`와 `NotiflyKMP.podspec`을 실제로 수정합니다.

```bash
KMP_SCRIPT_CHECKSUM="$(swift package compute-checksum "build/release/$KMP_SCRIPT_VERSION/NotiflyKMP.xcframework.zip")"
scripts/update_release_manifests.rb "$KMP_SCRIPT_VERSION" "$KMP_SCRIPT_CHECKSUM"
```

## 관련 파일

- [CI 워크플로](../.github/workflows/ci.yml): PR 및 main push 검증.
- [릴리스 워크플로](../.github/workflows/release.yml): 테스트·패키징, GitHub 릴리스, 플랫폼 SDK 업데이트 PR 생성.
- [SDK 서브모듈 업데이트 워크플로](../.github/workflows/bump-sdk-submodule.yml): 각 플랫폼 SDK의 KMP 참조 업데이트.
- [JVM 소비자 프로젝트](../smoke-tests/maven-consumer): Maven 산출물을 사용하는 별도 테스트 프로젝트.
- [브라우저 HTTP fixture](../karma.config.d/popup-loopback-fixture.js): `scripts/` 밖에 있는 로컬 테스트 응답 제공 코드. `jsBrowserTest`에서 CORS·쿠키·리다이렉트·취소 동작을 검사할 때 사용합니다.

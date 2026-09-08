<p align="center">
  <a href="https://notifly.tech">
    <img src="https://avatars.githubusercontent.com/u/147061310?s=192&v=4" width="96" alt="Notifly" />
  </a>
</p>

<h1 align="center">Notifly KMP SDK</h1>

<p align="center">
  Shared Kotlin Multiplatform core for the Notifly Android, iOS, and JavaScript SDKs.
</p>

<p align="center">
  <a href="https://github.com/notifly-tech/notifly-kmp-sdk/actions/workflows/ci.yml"><img src="https://github.com/notifly-tech/notifly-kmp-sdk/actions/workflows/ci.yml/badge.svg" alt="CI" /></a>
  <a href="https://github.com/notifly-tech/notifly-kmp-sdk/releases"><img src="https://img.shields.io/github/v/release/notifly-tech/notifly-kmp-sdk?include_prereleases&label=release" alt="Latest release" /></a>
  <a href="https://github.com/notifly-tech/notifly-kmp-sdk/blob/main/LICENSE"><img src="https://img.shields.io/github/license/notifly-tech/notifly-kmp-sdk" alt="License" /></a>
</p>

> [!IMPORTANT]
> This is an internal shared core, not a standalone SDK for application developers. Install the Notifly SDK for your platform instead.

## At a glance

| | |
| --- | --- |
| Module | root project |
| Package | `tech.notifly.kmp` |
| Targets | JVM, Kotlin/JS IR, iOS device and simulator |
| Consumers | Notifly Android, iOS, and JavaScript SDKs |
| Current shared policy | `UserIdTransitionPolicy` |

## Architecture

```mermaid
flowchart LR
    KMP["notifly-kmp-sdk<br/>Shared deterministic logic"]
    Android["Android SDK<br/>Maven artifact"]
    iOS["iOS SDK<br/>XCFramework"]
    JS["JavaScript SDK<br/>npm / CDN bundles"]

    KMP --> Android
    KMP --> iOS
    KMP --> JS
```

Each platform SDK keeps its existing public API and platform-specific behavior. Only deterministic, platform-independent logic belongs in this repository.

## Current scope

`UserIdTransitionPolicy` produces the same user ID transition decision on every platform:

```kotlin
val decision = UserIdTransitionPolicy.evaluate(
    previousUserId = null,
    newUserId = "user-123",
)

decision.shouldSync  // true
decision.shouldMerge // true
decision.shouldClear // false
```

## Platform integration

| SDK | How it consumes the KMP core | Customer-facing artifact |
| --- | --- | --- |
| Android | Builds the pinned submodule as a Gradle project | Maven Central package |
| iOS | Builds a static `NotiflyKMP.xcframework` and links it into the SDK | `notifly_sdk.xcframework` |
| JavaScript | Builds Kotlin/JS locally and inlines it into the SDK bundles | npm and CDN bundles |

Host SDKs pin an exact KMP tag commit as a Git submodule. Moving branches and version ranges are not supported.

## Development

Requirements:

- JDK 17
- Node.js 22
- Xcode 16 or later for Apple targets

Run the shared test suite:

```bash
./gradlew \
  jvmTest \
  jsNodeTest \
  iosSimulatorArm64Test \
  --no-daemon
```

Build and smoke-test all platform artifacts:

```bash
./gradlew \
  publishToMavenLocal \
  packJsPackage \
  assembleNotiflyKMPReleaseXCFramework \
  --no-daemon

scripts/smoke-maven-local.sh
node scripts/smoke-js-package.mjs build/packages/notifly-kmp-sdk-0.1.0-alpha.1.tgz
scripts/smoke-apple-artifacts.sh
```

## Kotlin compatibility

The build uses Kotlin Gradle Plugin `2.2.21` for current Multiplatform and Apple toolchain support. Published common and JVM code is restricted to Kotlin language/API version `1.8` and Kotlin stdlib `1.8.10`, keeping it compatible with the current Notifly Android SDK.

Kotlin/JS uses the stdlib matching the build compiler, as required by the Kotlin/JS compiler. This does not change the Kotlin requirement of the Android/JVM artifact.

## Release flow

1. Run the `Release` workflow with an `x.y.z-alpha.n` version.
2. Verify JVM, JavaScript, and Apple outputs and their smoke tests.
3. Create an immutable Git tag and GitHub prerelease.
4. Open submodule-bump pull requests for the Android, iOS, and JavaScript SDKs.
5. Let each host SDK build and publish its own customer-facing artifact.

The attached `NotiflyKMP.xcframework` is a validation artifact. The iOS SDK rebuilds the framework from its pinned source. This repository does not directly publish npm, CocoaPods, or JitPack packages.

## Repository structure

```text
.
├── src/          # Shared Kotlin Multiplatform sources
├── scripts/      # Release preparation and smoke tests
├── smoke-tests/  # Consumer-level verification projects
└── .github/      # CI and release workflows
```

## Links

- [Notifly](https://notifly.tech)
- [Documentation](https://docs.notifly.tech)
- [Releases](https://github.com/notifly-tech/notifly-kmp-sdk/releases)
- [Issues](https://github.com/notifly-tech/notifly-kmp-sdk/issues)
- [MIT License](LICENSE)

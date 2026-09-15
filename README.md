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
  <a href="https://github.com/notifly-tech/notifly-kmp-sdk/blob/main/LICENSE"><img src="https://img.shields.io/github/license/notifly-tech/notifly-kmp-sdk" alt="License" /></a>
</p>

> [!NOTE]
> This repository contains shared source code. Each platform SDK builds and distributes its own Core and Full packages. Application developers can install either the Full SDK or the platform's Core package.

## Architecture

```mermaid
flowchart TB
    KMP["notifly-kmp-sdk<br/>Shared source code"]
    Android["Android SDK<br/>Core JAR + Full AAR"]
    iOS["iOS SDK<br/>Core XCFramework + Full Swift sources"]
    JS["JavaScript SDK<br/>Core + Full packages"]
    RN["React Native SDK"]
    Flutter["Flutter SDK"]

    KMP --> Android
    KMP --> iOS
    KMP --> JS
    Android --> RN
    iOS --> RN
    Android --> Flutter
    iOS --> Flutter
    JS -->|Web| Flutter
```

Each platform SDK keeps its existing public API and platform-specific behavior. Only deterministic, platform-independent logic belongs in this repository.

## Platform integration

The Android, iOS, and JavaScript repositories pin a KMP source commit as a Git submodule and build their own Core artifacts. Core and Full use the same platform SDK version, and Full depends on that exact Core version.

| SDK | Integration and distribution |
| --- | --- |
| [Android](https://github.com/team-michael/notifly-android-sdk) | Gradle composite build; Core JAR and Full AAR distributed through JitPack. |
| [iOS](https://github.com/team-michael/notifly-ios-sdk) | Dynamic `NotiflyCore.xcframework` and Full SDK Swift sources, available through SwiftPM and CocoaPods. |
| [JavaScript](https://github.com/team-michael/notifly-js-sdk) | `notifly-core-sdk` and `notifly-js-sdk` on npm; browser bundles include the required Core code. |
| [React Native](https://github.com/team-michael/notifly-react-native-sdk) | Uses the Android and iOS SDKs through native bridges. |
| [Flutter](https://github.com/team-michael/notifly_flutter) | Uses the Android and iOS SDKs; Flutter Web uses the JavaScript SDK. |

The iOS repository hosts the Core binary on its own GitHub Releases. Customers do not need to select a separate KMP source version.

## Development

Requirements:

- JDK 17
- Node.js 22
- macOS with Xcode for Apple targets

Run the shared test suite:

```bash
./gradlew \
  jvmTest \
  jsNodeTest \
  iosSimulatorArm64Test \
  --no-daemon
```

## Kotlin compatibility

The build uses Kotlin `2.2.21`. Common and JVM code target Kotlin language/API `1.8` with stdlib `1.8.10` for Android compatibility. Kotlin/JS uses the stdlib matching the build compiler.

## Repository structure

```text
.
├── src/          # Shared Kotlin Multiplatform sources
├── scripts/      # Build and validation tools
├── smoke-tests/  # Consumer-level verification projects
└── .github/      # Automation workflows
```

## Links

- [Notifly](https://notifly.tech)
- [Documentation](https://docs.notifly.tech)
- [Issues](https://github.com/notifly-tech/notifly-kmp-sdk/issues)
- [MIT License](LICENSE)

# Notifly KMP SDK

Shared Kotlin Multiplatform implementation used internally by the Notifly Android, iOS, and JavaScript SDKs.

The repository contains one `:kmp` umbrella module. Platform SDKs keep their existing public APIs and delegate selected deterministic behavior to this module.

## Current scope

- `tech.notifly.kmp.identity.UserIdTransitionPolicy`
- JVM, Kotlin/JS IR, iOS device, and iOS simulator targets
- Static `NotiflyKMP.xcframework`

## Verify

```bash
./gradlew \
  :kmp:jvmTest \
  :kmp:jsNodeTest \
  :kmp:iosSimulatorArm64Test \
  --no-daemon
```

The Maven smoke test compiles and runs a consumer application with Kotlin `1.8.10`:

```bash
./gradlew :kmp:publishToMavenLocal --no-daemon
scripts/smoke-maven-local.sh
```

## Kotlin compatibility

The build uses Kotlin Gradle Plugin `2.2.21` for current Kotlin Multiplatform and Apple toolchain support. Published common and JVM code is restricted to Kotlin language/API version `1.8` and depends on Kotlin stdlib `1.8.10`, so the current Notifly Android SDK can consume it without upgrading from Kotlin `1.8.10`.

Kotlin/JS uses the stdlib matching the build compiler because the Kotlin/JS compiler requires it. This does not change the Kotlin requirement of the Android/JVM artifact.

## Versioning and host integration

Every host SDK pins an exact KMP tag commit as a git submodule. Moving branches and version ranges are not supported.

The KMP release verifies JVM, Kotlin/JS, and Apple outputs, creates an immutable Git tag and GitHub release, and opens submodule-bump pull requests in the Android, iOS, and JavaScript SDK repositories. The attached `NotiflyKMP.xcframework` is a validation artifact; the iOS SDK builds the framework again from its pinned source.

Customer artifacts are produced by the host SDK releases:

- Android builds the submodule project and publishes `tech.notifly:notifly-kmp-sdk:<android-sdk-version>` with the Android SDK to Maven Central.
- iOS builds a static `NotiflyKMP.xcframework` and links it into the final `notifly_sdk.xcframework`.
- JavaScript builds the Kotlin/JS package locally and inlines it into the final SDK bundles.

The KMP repository itself does not publish npm, CocoaPods, or JitPack packages. Application developers continue to install only the platform SDK they already use.

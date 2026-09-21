# Scripts

This directory contains scripts for preparing KMP SDK releases, validating build artifacts, and testing automation. These scripts are not part of the SDK runtime used by applications.

Run all commands below from the repository root. A smoke test checks that a built package can actually be used by a separate consumer program.

## Script reference

### Release preparation

| File | Purpose | Automated execution |
| --- | --- | --- |
| [prepare_release.sh](prepare_release.sh) | Builds the XCFramework and creates a ZIP archive and SHA-256 checksum file. Set `SKIP_BUILD=true` to use an existing build. Does not upload artifacts or publish packages. | CI and release workflows |
| [update_release_manifests.rb](update_release_manifests.rb) | Takes a version and ZIP checksum, then updates the download URL and checksum in `Package.swift` and the version and checksum in `NotiflyKMP.podspec`. | Release workflow when preparing a new tag |

### Build artifact validation

| File | Purpose | Automated execution |
| --- | --- | --- |
| [smoke-maven-local.sh](smoke-maven-local.sh) | Runs a separate JVM project against the KMP library installed in the local Maven repository. Checks compatibility with a Kotlin 1.8.10 consumer and exercises the public API. The test program lives in `smoke-tests/maven-consumer`. | CI and release workflows |
| [smoke-js-package.mjs](smoke-js-package.mjs) | Installs the built npm tarball into a temporary project. Checks for the license, type declarations, and source maps, exercises the public API in Node.js, and removes the temporary project. | CI and release workflows |
| [smoke-apple-artifacts.sh](smoke-apple-artifacts.sh) | Entry point for Apple artifact validation. Runs the Swift consumer test, then validates the CocoaPods package with `pod lib lint` in a temporary directory. | CI and release workflows |
| [smoke-swift-consumer.sh](smoke-swift-consumer.sh) | Compiles a Swift test program against the built XCFramework and runs it on an iPhone simulator. Removes temporary files and shuts down only a simulator it booted itself; an already-running simulator is left running. | Called by `smoke-apple-artifacts.sh` |
| [smoke-swift-consumer.swift](smoke-swift-consumer.swift) | Swift consumer test program. Checks the user ID transition API and popup behavior: the `static` outcome, renderer reuse, cancellation, duplicate callbacks, and native event parameter encoding with a local HTTP fixture. This is source code compiled by the shell script, not a standalone executable script. | Used by `smoke-swift-consumer.sh` |
| [smoke-host-packaging.sh](smoke-host-packaging.sh) | Checks that platform SDKs can build KMP with different package names, versions, and Apple framework names. Verifies Maven, npm, and Apple outputs, including the Apple dynamic framework format. Does not publish to public registries. | CI |

The Swift, JS, and JVM consumer tests are not end-to-end tests against the staging or production Render API. Their five-second callback wait limit is also separate from the SDK's request timeout configuration.

### Automation tests

| File | Purpose | Automated execution |
| --- | --- | --- |
| [test-release-contracts.rb](test-release-contracts.rb) | Reads workflow and configuration files to enforce release rules: package names, required verification steps, matching checksums, platform SDK update jobs, and the prohibition on publishing directly to npm, CocoaPods, or JitPack from this repository. | CI |
| [test-sdk-bump-workflow.rb](test-sdk-bump-workflow.rb) | Tests the SDK's KMP submodule update workflow using temporary Git repositories and a stub GitHub CLI. Validates release tags, target repositories, and PR file boundaries. Uses real npm with local fixtures to verify JS lockfile updates and failure handling. Does not modify real SDK repositories or create PRs. | CI |
| [test-swift-consumer-cleanup.rb](test-swift-consumer-cleanup.rb) | Uses a stub `xcrun` to test simulator cleanup in the Swift runner. Covers success, boot failure, boot-status wait failure, and use of an already-booted device. Does not boot a real simulator. | Manual only; not connected to CI |

## Prerequisites

- JVM builds: JDK 17 and the repository's Gradle Wrapper.
- JS package validation: Node.js 22 and npm. The script runs `npm install` inside a temporary project.
- Ruby tests: Ruby with the required libraries, including `yaml` and `minitest`. The SDK update tests also require Git, Node.js 22, and npm. Their npm fixtures run offline without registry downloads.
- Apple validation: macOS, Xcode 16 or later, CocoaPods, and an available iPhone simulator. The Swift consumer runner currently builds an `arm64` simulator binary.
- Initial builds and dependency installation may require network access.

## Running locally

### Run automation tests only

```bash
ruby scripts/test-release-contracts.rb
ruby scripts/test-sdk-bump-workflow.rb
ruby scripts/test-swift-consumer-cleanup.rb
```

These commands do not create real releases or SDK update PRs.

### Build platform packages and run consumer tests

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

`publishToMavenLocal` installs artifacts in the development machine's local Maven repository, not a public Maven registry. The `VERSION` passed to the Maven consumer must match the version used for the build.

To run only the Swift consumer test, build the XCFramework first, then run `scripts/smoke-swift-consumer.sh`. It is already included in `smoke-apple-artifacts.sh`, so there is no need to run both in sequence.

### Validate packaging under platform SDK names

```bash
scripts/smoke-host-packaging.sh
```

This test uses version `9.8.7` and the names `core`, `notifly-core-sdk`, and `NotiflyCore`. It does not change the actual SDK release version.

Warning: this script runs Gradle `clean`, removing existing build outputs before rebuilding under test-specific names. Do not run it concurrently with another build. Finish tests that need the default artifact names first, or rebuild those artifacts afterward.

### Prepare a release ZIP

```bash
KMP_SCRIPT_VERSION=0.1.0-alpha.1
VERSION="$KMP_SCRIPT_VERSION" scripts/prepare_release.sh "$KMP_SCRIPT_VERSION"
```

The outputs are `build/release/<version>/NotiflyKMP.xcframework.zip` and its `.zip.sha256` file. Add `SKIP_BUILD=true` if an XCFramework built for that version already exists.

Manifest updates are normally handled by the release workflow. If run manually, the following commands modify the repository's `Package.swift` and `NotiflyKMP.podspec` files.

```bash
KMP_SCRIPT_CHECKSUM="$(swift package compute-checksum "build/release/$KMP_SCRIPT_VERSION/NotiflyKMP.xcframework.zip")"
scripts/update_release_manifests.rb "$KMP_SCRIPT_VERSION" "$KMP_SCRIPT_CHECKSUM"
```

## Related files

- [CI workflow](../.github/workflows/ci.yml): Verification for PRs and pushes to main.
- [Release workflow](../.github/workflows/release.yml): Testing, packaging, GitHub releases, and platform SDK update PRs.
- [SDK submodule update workflow](../.github/workflows/bump-sdk-submodule.yml): Updates the KMP reference in each platform SDK. For JS, rebuilds Core and refreshes `package-lock.json` with npm 10.9.0 before opening the PR. Android and iOS PRs change only the submodule reference; no SDK is automatically merged or released.
- [JVM consumer project](../smoke-tests/maven-consumer): A separate test project that consumes the Maven artifact.
- [Browser HTTP fixture](../karma.config.d/popup-loopback-fixture.js): Local test response middleware outside `scripts/`, used by `jsBrowserTest` to verify CORS, cookies, redirects, and cancellation.

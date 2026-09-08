#!/usr/bin/env bash

set -euo pipefail

root_dir="$(cd "$(dirname "$0")/.." && pwd)"
stage_dir="$(mktemp -d "${TMPDIR:-/tmp}/notifly-kmp-host-packaging.XXXXXX")"
trap 'find "$stage_dir" -depth -delete' EXIT

host_group="com.github.team-michael.notifly-android-sdk"
host_version="9.8.7"
npm_package_name="notifly-core-sdk"
apple_framework_name="NotiflyCore"

GROUP="$host_group" \
VERSION="$host_version" \
MAVEN_ROOT_ARTIFACT_ID="core-metadata" \
MAVEN_JVM_ARTIFACT_ID="core" \
NPM_PACKAGE_NAME="$npm_package_name" \
APPLE_FRAMEWORK_NAME="$apple_framework_name" \
APPLE_FRAMEWORK_IS_STATIC="false" \
"$root_dir/gradlew" \
  clean \
  publishJvmPublicationToMavenLocal \
  packJsPackage \
  assembleNotiflyCoreReleaseXCFramework \
  "-Dmaven.repo.local=$stage_dir/m2" \
  --no-daemon

maven_jar="$stage_dir/m2/com/github/team-michael/notifly-android-sdk/core/$host_version/core-$host_version.jar"
npm_tarball="$root_dir/build/packages/$npm_package_name-$host_version.tgz"
apple_framework="$root_dir/build/XCFrameworks/release/$apple_framework_name.xcframework"
apple_binary="$apple_framework/ios-arm64/$apple_framework_name.framework/$apple_framework_name"

test -f "$maven_jar"
test -f "$npm_tarball"
test -d "$apple_framework"
test -f "$apple_binary"

mkdir -p "$stage_dir/npm"
tar -xzf "$npm_tarball" -C "$stage_dir/npm"
node -e '
  const manifest = require(process.argv[1]);
  if (manifest.name !== process.argv[2] || manifest.version !== process.argv[3]) {
    throw new Error(`unexpected npm identity: ${manifest.name}@${manifest.version}`);
  }
' "$stage_dir/npm/package/package.json" "$npm_package_name" "$host_version"

file "$apple_binary" | grep -F "dynamically linked shared library" >/dev/null

echo "Host-owned Core artifacts verified at version $host_version."

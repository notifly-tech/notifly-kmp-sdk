#!/usr/bin/env bash

set -euo pipefail

if [[ $# -ne 1 ]]; then
  echo "usage: $0 <version>" >&2
  exit 2
fi

version="$1"
root_dir="$(cd "$(dirname "$0")/.." && pwd)"
framework_dir="$root_dir/kmp/build/XCFrameworks/release/NotiflyKMP.xcframework"
release_dir="$root_dir/kmp/build/release/$version"
archive_path="$release_dir/NotiflyKMP.xcframework.zip"
checksum_path="$archive_path.sha256"
stage_dir="$release_dir/archive-root"

if [[ "${SKIP_BUILD:-false}" != "true" ]]; then
  "$root_dir/gradlew" :kmp:assembleNotiflyKMPReleaseXCFramework --no-daemon
fi

if [[ ! -d "$framework_dir" ]]; then
  echo "missing XCFramework: $framework_dir" >&2
  exit 1
fi

mkdir -p "$release_dir"
if [[ -e "$archive_path" ]]; then
  find "$archive_path" -depth -delete
fi
if [[ -e "$stage_dir" ]]; then
  find "$stage_dir" -depth -delete
fi
mkdir -p "$stage_dir"
cp -R "$framework_dir" "$stage_dir/"
find "$stage_dir" -exec touch -t 198001010000 {} +
(
  cd "$stage_dir"
  find NotiflyKMP.xcframework -print | LC_ALL=C sort | zip -X -q "$archive_path" -@
)
find "$stage_dir" -depth -delete

checksum="$(swift package compute-checksum "$archive_path")"
printf '%s  %s\n' "$checksum" "$(basename "$archive_path")" > "$checksum_path"
printf '%s\n' "$archive_path"
printf '%s\n' "$checksum"

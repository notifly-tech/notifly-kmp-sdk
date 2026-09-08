#!/usr/bin/env bash

set -euo pipefail

root_dir="$(cd "$(dirname "$0")/.." && pwd)"
framework_dir="$root_dir/build/XCFrameworks/release/NotiflyKMP.xcframework"
stage_dir="$(mktemp -d "${TMPDIR:-/tmp}/notifly-kmp-pod-lint.XXXXXX")"
trap 'find "$stage_dir" -depth -delete' EXIT

if [[ ! -d "$framework_dir" ]]; then
  echo "missing XCFramework: $framework_dir" >&2
  exit 1
fi

cp "$root_dir/NotiflyKMP.podspec" "$root_dir/LICENSE" "$stage_dir/"
cp -R "$framework_dir" "$stage_dir/"

pod lib lint "$stage_dir/NotiflyKMP.podspec" --allow-warnings --skip-tests

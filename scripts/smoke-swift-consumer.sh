#!/usr/bin/env bash

set -euo pipefail

root_dir="$(cd "$(dirname "$0")/.." && pwd)"
framework_slice="$root_dir/build/XCFrameworks/release/NotiflyKMP.xcframework/ios-arm64_x86_64-simulator"
framework="$framework_slice/NotiflyKMP.framework"
stage_dir="$(mktemp -d "${TMPDIR:-/tmp}/notifly-kmp-swift-smoke.XXXXXX")"
device_id=""
booted_by_smoke=false

cleanup() {
  if [[ "$booted_by_smoke" == "true" && -n "$device_id" ]]; then
    xcrun simctl shutdown "$device_id" >/dev/null 2>&1 || true
  fi
  find "$stage_dir" -depth -delete
}
trap cleanup EXIT

if [[ ! -d "$framework" ]]; then
  echo "missing simulator framework: $framework" >&2
  exit 1
fi

xcrun --sdk iphonesimulator swiftc \
  -target arm64-apple-ios14.0-simulator \
  -F "$framework_slice" \
  -framework NotiflyKMP \
  -Xlinker -rpath \
  -Xlinker @executable_path/Frameworks \
  "$root_dir/scripts/smoke-swift-consumer.swift" \
  -o "$stage_dir/notifly-kmp-swift-smoke"

mkdir -p "$stage_dir/Frameworks"
cp -R "$framework" "$stage_dir/Frameworks/"

device_record="$(
  xcrun simctl list devices available -j |
    ruby -rjson -e '
      devices = JSON.parse(STDIN.read).fetch("devices").values.flatten
      device = devices.find { |item| item["state"] == "Booted" && item["name"].start_with?("iPhone") } ||
        devices.find { |item| item["name"].start_with?("iPhone") }
      abort "no available iPhone simulator" unless device
      puts "#{device.fetch("udid")}\t#{device.fetch("state")}"
    '
)"
IFS=$'\t' read -r device_id device_state <<< "$device_record"
if [[ "$device_state" != "Booted" ]]; then
  xcrun simctl boot "$device_id"
  xcrun simctl bootstatus "$device_id" -b
  booted_by_smoke=true
fi

xcrun simctl spawn "$device_id" "$stage_dir/notifly-kmp-swift-smoke"
echo "Swift Popup consumer verified on simulator $device_id."

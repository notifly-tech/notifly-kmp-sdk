#!/usr/bin/env ruby

require 'fileutils'
require 'minitest/autorun'
require 'open3'
require 'tmpdir'

class SwiftConsumerCleanupTest < Minitest::Test
  ROOT = File.expand_path('..', __dir__)

  def setup
    @tmp = Dir.mktmpdir('notifly-swift-cleanup-')
    @project = File.join(@tmp, 'project')
    @log = File.join(@tmp, 'xcrun.log')
    @tmpdir = File.join(@tmp, 'tmp')
    framework = File.join(
      @project,
      'build/XCFrameworks/release/NotiflyKMP.xcframework/ios-arm64_x86_64-simulator/NotiflyKMP.framework'
    )
    FileUtils.mkdir_p([File.join(@project, 'scripts'), framework, @tmpdir, File.join(@tmp, 'bin')])
    FileUtils.cp(File.join(ROOT, 'scripts/smoke-swift-consumer.sh'), File.join(@project, 'scripts'))
    FileUtils.cp(File.join(ROOT, 'scripts/smoke-swift-consumer.swift'), File.join(@project, 'scripts'))
    write_xcrun_stub
  end

  def teardown
    FileUtils.remove_entry(@tmp)
  end

  def test_success_shuts_down_the_owned_device
    success, output, calls = run_smoke('success')
    assert success, output
    assert_order calls, 'simctl boot FIXTURE-DEVICE', 'simctl bootstatus FIXTURE-DEVICE -b',
                 'simctl spawn FIXTURE-DEVICE', 'simctl shutdown FIXTURE-DEVICE'
  end

  def test_boot_failure_does_not_claim_the_device
    success, _output, calls = run_smoke('boot_failure')
    refute success
    assert_includes calls, 'simctl boot FIXTURE-DEVICE'
    refute calls.any? { |call| call.start_with?('simctl bootstatus ', 'simctl spawn ', 'simctl shutdown ') }
  end

  def test_bootstatus_failure_still_shuts_down_the_owned_device
    success, _output, calls = run_smoke('bootstatus_failure')
    refute success
    assert_order calls, 'simctl boot FIXTURE-DEVICE', 'simctl bootstatus FIXTURE-DEVICE -b',
                 'simctl shutdown FIXTURE-DEVICE'
    refute calls.any? { |call| call.start_with?('simctl spawn ') }
  end

  def test_already_booted_device_is_not_owned_or_shut_down
    success, output, calls = run_smoke('already_booted')
    assert success, output
    refute calls.any? { |call| call.start_with?('simctl boot ', 'simctl bootstatus ', 'simctl shutdown ') }
    assert calls.any? { |call| call.start_with?('simctl spawn FIXTURE-DEVICE ') }
  end

  private

  def run_smoke(scenario)
    FileUtils.rm_f(@log)
    env = {
      'PATH' => "#{File.join(@tmp, 'bin')}:#{ENV.fetch('PATH')}",
      'TMPDIR' => @tmpdir,
      'XCRUN_LOG' => @log,
      'XCRUN_SCENARIO' => scenario,
    }
    out, err, status = Open3.capture3(env, 'bash', File.join(@project, 'scripts/smoke-swift-consumer.sh'))
    calls = File.exist?(@log) ? File.readlines(@log, chomp: true) : []
    [status.success?, out + err, calls]
  end

  def assert_order(calls, *expected)
    positions = expected.map do |entry|
      calls.index { |call| call == entry || call.start_with?("#{entry} ") }
    end
    refute_includes positions, nil, "missing calls in #{calls.inspect}"
    assert_equal positions.sort, positions, "calls out of order: #{calls.inspect}"
  end

  def write_xcrun_stub
    path = File.join(@tmp, 'bin/xcrun')
    File.write(path, XCRUN_STUB)
    FileUtils.chmod(0o755, path)
  end

  XCRUN_STUB = <<~'SH'
    #!/usr/bin/env bash
    set -euo pipefail
    printf '%s\n' "$*" >> "$XCRUN_LOG"
    if [[ "$1" == "--sdk" ]]; then
      while [[ $# -gt 0 ]]; do
        if [[ "$1" == "-o" ]]; then shift; : > "$1"; exit 0; fi
        shift
      done
      exit 2
    fi
    case "$2" in
      list)
        state="Shutdown"
        if [[ "$XCRUN_SCENARIO" == "already_booted" ]]; then state="Booted"; fi
        printf '{"devices":{"fixture":[{"state":"%s","name":"iPhone Fixture","udid":"FIXTURE-DEVICE"}]}}\n' "$state"
        ;;
      boot)
        if [[ "$XCRUN_SCENARIO" == "boot_failure" ]]; then exit 31; fi
        ;;
      bootstatus)
        if [[ "$XCRUN_SCENARIO" == "bootstatus_failure" ]]; then exit 32; fi
        ;;
      spawn|shutdown) ;;
      *) exit 3 ;;
    esac
  SH
end

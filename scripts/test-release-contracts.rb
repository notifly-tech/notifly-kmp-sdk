#!/usr/bin/env ruby

require "yaml"

root = File.expand_path("..", __dir__)

def assert_contract(condition, message)
  abort "release contract failed: #{message}" unless condition
end

workflow = File.read(File.join(root, ".github/workflows/release.yml"))
config = YAML.safe_load(workflow, aliases: true)
jobs = config.fetch("jobs")
preflight = jobs.fetch("preflight")
release = jobs.fetch("release")
steps = release.fetch("steps")
step_names = steps.map { |step| step["name"] }.compact
ci = YAML.safe_load(File.read(File.join(root, ".github/workflows/ci.yml")), aliases: true)
ci_steps = ci.fetch("jobs").fetch("verify").fetch("steps")
wrapper = File.read(File.join(root, "gradle/wrapper/gradle-wrapper.properties"))
package = File.read(File.join(root, "Package.swift"))
podspec = File.read(File.join(root, "NotiflyKMP.podspec"))
updater = File.read(File.join(root, "scripts/update_release_manifests.rb"))
gradle_build = File.read(File.join(root, "kmp/build.gradle.kts"))
js_smoke_test = File.read(File.join(root, "scripts/smoke-js-package.mjs"))
maven_smoke_build = File.read(File.join(root, "smoke-tests/maven-consumer/build.gradle.kts"))

assert_contract(workflow.include?("ref: main"), "release checkout must be pinned to main")
assert_contract(workflow.include?("id: release-state"), "release workflow must detect existing GitHub releases")
assert_contract(workflow.include?('gh release view "$tag"'), "GitHub release state must be resumable")
assert_contract(!workflow.include?("Publish npm prerelease"), "KMP source releases must not publish npm packages")
assert_contract(!workflow.include?("Publish CocoaPods prerelease"), "KMP source releases must not publish CocoaPods packages")
assert_contract(!workflow.include?("Trigger and verify JitPack"), "KMP source releases must not publish through JitPack")
assert_contract(!workflow.match?(/\bnpm publish\b/), "KMP release must not invoke npm publish")
assert_contract(!workflow.match?(/\bpod trunk push\b/), "KMP release must not invoke CocoaPods trunk")
assert_contract(!workflow.include?("jitpack.io"), "KMP release must not depend on JitPack")
assert_contract(!File.exist?(File.join(root, "jitpack.yml")), "KMP source repository must not expose a JitPack build")

permissions = config.fetch("permissions")
assert_contract(permissions["contents"] == "write", "release workflow must create tags and releases")
assert_contract(!permissions.key?("id-token"), "KMP source release no longer needs npm OIDC")
assert_contract(release["needs"] == "preflight", "release must validate cross-repository dispatch credentials first")
assert_contract(preflight.to_s.include?("${{ secrets.SDK_REPO_TOKEN }}"), "preflight must validate the SDK dispatch token")

{
  "bump-android" => "team-michael/notifly-android-sdk",
  "bump-ios" => "team-michael/notifly-ios-sdk",
  "bump-js" => "team-michael/notifly-js-sdk",
}.each do |job_name, repository|
  job = jobs.fetch(job_name)
  assert_contract(job["needs"] == "release", "#{job_name} must wait for the KMP release")
  job_text = job.to_s
  assert_contract(job_text.include?("gh workflow run bump-kmp-submodule.yml"), "#{job_name} must dispatch its SDK-owned workflow")
  assert_contract(job_text.include?(repository), "#{job_name} must target the correct SDK repository")
  assert_contract(job_text.include?("${{ secrets.SDK_REPO_TOKEN }}"), "#{job_name} must use the cross-repository dispatch token")
end

assert_contract(step_names.include?("Test and build"), "release must test every KMP target")
assert_contract(step_names.include?("Smoke-test artifacts"), "release must smoke-test host build inputs")
assert_contract(step_names.include?("Create GitHub release"), "release must publish the source release")
assert_contract(workflow.include?('NotiflyKMP.xcframework.zip.sha256'), "release must attach the XCFramework SHA-256 file")
assert_contract(step_names.include?("Restore checksum asset on an existing release"), "resumed releases must restore the checksum asset")
assert_contract(gradle_build.include?("github.com/notifly-tech/notifly-kmp-sdk"), "local JS package metadata must use the notifly-tech repository")
assert_contract(gradle_build.include?('artifactId = if') && gradle_build.include?('"notifly-kmp-sdk"'), "KMP root publication must use the official artifact ID")
assert_contract(gradle_build.include?("CENTRAL_STAGING_REPOSITORY"), "Android releases must be able to stage the KMP Maven publications")
assert_contract(maven_smoke_build.include?('implementation("tech.notifly:notifly-kmp-sdk:$notiflyKmpVersion")'), "Kotlin 1.8.10 smoke test must consume the official coordinate")
assert_contract(package.include?("github.com/notifly-tech/notifly-kmp-sdk"), "Swift package metadata must use the notifly-tech repository")
assert_contract(podspec.include?("github.com/notifly-tech/notifly-kmp-sdk"), "podspec metadata must use the notifly-tech repository")
assert_contract(js_smoke_test.include?('require("notifly-kmp-sdk")'), "local JS package smoke test must load the generated package")

ci_release_contract = ci_steps.find { |step| step["name"] == "Verify release contracts" }
assert_contract(ci_release_contract&.fetch("run", nil) == "ruby scripts/test-release-contracts.rb", "CI must enforce release contracts")

expected_wrapper_checksum = "d725d707bfabd4dfdc958c624003b3c80accc03f7037b5122c4b1d0ef15cecab"
assert_contract(wrapper.include?("distributionSha256Sum=#{expected_wrapper_checksum}"), "Gradle 8.9 distribution checksum must be pinned")

swift_checksum = package[/checksum: "([0-9a-f]{64})"/, 1]
pod_checksum = podspec[/:sha256 => "([0-9a-f]{64})"/, 1]
assert_contract(swift_checksum == pod_checksum, "SwiftPM and CocoaPods checksums must match")
assert_contract(updater.include?("podspec.sub!(/:sha256"), "manifest updater must update the CocoaPods checksum")

puts "release contracts verified"

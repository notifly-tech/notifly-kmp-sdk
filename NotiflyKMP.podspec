Pod::Spec.new do |spec|
  spec.name = "NotiflyKMP"
  spec.version = "0.1.0"
  spec.summary = "Shared Kotlin Multiplatform implementation used by the Notifly SDKs."
  spec.homepage = "https://github.com/notifly-tech/notifly-kmp-sdk"
  spec.license = { :type => "MIT" }
  spec.author = { "Notifly" => "engineering@notifly.tech" }
  spec.source = {
    :http => "https://github.com/notifly-tech/notifly-kmp-sdk/releases/download/v#{spec.version}/NotiflyKMP.xcframework.zip",
    :sha256 => "e3bc13efc776e7bab71307b29db656ea97ea383d9b7273509b260006e3cdbed5"
  }
  spec.ios.deployment_target = "15.0"
  spec.vendored_frameworks = "NotiflyKMP.xcframework"
end

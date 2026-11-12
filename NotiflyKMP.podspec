Pod::Spec.new do |spec|
  spec.name = "NotiflyKMP"
  spec.version = "0.1.2"
  spec.summary = "Shared Kotlin Multiplatform implementation used by the Notifly SDKs."
  spec.homepage = "https://github.com/notifly-tech/notifly-kmp-sdk"
  spec.license = { :type => "MIT" }
  spec.author = { "Notifly" => "engineering@notifly.tech" }
  spec.source = {
    :http => "https://github.com/notifly-tech/notifly-kmp-sdk/releases/download/v#{spec.version}/NotiflyKMP.xcframework.zip",
    :sha256 => "350282a21dc872ede1b4333a3048b1f914b5b2ab2c147ed03dc31ea3297d4c7a"
  }
  spec.ios.deployment_target = "15.0"
  spec.vendored_frameworks = "NotiflyKMP.xcframework"
end

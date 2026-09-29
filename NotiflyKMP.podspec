Pod::Spec.new do |spec|
  spec.name = "NotiflyKMP"
  spec.version = "0.1.1"
  spec.summary = "Shared Kotlin Multiplatform implementation used by the Notifly SDKs."
  spec.homepage = "https://github.com/notifly-tech/notifly-kmp-sdk"
  spec.license = { :type => "MIT" }
  spec.author = { "Notifly" => "engineering@notifly.tech" }
  spec.source = {
    :http => "https://github.com/notifly-tech/notifly-kmp-sdk/releases/download/v#{spec.version}/NotiflyKMP.xcframework.zip",
    :sha256 => "482552fae9c160dcfee32c6e24827960d8de96c46ca4e50cc64d922b9f031773"
  }
  spec.ios.deployment_target = "15.0"
  spec.vendored_frameworks = "NotiflyKMP.xcframework"
end

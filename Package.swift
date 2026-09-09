// swift-tools-version: 5.9

import PackageDescription

let package = Package(
    name: "NotiflyKMP",
    platforms: [
        .iOS(.v15),
    ],
    products: [
        .library(
            name: "NotiflyKMP",
            targets: ["NotiflyKMP"]
        ),
    ],
    targets: [
        .binaryTarget(
            name: "NotiflyKMP",
            url: "https://github.com/notifly-tech/notifly-kmp-sdk/releases/download/v0.1.0-alpha.4/NotiflyKMP.xcframework.zip",
            checksum: "9550286af4b614e3cb9fe308f3a208d03ab77290c897c48d55a88aee71b9715c"
        ),
    ]
)

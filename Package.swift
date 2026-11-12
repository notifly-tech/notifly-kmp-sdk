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
            url: "https://github.com/notifly-tech/notifly-kmp-sdk/releases/download/v0.1.2/NotiflyKMP.xcframework.zip",
            checksum: "350282a21dc872ede1b4333a3048b1f914b5b2ab2c147ed03dc31ea3297d4c7a"
        ),
    ]
)

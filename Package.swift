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
            url: "https://github.com/notifly-tech/notifly-kmp-sdk/releases/download/v0.1.0/NotiflyKMP.xcframework.zip",
            checksum: "e3bc13efc776e7bab71307b29db656ea97ea383d9b7273509b260006e3cdbed5"
        ),
    ]
)

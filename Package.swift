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
            url: "https://github.com/notifly-tech/notifly-kmp-sdk/releases/download/v0.1.0-alpha.2/NotiflyKMP.xcframework.zip",
            checksum: "8ecdf864495dbd325a65098caf3f02cd60f07df61a9b2abda1b1eef4c9a87412"
        ),
    ]
)

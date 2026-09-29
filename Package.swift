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
            url: "https://github.com/notifly-tech/notifly-kmp-sdk/releases/download/v0.1.1/NotiflyKMP.xcframework.zip",
            checksum: "482552fae9c160dcfee32c6e24827960d8de96c46ca4e50cc64d922b9f031773"
        ),
    ]
)

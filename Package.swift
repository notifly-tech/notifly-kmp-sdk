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
            url: "https://github.com/notifly-tech/notifly-kmp-sdk/releases/download/v0.1.0-alpha.3/NotiflyKMP.xcframework.zip",
            checksum: "3035e3910759d1eb3e3779484033a0b97f33efa8306e2d2ee0454a4239789f07"
        ),
    ]
)

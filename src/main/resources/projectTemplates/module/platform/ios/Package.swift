// swift-tools-version: 5.9

import PackageDescription

let package = Package(
    name: "Template-Module",
    platforms: [
        .iOS(.v13),
    ],
    products: [
        .library(
            name: "TemplateModule",
            targets: ["TemplateModule"]
        ),
    ],
    targets: [
        .target(name: "TemplateModule"),
    ]
)

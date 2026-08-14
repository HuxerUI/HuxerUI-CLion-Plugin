# HuxerUI for CLion

HuxerUI for CLion adds project creation, SDK management, resource navigation, device selection, running, and
cross-platform build actions to CLion.

## Features

- Create an application through the current HuxerUI CLI or scaffold a compile-time HuxerUI module.
- Download the latest host SDK from the `HuxerUI/HuxerUI` GitHub Release, verify its GitHub SHA-256 digest, and
  install it below `~/.huxerui/sdk`.
- Navigate `strings::name`, `images::name`, and `raw::name` references to their source resources instead of the
  generated resource header. Namespace-qualified references such as `app::strings::name` are supported. Multiple
  locale or image variants are presented by CLion as navigation targets.
- Select a ready Android device, an iOS Simulator or physical device, the current Windows or macOS computer, or
  Chrome before Run.
- Build Debug or Release artifacts for every platform enabled by the project from the Build | HuxerUI menu.

The application surface follows the current CLI exactly. Its platform registry currently contains Android, iOS,
Windows, macOS, and Web. Linux is available as a HuxerUI backend and module platform directory, but it is not yet an
application driver in the upstream CLI.

## Requirements

- CLion 2025.3 through 2026.2.
- A JDK 25 installation for building this plugin. Plugin bytecode targets Java 21.
- The native tools required by the selected HuxerUI platforms.

Configure an existing SDK or source checkout in Settings | Tools | HuxerUI. A source checkout must have a built CLI
at `build/bin/huxerui` or another supported CLion CMake build path.

## SDK release contract

Tools | HuxerUI | Install HuxerUI SDK reads the latest release from `HuxerUI/HuxerUI`. It selects a host archive named
like `huxerui-sdk-<version>-<os>-<architecture>.zip` or `.tar.gz`, requires GitHub's `sha256:` asset digest, rejects
unsafe archive paths, and validates the standard headers, CMake package, and CLI layout before activation. Network
requests explicitly use CLion's configured proxy.

If the upstream repository has no matching SDK Release, point the plugin at a built HuxerUI source checkout in
Settings | Tools | HuxerUI.

## Build

To compile and test against an installed CLion:

```bash
./gradlew -PclionPath=/path/to/clion test buildPlugin
```

Without `clionPath`, Gradle downloads CLion 2025.3 for the compatibility baseline.

The packaged plugin is written to `build/distributions/`.

## License

[MIT](LICENSE)

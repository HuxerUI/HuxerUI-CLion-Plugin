# HuxerUI for CLion

HuxerUI for CLion adds project creation, SDK management, resource navigation, device selection, running, and
cross-platform build actions to CLion.

## Features

- Create an application or compile-time module through the current HuxerUI CLI from CLion's New Project wizard,
  including the Welcome screen, a dedicated HuxerUI category, and separate HuxerUI App and HuxerUI Module entries.
- Set an optional reverse-domain Application ID that the CLI projects to Android package names and Apple bundle IDs.
- Configure CLion only for selected platforms: the current desktop host receives a native CMake profile, Web receives
  an Emscripten profile, and Gradle- or Xcode-owned mobile projects do not trigger an unrelated host configuration.
- Download the latest host SDK from the `HuxerUI/HuxerUI` GitHub Release, verify its GitHub SHA-256 digest, and
  install it below `~/.huxerui/sdk`.
- Navigate `strings::name`, `images::name`, and `raw::name` references to their source resources instead of the
  generated resource header. Namespace-qualified references such as `app::strings::name` are supported. Multiple
  locale or image variants are presented by CLion as navigation targets.
- Select a ready Android device, an iOS Simulator or physical device, the current Windows, macOS, or Linux computer,
  or Chrome before Run.
- Make Chrome the selected HuxerUI run target for new and existing Web-only projects so CLion invokes
  `huxerui run web` instead of trying to execute the generated `.mjs` file as a native program.
- Build Debug or Release artifacts for every platform enabled by the project from the Build | HuxerUI menu.

The application surface follows the current CLI exactly. Its platform registry contains Android, iOS, Windows,
macOS, Linux, and Web.

## Requirements

- CLion 2025.3 through 2026.2.
- A JDK 25 installation for building this plugin. Plugin bytecode targets Java 21.
- The native tools required by the selected HuxerUI platforms.

Configure an existing SDK or source checkout in Settings | Tools | HuxerUI. A source checkout must have a built CLI
at `build/bin/huxerui` or another supported CLion CMake build path. The configured directory is exported as
`HUXERUI_HOME` to the CLI and CLion CMake profiles.

Create a project from the Welcome screen with New Project | HuxerUI | HuxerUI App or HuxerUI Module, or use the same
HuxerUI category under File | New Project while a project is open. The App page provides an optional Application ID
field and both entries provide platform checkboxes below the project location. CLion owns the new project lifecycle,
while the plugin reports generation phases and CLI output in the standard cancellable project-creation progress
dialog.
Generated applications use `src/app.cpp` plus the selected native entry points such as `platform/linux/main.cpp`.
Generated modules include the CLI-owned `examples/preview` application and selected native module packages.
Web project creation requires `em-config` on `PATH` so the plugin can locate Emscripten's CMake toolchain file.

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

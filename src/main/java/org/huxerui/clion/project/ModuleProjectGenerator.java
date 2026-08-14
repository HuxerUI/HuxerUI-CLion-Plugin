package org.huxerui.clion.project;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

public final class ModuleProjectGenerator {
    private ModuleProjectGenerator() {}

    public static Path Create(Path parent, String project_name) throws IOException {
        if (!project_name.matches("[A-Za-z][A-Za-z0-9_-]*")) {
            throw new IllegalArgumentException(
                    "Module name must start with a letter and contain only letters, digits, underscores, or hyphens"
            );
        }
        Path destination = parent.resolve(project_name);
        if (Files.exists(destination)) {
            throw new IOException("Destination already exists: " + destination);
        }

        String target = project_name.toLowerCase(Locale.ROOT).replace('-', '_');
        Path temporary = parent.resolve("." + project_name + ".huxerui-module-tmp-" + System.nanoTime());
        try {
            Files.createDirectories(temporary.resolve("include").resolve(target));
            Files.createDirectories(temporary.resolve("src"));
            for (String kind : List.of("images", "strings", "raw")) {
                Files.createDirectories(temporary.resolve("resources").resolve(kind));
            }
            for (String platform : List.of("android", "ios", "macos", "windows", "linux", "web")) {
                Files.createDirectories(temporary.resolve("platform").resolve(platform));
            }

            Files.writeString(temporary.resolve(".gitignore"), "/build/\n/cmake-build-*/\n/.cache/\n/.idea/\n");
            Files.writeString(temporary.resolve("CMakeLists.txt"), CMakeLists(target));
            Files.writeString(temporary.resolve("include").resolve(target).resolve("module.h"), Header(target));
            Files.writeString(temporary.resolve("src/module.cpp"), Source(target));
            Files.writeString(
                    temporary.resolve("resources/strings/default.properties"),
                    "module_name = \"" + project_name + "\"\n"
            );
            Files.writeString(temporary.resolve("README.md"), Readme(project_name, target));
            Files.move(temporary, destination);
            return destination;
        } catch (IOException | RuntimeException error) {
            try {
                DeleteTree(temporary);
            } catch (IOException cleanup_error) {
                error.addSuppressed(cleanup_error);
            }
            throw error;
        }
    }

    private static String CMakeLists(String target) {
        return """
                cmake_minimum_required(VERSION 3.20)
                project(%s VERSION 0.1.0 LANGUAGES CXX)

                set(CMAKE_CXX_STANDARD 20)
                set(CMAKE_CXX_STANDARD_REQUIRED ON)
                set(CMAKE_CXX_EXTENSIONS OFF)

                set(HUXERUI_SDK_ROOT "$ENV{HUXERUI_SDK_ROOT}" CACHE PATH "HuxerUI SDK or source directory")
                if (HUXERUI_SDK_ROOT AND EXISTS "${HUXERUI_SDK_ROOT}/CMakeLists.txt"
                        AND EXISTS "${HUXERUI_SDK_ROOT}/include/huxerui/huxerui.h")
                    set(HUXERUI_BUILD_TESTS OFF CACHE BOOL "" FORCE)
                    set(HUXERUI_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
                    add_subdirectory("${HUXERUI_SDK_ROOT}" "${CMAKE_BINARY_DIR}/huxerui-sdk" EXCLUDE_FROM_ALL)
                else ()
                    find_package(HuxerUI CONFIG REQUIRED
                            PATHS "${HUXERUI_SDK_ROOT}"
                            NO_DEFAULT_PATH
                            NO_CMAKE_FIND_ROOT_PATH
                    )
                endif ()

                file(GLOB_RECURSE MODULE_SOURCE_FILES CONFIGURE_DEPENDS
                        "${CMAKE_CURRENT_SOURCE_DIR}/src/*.cpp"
                        "${CMAKE_CURRENT_SOURCE_DIR}/src/*.cc"
                        "${CMAKE_CURRENT_SOURCE_DIR}/src/*.cxx"
                )

                huxerui_add_module(%s
                        SOURCES
                            ${MODULE_SOURCE_FILES}
                )
                target_include_directories(%s PUBLIC
                        "$<BUILD_INTERFACE:${CMAKE_CURRENT_SOURCE_DIR}/include>"
                        "$<INSTALL_INTERFACE:include>"
                )
                huxerui_add_resources(%s
                        ROOT "${CMAKE_CURRENT_SOURCE_DIR}/resources"
                        NAMESPACE %s
                )
                add_library(HuxerUI::%s ALIAS %s)
                """.formatted(target, target, target, target, target, target, target);
    }

    private static String Header(String target) {
        return """
                #pragma once

                #include <huxerui/view.h>

                namespace %s {

                huxerui::View Content();

                } // namespace %s
                """.formatted(target, target);
    }

    private static String Source(String target) {
        return """
                #include <%s/module.h>

                #include <huxerui/text.h>

                #include <%s_resources.h>

                namespace %s {

                huxerui::View Content() {
                  return huxerui::Text(strings::module_name);
                }

                } // namespace %s
                """.formatted(target, target, target, target);
    }

    private static String Readme(String project_name, String target) {
        return """
                # %s

                A HuxerUI compile-time module generated by the HuxerUI CLion plugin.

                Link the module from an application with `huxerui_use_module`:

                ```cmake
                huxerui_use_module(my_app
                        TARGET HuxerUI::%s
                        PATH "/path/to/%s"
                )
                ```
                """.formatted(project_name, target, project_name);
    }

    private static void DeleteTree(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted((left, right) -> right.getNameCount() - left.getNameCount()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}

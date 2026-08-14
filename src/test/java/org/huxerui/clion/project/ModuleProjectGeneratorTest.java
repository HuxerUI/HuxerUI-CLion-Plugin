package org.huxerui.clion.project;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class ModuleProjectGeneratorTest {
    @Test
    void createsCurrentModuleLayout(@TempDir Path parent) throws Exception {
        Path module = ModuleProjectGenerator.Create(parent, "HuxerUI-Camera");

        assertTrue(Files.readString(module.resolve("CMakeLists.txt")).contains("huxerui_add_module(huxerui_camera"));
        assertTrue(Files.isRegularFile(module.resolve("include/huxerui_camera/module.h")));
        assertTrue(Files.isRegularFile(module.resolve("resources/strings/default.properties")));
        assertTrue(Files.isDirectory(module.resolve("platform/android")));
        assertTrue(Files.isDirectory(module.resolve("platform/web")));
    }
}

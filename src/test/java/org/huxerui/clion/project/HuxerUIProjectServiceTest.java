package org.huxerui.clion.project;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

final class HuxerUIProjectServiceTest {
    @Test
    void recognizesCurrentApplicationLayout(@TempDir Path root) throws Exception {
        CreateApplication(root);

        assertEquals(root, HuxerUIProjectService.FindApplicationRoot(root));
    }

    @Test
    void resolvesModulePreviewApplication(@TempDir Path root) throws Exception {
        Path preview = root.resolve("examples/preview");
        CreateApplication(preview);

        assertEquals(preview, HuxerUIProjectService.FindApplicationRoot(root));
    }

    @Test
    void rejectsTheRemovedMainSourceLayout(@TempDir Path root) throws Exception {
        Files.createDirectories(root.resolve("src"));
        Files.createDirectories(root.resolve("platform/linux"));
        Files.createFile(root.resolve("CMakeLists.txt"));
        Files.createFile(root.resolve("src/main.cpp"));

        assertNull(HuxerUIProjectService.FindApplicationRoot(root));
    }

    @Test
    void rejectsFrameworkOrUnrelatedLookalikeLayouts(@TempDir Path root) throws Exception {
        Files.createDirectories(root.resolve("src"));
        Files.createDirectories(root.resolve("platform/linux"));
        Files.createDirectories(root.resolve("resources"));
        Files.createFile(root.resolve("CMakeLists.txt"));
        Files.createFile(root.resolve("src/app.cpp"));

        assertNull(HuxerUIProjectService.FindApplicationRoot(root));
    }

    private static void CreateApplication(Path root) throws Exception {
        Files.createDirectories(root.resolve("src"));
        Files.createDirectories(root.resolve("platform/linux"));
        Files.createFile(root.resolve("CMakeLists.txt"));
        Files.createFile(root.resolve("src/app.cpp"));
        Files.createFile(root.resolve("platform/linux/huxerui.cmake"));
    }
}

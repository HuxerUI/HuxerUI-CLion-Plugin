package org.huxerui.clion.project;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class HuxerUIProjectServiceTest {
    @Test
    void recognizesApplicationsFromTheirCMakeCommand(@TempDir Path root) throws Exception {
        WriteCMake(root, "huxerui_add_app(sample SOURCES src/custom.cpp)\n");

        assertEquals(HuxerUIProjectService.ProjectKind.APPLICATION, HuxerUIProjectService.FindProjectKind(root));
        assertEquals(root, HuxerUIProjectService.FindApplicationRoot(root));
    }

    @Test
    void recognizesModulesAndResolvesTheirPreviewApplication(@TempDir Path root) throws Exception {
        WriteCMake(root, "HUXERUI_ADD_MODULE(camera SOURCES src/camera.cpp)\n");
        Path preview = root.resolve("examples/preview");
        WriteCMake(preview, "huxerui_add_app(camera_preview SOURCES src/preview.cpp)\n");

        assertEquals(HuxerUIProjectService.ProjectKind.MODULE, HuxerUIProjectService.FindProjectKind(root));
        assertEquals(preview, HuxerUIProjectService.FindApplicationRoot(root));
        assertEquals(root, HuxerUIProjectService.FindResourceRoot(root, root.resolve("src/camera.cpp")));
        assertEquals(preview, HuxerUIProjectService.FindResourceRoot(root, preview.resolve("src/preview.cpp")));
    }

    @Test
    void recognizesModulesEvenBeforeTheyHaveAPreviewShell(@TempDir Path root) throws Exception {
        WriteCMake(root, "huxerui_add_module(camera)\n");

        assertEquals(HuxerUIProjectService.ProjectKind.MODULE, HuxerUIProjectService.FindProjectKind(root));
        assertNull(HuxerUIProjectService.FindApplicationRoot(root));
    }

    @Test
    void ignoresCommandNamesInCommentsAndCMakeStrings(@TempDir Path root) throws Exception {
        WriteCMake(root, """
                # huxerui_add_app(commented)
                set(TEXT "huxerui_add_module(quoted)")
                #[=[ huxerui_add_app(bracket_comment) ]=]
                set(BRACKET [=[huxerui_add_module(bracket_argument)]=])
                """);

        assertEquals(HuxerUIProjectService.ProjectKind.NONE, HuxerUIProjectService.FindProjectKind(root));
    }

    @Test
    void doesNotUseGeneratedCachesOrLookalikeDirectoriesForRecognition(@TempDir Path root) throws Exception {
        Files.createDirectories(root.resolve(".huxerui/build"));
        Files.createDirectories(root.resolve("src"));
        Files.createDirectories(root.resolve("platform/linux"));
        Files.createDirectories(root.resolve("resources"));
        WriteCMake(root, "add_executable(sample src/app.cpp)\n");

        assertEquals(HuxerUIProjectService.ProjectKind.NONE, HuxerUIProjectService.FindProjectKind(root));
        assertNull(HuxerUIProjectService.FindApplicationRoot(root));
        assertTrue(Files.isDirectory(root.resolve(".huxerui")));
    }

    private static void WriteCMake(Path root, String content) throws Exception {
        Files.createDirectories(root);
        Files.writeString(root.resolve("CMakeLists.txt"), content);
    }
}

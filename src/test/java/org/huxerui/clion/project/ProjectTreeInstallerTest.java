package org.huxerui.clion.project;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ProjectTreeInstallerTest {
    @Test
    void installsGeneratedFilesIntoWizardProject(@TempDir Path temporary) throws Exception {
        Path generated = temporary.resolve("generated");
        Path destination = temporary.resolve("project");
        Files.createDirectories(generated.resolve("src"));
        Files.writeString(generated.resolve("CMakeLists.txt"), "project(sample)\n");
        Files.writeString(generated.resolve("src/app.cpp"), "const auto application = 1;\n");
        Files.createDirectories(destination.resolve(".idea"));
        Files.writeString(destination.resolve(".idea/.name"), "sample\n");

        ProjectTreeInstaller.Install(generated, destination);

        assertTrue(Files.isRegularFile(destination.resolve("CMakeLists.txt")));
        assertTrue(Files.isRegularFile(destination.resolve("src/app.cpp")));
        assertEquals("sample\n", Files.readString(destination.resolve(".idea/.name")));
    }

    @Test
    void rejectsCollisionsBeforeMovingAnything(@TempDir Path temporary) throws Exception {
        Path generated = temporary.resolve("generated");
        Path destination = temporary.resolve("project");
        Files.createDirectories(generated.resolve("src"));
        Files.writeString(generated.resolve("CMakeLists.txt"), "generated\n");
        Files.createDirectories(destination);
        Files.writeString(destination.resolve("CMakeLists.txt"), "existing\n");

        assertThrows(IOException.class, () -> ProjectTreeInstaller.Install(generated, destination));

        assertEquals("existing\n", Files.readString(destination.resolve("CMakeLists.txt")));
        assertTrue(Files.isDirectory(generated.resolve("src")));
        assertEquals("generated\n", Files.readString(generated.resolve("CMakeLists.txt")));
    }
}

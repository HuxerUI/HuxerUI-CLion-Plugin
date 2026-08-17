package org.huxerui.clion.settings;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class HuxerUISettingsTest {
    @Test
    void acceptsSourceCheckoutWithBuiltCli(@TempDir Path home) throws Exception {
        Files.createDirectories(home.resolve("include/huxerui"));
        Files.createDirectories(home.resolve("cmake"));
        Files.createDirectories(home.resolve("build/bin"));
        Files.createFile(home.resolve("include/huxerui/huxerui.h"));
        Files.createFile(home.resolve("cmake/HuxerUIApp.cmake"));
        Path cli = Files.createFile(home.resolve("build/bin").resolve(CliName()));

        assertTrue(HuxerUISettings.SdkLayout.IsValid(home));
        assertEquals(cli, HuxerUISettings.SdkLayout.FindCli(home));
    }

    @Test
    void acceptsMultiArchInstalledCMakeLayout(@TempDir Path home) throws Exception {
        Files.createDirectories(home.resolve("include/huxerui"));
        Files.createDirectories(home.resolve("lib/x86_64-linux-gnu/cmake/HuxerUI"));
        Files.createDirectories(home.resolve("bin"));
        Files.createFile(home.resolve("include/huxerui/huxerui.h"));
        Files.createFile(home.resolve("lib/x86_64-linux-gnu/cmake/HuxerUI/HuxerUIConfig.cmake"));
        Path cli = Files.createFile(home.resolve("bin").resolve(CliName()));

        assertTrue(HuxerUISettings.SdkLayout.IsValid(home));
        assertEquals(cli, HuxerUISettings.SdkLayout.FindCli(home));
    }

    @Test
    void recognizesAnSdkWithoutRequiringTheProjectCli(@TempDir Path home) throws Exception {
        Files.createDirectories(home.resolve("include/huxerui"));
        Files.createDirectories(home.resolve("cmake"));
        Files.createFile(home.resolve("include/huxerui/huxerui.h"));
        Files.createFile(home.resolve("cmake/HuxerUIApp.cmake"));
        HuxerUISettings settings = new HuxerUISettings();

        settings.SetSdkHome(home.toString());

        assertTrue(settings.HasValidSdk());
        assertEquals(home, settings.GetValidSdkHome());
        assertNull(HuxerUISettings.SdkLayout.FindCli(home));
        assertThrows(IllegalStateException.class, settings::RequireCli);
    }

    private static String CliName() {
        return System.getProperty("os.name", "").toLowerCase().contains("win") ? "huxerui.exe" : "huxerui";
    }
}

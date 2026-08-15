package org.huxerui.clion.project;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class HuxerUINewProjectWizardTest {
    @Test
    void registersGeneratorExtension() throws Exception {
        try (InputStream stream = getClass().getResourceAsStream("/META-INF/plugin.xml")) {
            assertNotNull(stream);
            String plugin_xml = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(plugin_xml.contains(
                    "implementation=\"org.huxerui.clion.project.HuxerUIAppProjectGenerator\""));
            assertTrue(plugin_xml.contains(
                    "implementation=\"org.huxerui.clion.project.HuxerUIModuleProjectGenerator\""));
            assertFalse(plugin_xml.contains("<newProjectWizard.generator"));
        }
    }

    @Test
    void exposesEveryProjectPlatform() {
        assertTrue(HuxerUIProjectService.all_platforms.contains("linux"));
        assertTrue(HuxerUIProjectService.all_platforms.contains("web"));
    }

    @Test
    void selectsChromeForWebOnlyProjects() {
        var device = HuxerUIDirectoryProjectGenerator.DefaultRunDevice(List.of("web"));

        assertNotNull(device);
        assertEquals("web", device.platform());
        assertEquals("chrome", device.id());
        assertNull(HuxerUIDirectoryProjectGenerator.DefaultRunDevice(List.of("linux", "web")));
    }
}

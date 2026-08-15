package org.huxerui.clion.project;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
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
}

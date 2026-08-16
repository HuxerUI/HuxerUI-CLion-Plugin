package org.huxerui.clion.resources;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class HuxerUIResourceIndexTest {
    @Test
    void discoversStringKeysAtTheirSourceOffsets() {
        String catalog = "# comment\napp-name = \"Demo\"\nwelcome_title = \"Welcome\"\n";

        List<HuxerUIResourceIndex.ParsedEntry> entries =
                HuxerUIResourceIndex.ParseFile("strings", "default.properties", catalog);

        assertEquals(List.of("app_name", "welcome_title"),
                entries.stream().map(HuxerUIResourceIndex.ParsedEntry::identifier).toList());
        assertEquals(catalog.indexOf("welcome_title"), entries.get(1).offset());
    }

    @Test
    void mapsImageAndRawPathsLikeResourceCodegen() {
        assertEquals("icons_brand_logo", HuxerUIResourceIndex.ParseFile(
                "images", "icons/brand-logo@2x.png", "").get(0).identifier());
        assertEquals("sample_config_json", HuxerUIResourceIndex.ParseFile(
                "raw", "sample/config.json", "").get(0).identifier());
        assertTrue(HuxerUIResourceIndex.ParseFile("images", "notes.txt", "").isEmpty());
    }

    @Test
    void prefersCanonicalFilesForDirectNavigation() {
        assertEquals(0, HuxerUIResourceIndex.NavigationPriority("strings", "default.properties"));
        assertEquals(1, HuxerUIResourceIndex.NavigationPriority("strings", "zh.properties"));
        assertEquals(0, HuxerUIResourceIndex.NavigationPriority("images", "logo.svg"));
        assertEquals(1, HuxerUIResourceIndex.NavigationPriority("images", "logo@2x.png"));
    }
}

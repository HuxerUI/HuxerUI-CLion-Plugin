package org.huxerui.clion.resources;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class ResourceNameCodecTest {
    @Test
    void matchesHaptIdentifiers() {
        assertEquals("logo", ResourceNameCodec.Identifier("logo"));
        assertEquals("icons_search_dark", ResourceNameCodec.Identifier("icons/search-dark"));
        assertEquals("config_json", ResourceNameCodec.Identifier("config.json"));
        assertEquals("_2fa", ResourceNameCodec.Identifier("2fa"));
        assertEquals("resource_class", ResourceNameCodec.Identifier("class"));
    }

    @Test
    void removesImageExtensionAndDensity() {
        assertEquals("logo", ResourceNameCodec.ImageLogicalName("logo@2x.png"));
        assertEquals("nested/mark", ResourceNameCodec.ImageLogicalName("nested/mark.svg"));
    }
}

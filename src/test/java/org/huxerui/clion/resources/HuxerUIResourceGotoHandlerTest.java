package org.huxerui.clion.resources;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class HuxerUIResourceGotoHandlerTest {
    @Test
    void findsUnqualifiedResourceReference() {
        String source = "return Text(strings::title);";

        assertEquals(
                new HuxerUIResourceGotoHandler.ResourceReference("strings", "title"),
                HuxerUIResourceGotoHandler.FindReference(source, source.indexOf("title"))
        );
    }

    @Test
    void findsNamespaceQualifiedResourceReference() {
        String source = "Image(app::images::brand_logo);";

        assertEquals(
                new HuxerUIResourceGotoHandler.ResourceReference("images", "brand_logo"),
                HuxerUIResourceGotoHandler.FindReference(source, source.indexOf("brand_logo"))
        );
    }
}

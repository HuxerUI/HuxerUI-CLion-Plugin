package org.huxerui.clion.resources;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

final class HuxerUIResourceCompletionContributorTest {
    @Test
    void findsQualifiedResourceCompletionPrefixes() {
        String empty = "Text(app::strings::);";
        String partial = "UseVectorImage(app::images::brand);";

        assertEquals(
                new HuxerUIResourceCompletionContributor.ResourcePrefix("strings", ""),
                HuxerUIResourceCompletionContributor.FindPrefix(empty, empty.indexOf(')')));
        assertEquals(
                new HuxerUIResourceCompletionContributor.ResourcePrefix("images", "brand"),
                HuxerUIResourceCompletionContributor.FindPrefix(partial, partial.indexOf(')')));
    }

    @Test
    void ignoresNonResourceScopes() {
        String source = "std::vector::";

        assertNull(HuxerUIResourceCompletionContributor.FindPrefix(source, source.length()));
    }
}

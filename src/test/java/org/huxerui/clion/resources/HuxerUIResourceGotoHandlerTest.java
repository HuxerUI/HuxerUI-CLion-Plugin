package org.huxerui.clion.resources;

import org.junit.jupiter.api.Test;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

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

    @Test
    void findsRawResourceReference() {
        String source = "UseRawResource(app::raw::sample_config_json);";

        assertEquals(
                new HuxerUIResourceGotoHandler.ResourceReference("raw", "sample_config_json"),
                HuxerUIResourceGotoHandler.FindReference(source, source.indexOf("sample_config_json"))
        );
    }

    @Test
    void registersBeforeNativeNavigationHandlers() throws Exception {
        try (InputStream descriptor = HuxerUIResourceGotoHandlerTest.class.getResourceAsStream(
                "/META-INF/plugin.xml")) {
            assertNotNull(descriptor);
            var document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(descriptor);
            var handlers = document.getElementsByTagName("gotoDeclarationHandler");
            for (int index = 0; index < handlers.getLength(); ++index) {
                var attributes = handlers.item(index).getAttributes();
                var implementation = attributes.getNamedItem("implementation");
                if (implementation != null && implementation.getNodeValue().equals(
                        "org.huxerui.clion.resources.HuxerUIResourceGotoHandler")) {
                    assertEquals("first", attributes.getNamedItem("order").getNodeValue());
                    return;
                }
            }
            fail("HuxerUI resource navigation handler is not registered");
        }
    }
}

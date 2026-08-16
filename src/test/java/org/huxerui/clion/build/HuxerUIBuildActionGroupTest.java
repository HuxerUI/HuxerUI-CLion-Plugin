package org.huxerui.clion.build;

import org.junit.jupiter.api.Test;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class HuxerUIBuildActionGroupTest {
    @Test
    void registersBuildGroupInTheMainToolbar() throws Exception {
        try (InputStream descriptor = HuxerUIBuildActionGroupTest.class.getResourceAsStream(
                "/META-INF/plugin.xml")) {
            assertNotNull(descriptor);
            var document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(descriptor);
            var groups = document.getElementsByTagName("group");
            boolean registered = false;
            for (int group_index = 0; group_index < groups.getLength(); ++group_index) {
                var group = groups.item(group_index);
                var attributes = group.getAttributes();
                var id = attributes.getNamedItem("id");
                if (id == null || !id.getNodeValue().equals("HuxerUI.Build")) {
                    continue;
                }
                var children = group.getChildNodes();
                for (int child_index = 0; child_index < children.getLength(); ++child_index) {
                    var child = children.item(child_index);
                    var child_attributes = child.getAttributes();
                    if (child_attributes == null) {
                        continue;
                    }
                    var target = child_attributes.getNamedItem("group-id");
                    var anchor = child_attributes.getNamedItem("anchor");
                    var relative = child_attributes.getNamedItem("relative-to-action");
                    if (target != null && target.getNodeValue().equals("RunToolbarMainActionGroup")
                            && anchor != null && anchor.getNodeValue().equals("after")
                            && relative != null
                            && relative.getNodeValue().equals("RunToolbarTopLevelExecutorActionGroup")) {
                        registered = true;
                    }
                }
            }
            assertTrue(registered);
        }
    }

    @Test
    void usesAStandardToolbarIcon() {
        assertNotNull(new HuxerUIBuildActionGroup().getTemplatePresentation().getIcon());
    }

    @Test
    void doesNotRegisterASeparateDeviceSelector() throws Exception {
        try (InputStream descriptor = HuxerUIBuildActionGroupTest.class.getResourceAsStream(
                "/META-INF/plugin.xml")) {
            assertNotNull(descriptor);
            var document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(descriptor);
            var actions = document.getElementsByTagName("action");
            for (int action_index = 0; action_index < actions.getLength(); ++action_index) {
                var id = actions.item(action_index).getAttributes().getNamedItem("id");
                assertFalse(id != null && id.getNodeValue().equals("HuxerUI.DeviceSelector"));
            }
        }
    }
}

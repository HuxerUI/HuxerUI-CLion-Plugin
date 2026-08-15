package org.huxerui.clion.project;

import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBTextField;
import com.jetbrains.cidr.cpp.cmake.projectWizard.generators.CLionProjectGenerator;
import org.junit.jupiter.api.Test;

import javax.swing.JComponent;
import java.awt.Component;
import java.awt.Container;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class HuxerUIDirectoryProjectGeneratorTest {
    @Test
    void appGeneratorUsesTheHuxerUIGroupAndAcceptsAnApplicationId() {
        HuxerUIAppProjectGenerator generator = new HuxerUIAppProjectGenerator();
        assertTrue(generator instanceof CLionProjectGenerator);
        assertEquals("HuxerUI", generator.getGroupName());
        assertEquals("HuxerUI", generator.getGroupDisplayName());
        assertEquals(800, generator.getGroupOrder());
        assertEquals("HuxerUI App", generator.getName());

        HuxerUIDirectoryProjectGenerator.Peer peer =
                (HuxerUIDirectoryProjectGenerator.Peer) generator.createPeer();
        JComponent component = peer.getComponent(new TextFieldWithBrowseButton(), () -> {});
        assertSame(component, generator.getSettingsPanel());

        JBCheckBox macos = FindCheckBox(component, "huxerui.platform.macos");
        assertNotNull(macos);
        assertEquals("macOS", macos.getText());

        JBTextField application_id = FindTextField(component, "huxerui.application_id");
        assertNotNull(application_id);
        application_id.setText("dev.example.myapp");
        assertEquals("dev.example.myapp", peer.getSettings().project_id());
        assertNull(peer.validate());

        application_id.setText("Dev.Example.MyApp");
        assertNotNull(peer.validate());
    }

    @Test
    void moduleGeneratorIsASeparateEntryWithInteractivePlatforms() {
        HuxerUIModuleProjectGenerator generator = new HuxerUIModuleProjectGenerator();
        assertEquals("HuxerUI", generator.getGroupName());
        assertEquals("HuxerUI Module", generator.getName());

        HuxerUIDirectoryProjectGenerator.Peer peer =
                (HuxerUIDirectoryProjectGenerator.Peer) generator.createPeer();
        JComponent component = peer.getComponent(new TextFieldWithBrowseButton(), () -> {});
        assertNull(FindTextField(component, "huxerui.application_id"));

        JBCheckBox linux = FindCheckBox(component, "huxerui.platform.linux");
        assertNotNull(linux);
        assertTrue(peer.getSettings().platforms().contains("linux"));
        linux.doClick();
        assertFalse(peer.getSettings().platforms().contains("linux"));
        assertNull(peer.validate());
    }

    private static JBCheckBox FindCheckBox(Container container, String name) {
        for (Component child : container.getComponents()) {
            if (child instanceof JBCheckBox check_box && name.equals(check_box.getName())) {
                return check_box;
            }
            if (child instanceof Container nested) {
                JBCheckBox found = FindCheckBox(nested, name);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static JBTextField FindTextField(Container container, String name) {
        for (Component child : container.getComponents()) {
            if (child instanceof JBTextField text_field && name.equals(text_field.getName())) {
                return text_field;
            }
            if (child instanceof Container nested) {
                JBTextField found = FindTextField(nested, name);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}

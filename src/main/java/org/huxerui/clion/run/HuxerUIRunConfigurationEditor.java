package org.huxerui.clion.run;

import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.FormBuilder;
import org.huxerui.clion.project.HuxerUIProjectService;
import org.jetbrains.annotations.NotNull;

import javax.swing.JComboBox;
import javax.swing.JComponent;

final class HuxerUIRunConfigurationEditor extends SettingsEditor<HuxerUIRunConfiguration> {
    private final Project project_;
    private final JComboBox<HuxerUIDevice> device_ = new JComboBox<>();
    private final JComboBox<String> profile_ = new JComboBox<>(new String[]{"debug", "release"});

    HuxerUIRunConfigurationEditor(Project project) {
        project_ = project;
        device_.setRenderer(new javax.swing.DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(
                    javax.swing.JList<?> list, Object value, int index, boolean selected, boolean focused
            ) {
                super.getListCellRendererComponent(list, value, index, selected, focused);
                if (value instanceof HuxerUIDevice device) {
                    setText(device.DisplayName());
                }
                return this;
            }
        });
    }

    @Override
    protected void resetEditorFrom(@NotNull HuxerUIRunConfiguration configuration) {
        device_.removeAllItems();
        HuxerUIProjectService service = HuxerUIProjectService.Get(project_);
        for (HuxerUIDevice device : service.Devices()) {
            if (device.IsReady()) {
                device_.addItem(device);
                if (device.platform().equals(configuration.GetPlatform())
                        && device.id().equals(configuration.GetDeviceId())) {
                    device_.setSelectedItem(device);
                }
            }
        }
        profile_.setSelectedItem(configuration.GetProfile());
    }

    @Override
    protected void applyEditorTo(@NotNull HuxerUIRunConfiguration configuration) {
        HuxerUIDevice device = (HuxerUIDevice) device_.getSelectedItem();
        if (device != null) {
            configuration.SetPlatform(device.platform());
            configuration.SetDeviceId(device.id());
        }
        configuration.SetProfile((String) profile_.getSelectedItem());
    }

    @Override
    protected @NotNull JComponent createEditor() {
        return FormBuilder.createFormBuilder()
                .addLabeledComponent(new JBLabel("Device:"), device_)
                .addLabeledComponent(new JBLabel("Profile:"), profile_)
                .getPanel();
    }
}

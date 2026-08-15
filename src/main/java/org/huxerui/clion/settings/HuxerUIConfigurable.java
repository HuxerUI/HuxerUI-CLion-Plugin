package org.huxerui.clion.settings;

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.JPanel;
import java.util.Objects;

public final class HuxerUIConfigurable implements Configurable {
    private TextFieldWithBrowseButton sdk_home_;
    private JPanel panel_;

    @Override
    public @Nls String getDisplayName() {
        return "HuxerUI";
    }

    @Override
    public @Nullable JComponent createComponent() {
        sdk_home_ = new TextFieldWithBrowseButton();
        sdk_home_.addBrowseFolderListener(null, FileChooserDescriptorFactory.createSingleFolderDescriptor());
        panel_ = FormBuilder.createFormBuilder()
                .addLabeledComponent(new JBLabel("HUXERUI_HOME:"), sdk_home_, 1, false)
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();
        reset();
        return panel_;
    }

    @Override
    public boolean isModified() {
        return sdk_home_ != null
                && !Objects.equals(sdk_home_.getText().trim(), HuxerUISettings.getInstance().GetSdkHome());
    }

    @Override
    public void apply() {
        HuxerUISettings.getInstance().SetSdkHome(sdk_home_.getText());
    }

    @Override
    public void reset() {
        if (sdk_home_ != null) {
            sdk_home_.setText(HuxerUISettings.getInstance().GetSdkHome());
        }
    }

    @Override
    public void disposeUIResources() {
        sdk_home_ = null;
        panel_ = null;
    }
}

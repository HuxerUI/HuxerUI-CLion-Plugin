package org.huxerui.clion.project;

import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class NewProjectDialog extends DialogWrapper {
    private final JComboBox<String> kind_ = new JComboBox<>(new String[]{"Application", "Module"});
    private final JBTextField name_ = new JBTextField("hello_huxer");
    private final JBTextField parent_ = new JBTextField(System.getProperty("user.home"));
    private final Map<String, JBCheckBox> platforms_ = new LinkedHashMap<>();

    NewProjectDialog() {
        super(true);
        setTitle("New HuxerUI Project");
        for (String platform : HuxerUIProjectService.all_platforms) {
            platforms_.put(platform, new JBCheckBox(DisplayName(platform), true));
        }
        kind_.addActionListener(event -> UpdatePlatformAvailability());
        init();
        UpdatePlatformAvailability();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel platform_panel = new JPanel();
        for (JBCheckBox check_box : platforms_.values()) {
            platform_panel.add(check_box);
        }
        return FormBuilder.createFormBuilder()
                .addLabeledComponent(new JBLabel("Type:"), kind_)
                .addLabeledComponent(new JBLabel("Name:"), name_)
                .addLabeledComponent(new JBLabel("Parent directory:"), parent_)
                .addLabeledComponent(new JBLabel("Platforms:"), platform_panel)
                .getPanel();
    }

    @Override
    protected @Nullable ValidationInfo doValidate() {
        if (!name_.getText().matches("[A-Za-z][A-Za-z0-9_-]*")) {
            return new ValidationInfo("Name must start with a letter and contain letters, digits, '_' or '-'.", name_);
        }
        Path parent = Path.of(parent_.getText()).toAbsolutePath().normalize();
        if (!Files.isDirectory(parent)) {
            return new ValidationInfo("Parent directory does not exist.", parent_);
        }
        if (Files.exists(parent.resolve(name_.getText()))) {
            return new ValidationInfo("Destination already exists.", name_);
        }
        if (Kind().equals("app") && SelectedPlatforms().isEmpty()) {
            return new ValidationInfo("An application requires at least one platform.");
        }
        return null;
    }

    String Kind() {
        return kind_.getSelectedIndex() == 0 ? "app" : "module";
    }

    String ProjectName() {
        return name_.getText();
    }

    Path ParentDirectory() {
        return Path.of(parent_.getText()).toAbsolutePath().normalize();
    }

    List<String> SelectedPlatforms() {
        List<String> selected = new ArrayList<>();
        platforms_.forEach((platform, check_box) -> {
            if (check_box.isSelected()) {
                selected.add(platform);
            }
        });
        return selected;
    }

    private static String DisplayName(String platform) {
        return platform.equals("ios") ? "iOS" : Character.toUpperCase(platform.charAt(0)) + platform.substring(1);
    }

    private void UpdatePlatformAvailability() {
        boolean application = Kind().equals("app");
        platforms_.values().forEach(check_box -> check_box.setEnabled(application));
    }
}

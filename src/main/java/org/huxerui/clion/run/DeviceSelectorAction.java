package org.huxerui.clion.run;

import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import org.huxerui.clion.HuxerUINotifications;
import org.huxerui.clion.project.HuxerUIProjectService;
import org.jetbrains.annotations.NotNull;

import javax.swing.JComponent;

public final class DeviceSelectorAction extends ComboBoxAction implements DumbAware {
    @Override
    public void update(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        boolean visible = project != null && HuxerUIProjectService.Get(project).IsProject();
        event.getPresentation().setEnabledAndVisible(visible);
        if (visible) {
            HuxerUIProjectService service = HuxerUIProjectService.Get(project);
            service.EnsureDevicesLoaded();
            HuxerUIDevice selected = service.SelectedDevice();
            event.getPresentation().setText(selected.platform().isBlank() ? "Select Device" : selected.DisplayName());
        }
    }

    @Override
    protected @NotNull DefaultActionGroup createPopupActionGroup(
            @NotNull JComponent button,
            @NotNull DataContext data_context
    ) {
        DefaultActionGroup group = new DefaultActionGroup();
        Project project = com.intellij.openapi.actionSystem.CommonDataKeys.PROJECT.getData(data_context);
        if (project == null) {
            return group;
        }
        HuxerUIProjectService service = HuxerUIProjectService.Get(project);
        for (HuxerUIDevice device : service.Devices()) {
            group.add(new SelectDeviceAction(project, device));
        }
        if (!service.Devices().isEmpty()) {
            group.addSeparator();
        }
        group.add(new RefreshDevicesAction(project));
        return group;
    }

    private static final class SelectDeviceAction extends AnAction implements DumbAware {
        private final Project project_;
        private final HuxerUIDevice device_;

        private SelectDeviceAction(Project project, HuxerUIDevice device) {
            super(device.DisplayName() + (device.IsReady() ? "" : " [" + device.state() + "]"));
            project_ = project;
            device_ = device;
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent event) {
            if (!device_.IsReady()) {
                return;
            }
            HuxerUIProjectService.Get(project_).SelectDevice(device_);
            SelectRunConfiguration(project_, device_);
        }

        @Override
        public void update(@NotNull AnActionEvent event) {
            event.getPresentation().setEnabled(device_.IsReady());
        }
    }

    private static final class RefreshDevicesAction extends AnAction implements DumbAware {
        private final Project project_;

        private RefreshDevicesAction(Project project) {
            super("Refresh Devices");
            project_ = project;
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent event) {
            new Task.Backgroundable(project_, "Discover HuxerUI Devices", true) {
                private Exception failure_;

                @Override
                public void run(@NotNull ProgressIndicator indicator) {
                    indicator.setIndeterminate(true);
                    try {
                        HuxerUIProjectService.Get(project_).RefreshDevices();
                    } catch (Exception error) {
                        failure_ = error;
                    }
                }

                @Override
                public void onSuccess() {
                    if (failure_ != null) {
                        HuxerUINotifications.error(project_, "Device discovery failed", failure_.getMessage());
                    }
                }
            }.queue();
        }
    }

    private static void SelectRunConfiguration(Project project, HuxerUIDevice device) {
        RunManager manager = RunManager.getInstance(project);
        RunnerAndConfigurationSettings settings = manager.getAllSettings().stream()
                .filter(item -> item.getConfiguration() instanceof HuxerUIRunConfiguration)
                .findFirst()
                .orElseGet(() -> {
                    RunnerAndConfigurationSettings created = manager.createConfiguration(
                            "HuxerUI",
                            HuxerUIConfigurationType.class
                    );
                    manager.addConfiguration(created);
                    return created;
                });
        HuxerUIRunConfiguration configuration = (HuxerUIRunConfiguration) settings.getConfiguration();
        configuration.SetPlatform(device.platform());
        configuration.SetDeviceId(device.id());
        configuration.SetProfile(HuxerUIProjectService.Get(project).Profile());
        manager.setSelectedConfiguration(settings);
    }
}

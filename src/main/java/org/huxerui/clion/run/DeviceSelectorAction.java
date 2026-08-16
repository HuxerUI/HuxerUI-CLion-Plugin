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
import org.huxerui.clion.PlatformNames;
import org.huxerui.clion.project.HuxerUIProjectService;
import org.jetbrains.annotations.NotNull;

import javax.swing.JComponent;
import java.util.List;

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

    public static void SelectRunConfiguration(Project project, HuxerUIDevice device) {
        RunManager manager = RunManager.getInstance(project);
        RunnerAndConfigurationSettings settings = EnsureRunConfiguration(manager, project, device);
        HuxerUIProjectService.Get(project).SelectDevice(device);
        manager.setSelectedConfiguration(settings);
    }

    public static void SyncRunConfigurations(Project project, List<HuxerUIDevice> devices) {
        RunManager manager = RunManager.getInstance(project);
        HuxerUIProjectService service = HuxerUIProjectService.Get(project);
        HuxerUIDevice selected = service.SelectedDevice();
        RunnerAndConfigurationSettings selected_settings = null;
        List<HuxerUIDevice> ready = devices.stream().filter(HuxerUIDevice::IsReady).toList();
        for (HuxerUIDevice device : ready) {
            RunnerAndConfigurationSettings settings = EnsureRunConfiguration(manager, project, device);
            if (SameTarget(device, selected)) {
                selected_settings = settings;
            }
        }
        if (selected_settings == null && ready.size() == 1) {
            HuxerUIDevice device = ready.get(0);
            service.SelectDevice(device);
            selected_settings = EnsureRunConfiguration(manager, project, device);
        }
        if (selected_settings != null) {
            manager.setSelectedConfiguration(selected_settings);
        }
    }

    static String ConfigurationName(HuxerUIDevice device) {
        String platform = PlatformNames.DisplayName(device.platform());
        return device.platform().equals("android") || device.platform().equals("ios")
                ? "HuxerUI " + platform + " — " + (device.name().isBlank() ? device.id() : device.name())
                : "HuxerUI " + platform;
    }

    private static RunnerAndConfigurationSettings EnsureRunConfiguration(
            RunManager manager,
            Project project,
            HuxerUIDevice device
    ) {
        RunnerAndConfigurationSettings cmake = HuxerUICMakeRunConfiguration.Ensure(manager, project, device);
        if (cmake != null) {
            for (RunnerAndConfigurationSettings item : List.copyOf(manager.getAllSettings())) {
                if (item.getConfiguration() instanceof HuxerUIRunConfiguration configuration
                        && configuration.GetPlatform().equals(device.platform())
                        && configuration.GetDeviceId().equals(device.id())) {
                    manager.removeConfiguration(item);
                }
            }
            return cmake;
        }
        RunnerAndConfigurationSettings settings = manager.getAllSettings().stream()
                .filter(item -> item.getConfiguration() instanceof HuxerUIRunConfiguration configuration
                        && configuration.GetPlatform().equals(device.platform())
                        && configuration.GetDeviceId().equals(device.id()))
                .findFirst()
                .orElseGet(() -> {
                    RunnerAndConfigurationSettings created = manager.createConfiguration(
                            ConfigurationName(device),
                            HuxerUIConfigurationType.class
                    );
                    manager.addConfiguration(created);
                    return created;
                });
        settings.setName(ConfigurationName(device));
        HuxerUIRunConfiguration configuration = (HuxerUIRunConfiguration) settings.getConfiguration();
        configuration.SetPlatform(device.platform());
        configuration.SetDeviceId(device.id());
        configuration.SetProfile(HuxerUIProjectService.Get(project).Profile());
        return settings;
    }

    private static boolean SameTarget(HuxerUIDevice left, HuxerUIDevice right) {
        return left.platform().equals(right.platform()) && left.id().equals(right.id());
    }
}

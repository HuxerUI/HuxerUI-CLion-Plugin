package org.huxerui.clion.project;

import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import org.huxerui.clion.HuxerUINotifications;
import org.huxerui.clion.run.DeviceSelectorAction;
import org.huxerui.clion.run.HuxerUIDevice;
import org.huxerui.clion.sdk.InstallSdkAction;
import org.huxerui.clion.settings.HuxerUIConfigurable;
import org.huxerui.clion.settings.HuxerUISettings;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class HuxerUIProjectStartupActivity implements StartupActivity {
    @Override
    public void runActivity(@NotNull Project project) {
        Activate(project);
    }

    public static void Activate(Project project) {
        if (project.isDisposed()) {
            return;
        }
        HuxerUIProjectService service = HuxerUIProjectService.Get(project);
        if (!service.IsProject()) {
            return;
        }
        if (!HuxerUISettings.getInstance().HasValidSdk()) {
            PromptForSdk(project, service);
            return;
        }
        service.ResetSdkPrompt();
        List<HuxerUIDevice> immediate = HuxerUIProjectService.ImmediateRunDevices(
                service.EnabledPlatforms(), HuxerUIProjectService.HostPlatformId());
        ApplicationManager.getApplication().invokeLater(() -> {
            if (!project.isDisposed()) {
                DeviceSelectorAction.SyncRunConfigurations(project, immediate);
            }
        });
        service.EnsureDevicesLoaded();
    }

    private static void PromptForSdk(Project project, HuxerUIProjectService service) {
        if (!service.MarkSdkPromptShown()) {
            return;
        }
        var notification = NotificationGroupManager.getInstance().getNotificationGroup("HuxerUI").createNotification(
                "HuxerUI SDK required",
                "This CMake project uses HuxerUI. Install an SDK to enable build, run, and device "
                        + "support.",
                NotificationType.WARNING);
        notification.addAction(NotificationAction.createSimpleExpiring(
                "Install SDK", () -> InstallSdkAction.Install(project, () -> ConfigureAfterSdkAvailable(project))));
        notification.addAction(NotificationAction.createSimpleExpiring("Configure SDK", () -> {
            ShowSettingsUtil.getInstance().showSettingsDialog(project, HuxerUIConfigurable.class);
            if (HuxerUISettings.getInstance().HasValidSdk()) {
                ConfigureAfterSdkAvailable(project);
            }
        }));
        notification.notify(project);
    }

    private static void ConfigureAfterSdkAvailable(Project project) {
        new Task.Backgroundable(project, "Configure HuxerUI Project", false) {
            private Exception failure_;

            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                indicator.setIndeterminate(true);
                indicator.setText("Configuring HuxerUI CMake profiles");
                try {
                    HuxerUIDirectoryProjectGenerator.ConfigureRecognizedProject(project);
                } catch (Exception error) {
                    failure_ = error;
                }
            }

            @Override
            public void onSuccess() {
                if (failure_ != null) {
                    HuxerUINotifications.error(project, "HuxerUI project configuration failed", failure_.getMessage());
                }
                Activate(project);
            }
        }.queue();
    }
}

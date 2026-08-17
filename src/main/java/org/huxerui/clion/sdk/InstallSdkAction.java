package org.huxerui.clion.sdk;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import org.huxerui.clion.HuxerUINotifications;
import org.huxerui.clion.settings.HuxerUISettings;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

public final class InstallSdkAction extends AnAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        Install(event.getProject(), null);
    }

    public static void Install(Project project, Runnable on_installed) {
        new Task.Modal(project, "Install HuxerUI SDK", true) {
            private Path installed_;
            private Exception failure_;

            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                try {
                    installed_ = new SdkInstaller().InstallLatest(indicator);
                } catch (Exception error) {
                    failure_ = error;
                }
            }

            @Override
            public void onSuccess() {
                if (failure_ != null) {
                    HuxerUINotifications.error(project, "HuxerUI SDK installation failed", failure_.getMessage());
                    return;
                }
                HuxerUISettings.getInstance().SetSdkHome(installed_.toString());
                HuxerUINotifications.info(project, "HuxerUI SDK installed", "HUXERUI_HOME is now " + installed_);
                if (on_installed != null) {
                    on_installed.run();
                }
            }
        }.queue();
    }
}

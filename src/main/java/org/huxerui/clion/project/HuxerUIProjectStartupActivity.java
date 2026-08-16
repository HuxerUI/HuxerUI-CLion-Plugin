package org.huxerui.clion.project;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import org.huxerui.clion.run.DeviceSelectorAction;
import org.huxerui.clion.run.HuxerUIDevice;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class HuxerUIProjectStartupActivity implements StartupActivity {
    @Override
    public void runActivity(@NotNull Project project) {
        HuxerUIProjectService service = HuxerUIProjectService.Get(project);
        if (!service.IsProject()) {
            return;
        }
        List<HuxerUIDevice> immediate = HuxerUIProjectService.ImmediateRunDevices(
                service.EnabledPlatforms(), HuxerUIProjectService.HostPlatformId());
        ApplicationManager.getApplication().invokeLater(() -> {
            if (!project.isDisposed()) {
                DeviceSelectorAction.SyncRunConfigurations(project, immediate);
            }
        });
        service.EnsureDevicesLoaded();
    }
}

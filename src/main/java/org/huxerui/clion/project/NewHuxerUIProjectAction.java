package org.huxerui.clion.project;

import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import org.huxerui.clion.HuxerUINotifications;
import org.huxerui.clion.cli.HuxerUICommand;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.List;

public final class NewHuxerUIProjectAction extends AnAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        NewProjectDialog dialog = new NewProjectDialog();
        if (!dialog.showAndGet()) {
            return;
        }
        new Task.Modal(event.getProject(), "Create HuxerUI Project", true) {
            private Exception failure_;

            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                indicator.setIndeterminate(true);
                indicator.setText("Creating " + dialog.ProjectName());
                try {
                    if (dialog.Kind().equals("module")) {
                        ModuleProjectGenerator.Create(dialog.ParentDirectory(), dialog.ProjectName());
                    } else {
                        List<String> arguments = List.of(
                                "create",
                                dialog.ProjectName(),
                                "--platform",
                                String.join(",", dialog.SelectedPlatforms())
                        );
                        var output = new com.intellij.execution.process.CapturingProcessHandler(
                                HuxerUICommand.Create(dialog.ParentDirectory(), arguments)
                        ).runProcess(120_000);
                        if (output.isTimeout() || output.getExitCode() != 0) {
                            throw new IllegalStateException((output.getStderr() + output.getStdout()).strip());
                        }
                    }
                } catch (Exception error) {
                    failure_ = error;
                }
            }

            @Override
            public void onSuccess() {
                if (failure_ != null) {
                    HuxerUINotifications.error(
                            event.getProject(),
                            "HuxerUI project creation failed",
                            failure_.getMessage()
                    );
                    return;
                }
                Path project = dialog.ParentDirectory().resolve(dialog.ProjectName());
                ProjectUtil.openOrImport(project.toString(), event.getProject(), false);
            }
        }.queue();
    }
}

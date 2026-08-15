package org.huxerui.clion.build;

import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import org.huxerui.clion.HuxerUINotifications;
import org.huxerui.clion.PlatformNames;
import org.huxerui.clion.cli.HuxerUICommand;
import org.huxerui.clion.project.HuxerUIProjectService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class HuxerUIBuildActionGroup extends ActionGroup implements DumbAware {
    @Override
    public AnAction @NotNull [] getChildren(@Nullable AnActionEvent event) {
        if (event == null || event.getProject() == null) {
            return AnAction.EMPTY_ARRAY;
        }
        Project project = event.getProject();
        List<String> platforms = HuxerUIProjectService.Get(project).EnabledPlatforms();
        List<AnAction> actions = new ArrayList<>();
        if (platforms.size() > 1) {
            DefaultActionGroup all = new DefaultActionGroup("Build All Enabled Platforms", true);
            all.add(new BuildAction(project, String.join(",", platforms), "debug"));
            all.add(new BuildAction(project, String.join(",", platforms), "release"));
            actions.add(all);
        }
        for (String platform : platforms) {
            DefaultActionGroup platform_group = new DefaultActionGroup(PlatformNames.DisplayName(platform), true);
            platform_group.add(new BuildAction(project, platform, "debug"));
            platform_group.add(new BuildAction(project, platform, "release"));
            actions.add(platform_group);
        }
        return actions.toArray(AnAction[]::new);
    }

    @Override
    public void update(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        boolean visible = project != null && HuxerUIProjectService.Get(project).IsProject();
        event.getPresentation().setEnabledAndVisible(visible);
    }

    private static final class BuildAction extends AnAction implements DumbAware {
        private final Project project_;
        private final String platforms_;
        private final String profile_;

        private BuildAction(Project project, String platforms, String profile) {
            super(profile.equals("debug") ? "Debug" : "Release");
            project_ = project;
            platforms_ = platforms;
            profile_ = profile;
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent event) {
            new Task.Backgroundable(project_, "Build HuxerUI " + platforms_, true) {
                private String output_ = "";
                private Exception failure_;

                @Override
                public void run(@NotNull ProgressIndicator indicator) {
                    indicator.setIndeterminate(true);
                    try {
                        ProcessOutput output = new CapturingProcessHandler(HuxerUICommand.Create(
                                HuxerUICommand.ProjectRoot(project_),
                                List.of("build", platforms_, "--profile", profile_)
                        )).runProcess(0);
                        output_ = (output.getStdout() + output.getStderr()).strip();
                        if (output.getExitCode() != 0) {
                            throw new IllegalStateException(output_);
                        }
                    } catch (Exception error) {
                        failure_ = error;
                    }
                }

                @Override
                public void onSuccess() {
                    if (failure_ != null) {
                        HuxerUINotifications.error(project_, "HuxerUI build failed", failure_.getMessage());
                    } else {
                        HuxerUINotifications.info(
                                project_,
                                "HuxerUI build completed",
                                platforms_ + " (" + profile_ + ")" + (output_.isBlank() ? "" : "<br>" + Escape(output_))
                        );
                    }
                }
            }.queue();
        }
    }

    private static String Escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>");
    }
}

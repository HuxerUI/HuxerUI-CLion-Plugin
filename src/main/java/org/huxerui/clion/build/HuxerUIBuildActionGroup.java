package org.huxerui.clion.build;

import com.intellij.build.DefaultBuildDescriptor;
import com.intellij.build.BuildViewManager;
import com.intellij.build.events.impl.FailureResultImpl;
import com.intellij.build.events.impl.SuccessResultImpl;
import com.intellij.build.progress.BuildProgress;
import com.intellij.build.progress.BuildProgressDescriptor;
import com.intellij.icons.AllIcons;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.KillableColoredProcessHandler;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessListener;
import com.intellij.execution.process.ProcessOutputType;
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
    public HuxerUIBuildActionGroup() {
        super("Build HuxerUI", true);
        getTemplatePresentation().setDescription("Build HuxerUI artifacts for an enabled platform");
        getTemplatePresentation().setIcon(AllIcons.Actions.Compile);
    }

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
            String title = "Build HuxerUI " + platforms_ + " (" + profile_ + ")";
            new Task.Backgroundable(project_, title, true) {
                private Exception failure_;
                private boolean canceled_;

                @Override
                public void run(@NotNull ProgressIndicator indicator) {
                    indicator.setIndeterminate(true);
                    indicator.setText(title);
                    long start_time = System.currentTimeMillis();
                    DefaultBuildDescriptor descriptor = new DefaultBuildDescriptor(
                            new Object(), title, HuxerUICommand.ProjectRoot(project_).toString(), start_time);
                    descriptor.setActivateToolWindowWhenAdded(true);
                    descriptor.setActivateToolWindowWhenFailed(true);
                    BuildProgress<BuildProgressDescriptor> build = BuildViewManager.createBuildProgress(project_);
                    build.start(new HuxerUIBuildProgressDescriptor(title, descriptor));
                    try {
                        GeneralCommandLine command = HuxerUICommand.Create(
                                HuxerUICommand.ProjectRoot(project_),
                                List.of("build", platforms_, "--profile", profile_)
                        );
                        build.output("> " + command.getCommandLineString() + "\n", ProcessOutputType.SYSTEM);
                        KillableColoredProcessHandler process = new KillableColoredProcessHandler(command);
                        process.addProcessListener(new ProcessListener() {
                            @Override
                            public void onTextAvailable(
                                    @NotNull ProcessEvent event,
                                    @NotNull com.intellij.openapi.util.Key output_type
                            ) {
                                ProcessOutputType type = ProcessOutputType.fromKey(output_type);
                                build.output(event.getText(), type == null ? ProcessOutputType.SYSTEM : type);
                            }
                        });
                        process.startNotify();
                        while (!process.waitFor(100)) {
                            if (indicator.isCanceled()) {
                                canceled_ = true;
                                process.destroyProcess();
                                process.waitFor();
                                build.cancel(System.currentTimeMillis(), "Build canceled");
                                return;
                            }
                        }
                        Integer exit_code = process.getExitCode();
                        if (exit_code == null || exit_code != 0) {
                            String message = "HuxerUI build exited with code "
                                    + (exit_code == null ? "unknown" : exit_code);
                            failure_ = new IllegalStateException(message);
                            build.finish(System.currentTimeMillis(), "Build failed", new FailureResultImpl(message));
                        } else {
                            build.finish(System.currentTimeMillis(), "Build successful", new SuccessResultImpl());
                        }
                    } catch (Exception error) {
                        failure_ = error;
                        String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
                        build.output(message + "\n", ProcessOutputType.STDERR);
                        build.finish(System.currentTimeMillis(), "Build failed", new FailureResultImpl(message, error));
                    }
                }

                @Override
                public void onSuccess() {
                    if (canceled_) {
                        return;
                    }
                    if (failure_ != null) {
                        HuxerUINotifications.error(project_, "HuxerUI build failed", failure_.getMessage());
                    } else {
                        HuxerUINotifications.info(
                                project_,
                                "HuxerUI build completed",
                                platforms_ + " (" + profile_ + ")"
                        );
                    }
                }
            }.queue();
        }
    }

    private record HuxerUIBuildProgressDescriptor(
            String title,
            DefaultBuildDescriptor descriptor
    ) implements BuildProgressDescriptor {
        @Override
        public String getTitle() {
            return title;
        }

        @Override
        public DefaultBuildDescriptor getBuildDescriptor() {
            return descriptor;
        }
    }
}

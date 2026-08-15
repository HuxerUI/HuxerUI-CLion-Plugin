package org.huxerui.clion.run;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.RunConfigurationOptions;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.execution.process.KillableColoredProcessHandler;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import org.huxerui.clion.cli.HuxerUICommand;
import org.huxerui.clion.project.HuxerUIProjectService;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class HuxerUIRunConfiguration extends RunConfigurationBase<RunConfigurationOptions> {
    private String platform_ = "";
    private String device_id_ = "";
    private String profile_ = "debug";

    protected HuxerUIRunConfiguration(
            Project project,
            ConfigurationFactory factory,
            String name
    ) {
        super(project, factory, name);
    }

    @Override
    public @NotNull SettingsEditor<? extends HuxerUIRunConfiguration> getConfigurationEditor() {
        return new HuxerUIRunConfigurationEditor(getProject());
    }

    @Override
    public void checkConfiguration() throws com.intellij.execution.configurations.RuntimeConfigurationException {
        if (platform_.isBlank()) {
            throw new com.intellij.execution.configurations.RuntimeConfigurationError("Select a HuxerUI device");
        }
    }

    @Override
    public @Nullable RunProfileState getState(
            @NotNull Executor executor,
            @NotNull ExecutionEnvironment environment
    ) {
        return new HuxerUICommandLineState(environment, this);
    }

    @Override
    public void readExternal(@NotNull Element element) throws com.intellij.openapi.util.InvalidDataException {
        super.readExternal(element);
        platform_ = element.getAttributeValue("platform", "");
        device_id_ = element.getAttributeValue("device", "");
        profile_ = element.getAttributeValue("profile", "debug");
    }

    @Override
    public void writeExternal(@NotNull Element element) throws com.intellij.openapi.util.WriteExternalException {
        super.writeExternal(element);
        element.setAttribute("platform", platform_);
        element.setAttribute("device", device_id_);
        element.setAttribute("profile", profile_);
    }

    public String GetPlatform() {
        return platform_;
    }

    public void SetPlatform(String platform) {
        platform_ = platform;
    }

    public String GetDeviceId() {
        return device_id_;
    }

    public void SetDeviceId(String device_id) {
        device_id_ = device_id;
    }

    public String GetProfile() {
        return profile_;
    }

    public void SetProfile(String profile) {
        profile_ = profile.equals("release") ? "release" : "debug";
    }

    private static final class HuxerUICommandLineState extends com.intellij.execution.configurations.CommandLineState {
        private final HuxerUIRunConfiguration configuration_;

        private HuxerUICommandLineState(ExecutionEnvironment environment, HuxerUIRunConfiguration configuration) {
            super(environment);
            configuration_ = configuration;
            setConsoleBuilder(TextConsoleBuilderFactory.getInstance().createBuilder(configuration.getProject()));
        }

        @Override
        protected @NotNull ProcessHandler startProcess() throws ExecutionException {
            Path root = HuxerUICommand.ProjectRoot(configuration_.getProject());
            if (configuration_.platform_.equals("web") && configuration_.device_id_.equals("chrome")) {
                return StartChrome(root);
            }
            List<String> arguments = new ArrayList<>(List.of(
                    "run", configuration_.platform_, "--profile", configuration_.profile_
            ));
            if ((configuration_.platform_.equals("android") || configuration_.platform_.equals("ios"))
                    && !configuration_.device_id_.isBlank()) {
                arguments.add("--device");
                arguments.add(configuration_.device_id_);
            }
            try {
                return new KillableColoredProcessHandler(HuxerUICommand.Create(root, arguments));
            } catch (ExecutionException error) {
                throw error;
            }
        }

        private ProcessHandler StartChrome(Path root) throws ExecutionException {
            try {
                ProcessOutput build = new CapturingProcessHandler(HuxerUICommand.Create(
                        root,
                        List.of("build", "web", "--profile", configuration_.profile_)
                )).runProcess(0);
                if (build.getExitCode() != 0) {
                    throw new ExecutionException((build.getStderr() + build.getStdout()).strip());
                }
                Path application_root = HuxerUIProjectService.FindApplicationRoot(root);
                if (application_root == null) {
                    throw new ExecutionException("HuxerUI application root is not configured");
                }
                Path build_root = application_root.resolve(".huxerui/build/web").resolve(configuration_.profile_);
                Path html;
                try (var files = Files.walk(build_root)) {
                    html = files.filter(path ->
                                    Files.isRegularFile(path) && path.getFileName().toString().endsWith(".html")
                            )
                            .max(Comparator.comparingLong(HuxerUIRunConfiguration::ModifiedTime))
                            .orElseThrow(() -> new ExecutionException("Web build produced no HTML entry point"));
                }
                var command = new com.intellij.execution.configurations.GeneralCommandLine("emrun")
                        .withWorkDirectory(html.getParent().toFile())
                        .withParameters("--browser", "chrome", html.getFileName().toString());
                return new KillableColoredProcessHandler(command);
            } catch (ExecutionException error) {
                throw error;
            } catch (Exception error) {
                throw new ExecutionException("Unable to run HuxerUI Web application in Chrome", error);
            }
        }
    }

    private static long ModifiedTime(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (Exception ignored) {
            return 0;
        }
    }
}

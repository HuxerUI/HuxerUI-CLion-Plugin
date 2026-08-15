package org.huxerui.clion.project;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.facet.ui.ValidationResult;
import com.intellij.ide.util.projectWizard.SettingsStep;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.ProjectGeneratorPeer;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBTextField;
import com.jetbrains.cidr.cpp.cmake.CMakeSettings;
import com.jetbrains.cidr.cpp.cmake.projectWizard.generators.CLionProjectGenerator;
import com.jetbrains.cidr.cpp.cmake.workspace.CMakeWorkspace;
import kotlin.coroutines.EmptyCoroutineContext;
import kotlinx.coroutines.BuildersKt;
import kotlinx.coroutines.CoroutineStart;
import org.huxerui.clion.PlatformNames;
import org.huxerui.clion.settings.HuxerUISettings;
import org.jetbrains.annotations.NotNull;

import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.event.DocumentEvent;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public abstract class HuxerUIDirectoryProjectGenerator
        extends CLionProjectGenerator<HuxerUIDirectoryProjectGenerator.Settings> {
    private static final Logger log = Logger.getInstance(HuxerUIDirectoryProjectGenerator.class);
    private static final Icon icon = IconLoader.getIcon("/icons/huxerui.svg", HuxerUIDirectoryProjectGenerator.class);
    private final boolean application_;
    private final Peer peer_;

    protected HuxerUIDirectoryProjectGenerator(boolean application) {
        application_ = application;
        peer_ = new Peer(application);
    }

    record Settings(List<String> platforms, String project_id) {
        Settings {
            platforms = List.copyOf(platforms);
            project_id = project_id == null ? "" : project_id.trim();
        }
    }

    record CMakeProfilePlan(String name, boolean enabled, String generation_options) {}

    @Override
    public final @NotNull String getGroupName() {
        return "HuxerUI";
    }

    @Override
    public final @NotNull String getGroupDisplayName() {
        return "HuxerUI";
    }

    @Override
    public final int getGroupOrder() {
        return 800;
    }

    @Override
    public final @NotNull String getName() {
        return application_ ? "HuxerUI App" : "HuxerUI Module";
    }

    @Override
    public final @NotNull String getDescription() {
        return application_
                ? "Create a cross-platform HuxerUI application"
                : "Create a compile-time HuxerUI module with a preview application";
    }

    @Override
    public final @NotNull Icon getLogo() {
        return icon;
    }

    @Override
    public final @NotNull ProjectGeneratorPeer<Settings> createPeer() {
        return peer_;
    }

    @Override
    public final @NotNull JComponent getSettingsPanel() {
        return peer_.component_;
    }

    @Override
    public final @NotNull ValidationResult validate(@NotNull String base_dir_path) {
        if (base_dir_path.isBlank()) {
            return new ValidationResult("Enter a HuxerUI project location.");
        }
        try {
            Path path = Path.of(base_dir_path);
            Path file_name = path.getFileName();
            if (file_name == null || !IsValidProjectName(file_name.toString(), application_)) {
                return new ValidationResult(application_
                        ? "App name must start with a letter and contain letters, digits, '_' or '-'."
                        : "Module name must contain non-empty letter or digit segments separated by '_' or '-'.");
            }
        } catch (InvalidPathException error) {
            return new ValidationResult("Enter a valid HuxerUI project location.");
        }
        return ValidationResult.OK;
    }

    @Override
    public final void generateProject(@NotNull Project project, @NotNull VirtualFile base_dir,
            @NotNull Settings settings, @NotNull Module module) {
        super.generateProject(project, base_dir, settings, module);
        Path destination = Path.of(base_dir.getPath());
        String project_name = project.getName();
        Runnable generation = () -> Generate(project, destination, project_name, settings);

        try {
            ProgressIndicator current = ProgressManager.getInstance().getProgressIndicator();
            if (current != null) {
                generation.run();
                return;
            }
            ProgressManager.getInstance().runProcessWithProgressSynchronously(
                    generation, "Create " + getName(), true, project);
        } catch (ProcessCanceledException ignored) {
            // The project wizard owns cancellation and cleanup of the newly created project.
        } catch (RuntimeException error) {
            log.warn(getName() + " creation failed", error);
            com.intellij.openapi.ui.Messages.showErrorDialog(project, RootMessage(error), "Create " + getName());
        }
    }

    private void Generate(Project project, Path destination, String project_name, Settings settings) {
        ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
        if (indicator == null) {
            throw new IllegalStateException("HuxerUI project generation has no progress indicator");
        }
        try {
            if (application_) {
                HuxerUIProjectGenerator.CreateApplication(
                        destination, project_name, settings.project_id(), settings.platforms(), indicator);
            } else {
                HuxerUIProjectGenerator.CreateModule(destination, project_name, settings.platforms(), indicator);
            }
            VirtualFile root = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(destination);
            if (root != null) {
                root.refresh(false, true);
            }
            ConfigureCMake(project, destination, settings.platforms());
        } catch (ProcessCanceledException error) {
            throw error;
        } catch (Exception error) {
            throw new IllegalStateException(getName() + " creation failed: " + RootMessage(error), error);
        }
    }

    static void ConfigureCMake(Project project, Path destination, List<String> platforms) throws Exception {
        String sdk_home = HuxerUISettings.getInstance().RequireSdkHome().toString();
        CMakeSettings settings = CMakeSettings.getInstance(project);
        Path emscripten_toolchain = platforms.contains("web") ? FindEmscriptenToolchain() : null;
        settings.setProfiles(CreateCMakeProfiles(
                settings.getProfiles(), platforms, sdk_home, HuxerUIProjectService.HostPlatformId(),
                emscripten_toolchain));
        LinkCMakeProject(project, destination);
    }

    private static List<CMakeSettings.Profile> CreateCMakeProfiles(List<CMakeSettings.Profile> existing,
            List<String> platforms, String sdk_home, String host_platform, Path emscripten_toolchain) {
        CMakeSettings.Profile template = existing.isEmpty()
                ? new CMakeSettings.Profile()
                : existing.stream().filter(CMakeSettings.Profile::getEnabled).findFirst().orElse(existing.get(0));
        Map<String, String> environment = new HashMap<>(template.getAdditionalEnvironment());
        environment.remove("HUXERUI_SDK_ROOT");
        environment.put("HUXERUI_HOME", sdk_home);
        CMakeSettings.Profile base = template
                .withBuildType("Debug")
                .withEnvironment(true, environment)
                .withEnabled(true);

        List<CMakeSettings.Profile> configured = new ArrayList<>(2);
        for (CMakeProfilePlan plan : PlanCMakeProfiles(platforms, host_platform, emscripten_toolchain)) {
            configured.add(base
                    .withName(plan.name())
                    .withGenerationOptions(MergeGenerationOptions(
                            base.getGenerationOptions(), plan.generation_options()))
                    .withEnabled(plan.enabled()));
        }
        return List.copyOf(configured);
    }

    static String MergeGenerationOptions(String existing, String additional) {
        String normalized_existing = existing == null ? "" : existing.strip();
        String normalized_additional = additional == null ? "" : additional.strip();
        if (normalized_existing.isEmpty()) {
            return normalized_additional;
        }
        if (normalized_additional.isEmpty()) {
            return normalized_existing;
        }
        return normalized_existing + " " + normalized_additional;
    }

    static List<CMakeProfilePlan> PlanCMakeProfiles(
            List<String> platforms, String host_platform, Path emscripten_toolchain) {
        List<CMakeProfilePlan> configured = new ArrayList<>(2);
        if (platforms.contains(host_platform)) {
            configured.add(new CMakeProfilePlan(
                    ProfileName(PlatformNames.DisplayName(host_platform)), true, ""));
        }
        if (platforms.contains("web")) {
            if (emscripten_toolchain == null || !Files.isRegularFile(emscripten_toolchain)) {
                throw new IllegalStateException(
                        "HuxerUI Web requires an Emscripten toolchain; ensure em-config is available on PATH");
            }
            String toolchain_option = "-DCMAKE_TOOLCHAIN_FILE="
                    + QuoteGenerationOptionValue(emscripten_toolchain.toString());
            configured.add(new CMakeProfilePlan(ProfileName("Web"), true, toolchain_option));
        }
        if (configured.isEmpty()) {
            configured.add(new CMakeProfilePlan("HuxerUI Native Builds", false, ""));
        }
        return List.copyOf(configured);
    }

    private static String ProfileName(String platform) {
        return "HuxerUI " + platform + " Debug";
    }

    private static String QuoteGenerationOptionValue(String value) {
        if (value.chars().noneMatch(Character::isWhitespace)) {
            return value;
        }
        return '"' + value.replace("\"", "\\\"") + '"';
    }

    private static Path FindEmscriptenToolchain() throws Exception {
        GeneralCommandLine command = new GeneralCommandLine("em-config", "EMSCRIPTEN_ROOT");
        command.withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE);
        ProcessOutput output = new CapturingProcessHandler(command).runProcess(10_000);
        if (output.isTimeout()) {
            throw new IllegalStateException(
                    "HuxerUI Web timed out while locating the Emscripten toolchain with em-config");
        }
        if (output.getExitCode() != 0) {
            String message = (output.getStderr() + output.getStdout()).strip();
            throw new IllegalStateException(message.isEmpty()
                    ? "HuxerUI Web cannot locate the Emscripten toolchain with em-config"
                    : "HuxerUI Web cannot locate the Emscripten toolchain: " + message);
        }
        String root = output.getStdout().strip();
        if (root.isEmpty()) {
            throw new IllegalStateException("HuxerUI Web em-config returned an empty Emscripten root");
        }
        Path toolchain = Path.of(root, "cmake", "Modules", "Platform", "Emscripten.cmake");
        if (!Files.isRegularFile(toolchain)) {
            throw new IllegalStateException("HuxerUI Web Emscripten CMake toolchain does not exist: " + toolchain);
        }
        return toolchain;
    }

    static boolean IsValidApplicationId(String project_id) {
        return project_id.matches("[a-z][a-z0-9]*(?:\\.[a-z][a-z0-9]*)+");
    }

    private static boolean IsValidProjectName(String project_name, boolean application) {
        return project_name.matches(application
                ? "[A-Za-z][A-Za-z0-9_-]*"
                : "[A-Za-z][A-Za-z0-9]*(?:[-_][A-Za-z0-9]+)*");
    }

    private static void LinkCMakeProject(Project project, Path destination) {
        CMakeWorkspace workspace = CMakeWorkspace.getInstance(project);
        BuildersKt.launch(workspace.coroutineScope, EmptyCoroutineContext.INSTANCE, CoroutineStart.DEFAULT,
                (scope, continuation) -> workspace.linkCMakeProject(destination.toFile(), continuation));
    }

    private static String RootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }

    static final class Peer implements ProjectGeneratorPeer<Settings> {
        private final boolean application_;
        private final JBTextField application_id_ = new JBTextField();
        private final Map<String, JBCheckBox> platforms_ = new LinkedHashMap<>();
        private final JPanel platforms_panel_ = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        private final JPanel component_ = new JPanel(new GridBagLayout());
        private final List<SettingsListener> listeners_ = new ArrayList<>();
        private Runnable check_valid_ = () -> {};

        Peer(boolean application) {
            application_ = application;
            application_id_.setName("huxerui.application_id");
            application_id_.getEmptyText().setText("Optional, for example com.example.myapp");
            application_id_.setToolTipText("Reverse-domain Android package and Apple bundle identifier");
            application_id_.getDocument().addDocumentListener(new DocumentAdapter() {
                @Override
                protected void textChanged(@NotNull DocumentEvent event) {
                    SettingsChanged();
                }
            });
            platforms_panel_.setOpaque(false);
            for (String platform : HuxerUIProjectService.all_platforms) {
                JBCheckBox check_box = new JBCheckBox(PlatformNames.DisplayName(platform), true);
                check_box.setName("huxerui.platform." + platform);
                check_box.addActionListener(event -> SettingsChanged());
                platforms_.put(platform, check_box);
                platforms_panel_.add(check_box);
            }
            BuildComponent();
        }

        @Override
        public @NotNull JComponent getComponent(
                @NotNull TextFieldWithBrowseButton location_field, @NotNull Runnable check_valid) {
            check_valid_ = check_valid;
            return component_;
        }

        @Override
        public void buildUI(@NotNull SettingsStep settings_step) {
            settings_step.addSettingsComponent(component_);
        }

        @Override
        public @NotNull Settings getSettings() {
            return new Settings(SelectedPlatforms(), application_ ? application_id_.getText() : "");
        }

        @Override
        public ValidationInfo validate() {
            String project_id = application_id_.getText().trim();
            if (application_ && !project_id.isEmpty() && !IsValidApplicationId(project_id)) {
                return new ValidationInfo(
                        "Application ID must be a lowercase reverse-domain identifier, for example com.example.myapp.",
                        application_id_);
            }
            if (application_ && SelectedPlatforms().isEmpty()) {
                return new ValidationInfo("An application requires at least one platform.", platforms_panel_);
            }
            return null;
        }

        @Override
        public boolean isBackgroundJobRunning() {
            return false;
        }

        @Override
        public void addSettingsListener(@NotNull SettingsListener listener) {
            listeners_.add(listener);
        }

        private void BuildComponent() {
            int row = 0;
            if (application_) {
                AddLabel("Application ID:", row, true);
                GridBagConstraints id = FieldConstraints(row, true);
                component_.add(application_id_, id);
                ++row;
            }

            AddLabel("Platforms:", row, false);
            GridBagConstraints platforms = FieldConstraints(row, false);
            component_.add(platforms_panel_, platforms);
        }

        private void AddLabel(String text, int row, boolean bottom_inset) {
            GridBagConstraints label = new GridBagConstraints();
            label.gridx = 0;
            label.gridy = row;
            label.anchor = GridBagConstraints.WEST;
            label.insets = new Insets(0, 0, bottom_inset ? 8 : 0, 12);
            component_.add(new JLabel(text), label);
        }

        private static GridBagConstraints FieldConstraints(int row, boolean bottom_inset) {
            GridBagConstraints field = new GridBagConstraints();
            field.gridx = 1;
            field.gridy = row;
            field.weightx = 1.0;
            field.fill = GridBagConstraints.HORIZONTAL;
            field.anchor = GridBagConstraints.WEST;
            field.insets = new Insets(0, 0, bottom_inset ? 8 : 0, 0);
            return field;
        }

        private void SettingsChanged() {
            String project_id = application_id_.getText().trim();
            boolean valid = (!application_ || !SelectedPlatforms().isEmpty())
                    && (!application_ || project_id.isEmpty() || IsValidApplicationId(project_id));
            listeners_.forEach(listener -> listener.stateChanged(valid));
            check_valid_.run();
        }

        private List<String> SelectedPlatforms() {
            List<String> selected = new ArrayList<>();
            platforms_.forEach((platform, check_box) -> {
                if (check_box.isSelected()) {
                    selected.add(platform);
                }
            });
            return selected;
        }
    }
}

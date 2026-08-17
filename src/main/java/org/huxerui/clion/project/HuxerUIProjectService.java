package org.huxerui.clion.project;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.huxerui.clion.cli.HuxerUICommand;
import org.huxerui.clion.run.DeviceOutputParser;
import org.huxerui.clion.run.DeviceSelectorAction;
import org.huxerui.clion.run.HuxerUIDevice;
import org.huxerui.clion.settings.HuxerUISettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Service(Service.Level.PROJECT)
@State(name = "HuxerUIProject", storages = @Storage("huxerui.xml"))
public final class HuxerUIProjectService implements PersistentStateComponent<HuxerUIProjectService.StateData> {
    public static final List<String> all_platforms = List.of("android", "ios", "windows", "macos", "linux", "web");

    enum ProjectKind { NONE, APPLICATION, MODULE }

    public static final class StateData {
        public String platform = "";
        public String device_id = "";
        public String device_name = "";
        public String profile = "debug";
    }

    private final Project project_;
    private StateData state_ = new StateData();
    private volatile List<HuxerUIDevice> devices_ = List.of();
    private final AtomicBoolean refresh_scheduled_ = new AtomicBoolean();
    private final AtomicBoolean sdk_prompt_shown_ = new AtomicBoolean();

    public HuxerUIProjectService(Project project) {
        project_ = project;
    }

    public static HuxerUIProjectService Get(Project project) {
        return project.getService(HuxerUIProjectService.class);
    }

    @Override
    public @Nullable StateData getState() {
        return state_;
    }

    @Override
    public void loadState(@NotNull StateData state) {
        XmlSerializerUtil.copyBean(state, state_);
    }

    public boolean IsProject() {
        String base_path = project_.getBasePath();
        return base_path != null && FindProjectKind(Path.of(base_path)) != ProjectKind.NONE;
    }

    public List<String> EnabledPlatforms() {
        Path root = FindApplicationRoot(HuxerUICommand.ProjectRoot(project_));
        if (root == null) {
            return List.of();
        }
        Path platforms = root.resolve("platform");
        List<String> result = new ArrayList<>();
        for (String platform : all_platforms) {
            if (Files.isDirectory(platforms.resolve(platform))) {
                result.add(platform);
            }
        }
        return result;
    }

    public static @Nullable Path FindApplicationRoot(Path project_root) {
        ProjectKind root_kind = FindProjectKind(project_root);
        if (root_kind == ProjectKind.APPLICATION) {
            return project_root;
        }
        Path preview = project_root.resolve("examples/preview");
        return root_kind == ProjectKind.MODULE && FindProjectKind(preview) == ProjectKind.APPLICATION ? preview : null;
    }

    public static @Nullable Path FindResourceRoot(Path project_root, @Nullable Path source_file) {
        ProjectKind root_kind = FindProjectKind(project_root);
        if (root_kind == ProjectKind.APPLICATION) {
            return project_root;
        }
        if (root_kind != ProjectKind.MODULE) {
            return null;
        }
        Path preview = project_root.resolve("examples/preview").normalize();
        if (source_file != null && source_file.toAbsolutePath().normalize().startsWith(preview)
                && FindProjectKind(preview) == ProjectKind.APPLICATION) {
            return preview;
        }
        return project_root;
    }

    static ProjectKind FindProjectKind(Path project_root) {
        Path cmake = project_root.resolve("CMakeLists.txt");
        if (!Files.isRegularFile(cmake)) {
            return ProjectKind.NONE;
        }
        try {
            String content = Files.readString(cmake);
            if (ContainsCMakeCommand(content, "huxerui_add_app")) {
                return ProjectKind.APPLICATION;
            }
            return ContainsCMakeCommand(content, "huxerui_add_module") ? ProjectKind.MODULE : ProjectKind.NONE;
        } catch (IOException ignored) {
            return ProjectKind.NONE;
        }
    }

    static boolean ContainsCMakeCommand(String content, String command) {
        int offset = 0;
        while (offset < content.length()) {
            char character = content.charAt(offset);
            if (character == '#') {
                offset = SkipComment(content, offset);
            } else if (character == '"') {
                offset = SkipQuoted(content, offset);
            } else if (character == '[' && BracketEquals(content, offset) >= 0) {
                offset = SkipBracket(content, offset);
            } else if (IsIdentifierStart(character)) {
                int end = offset + 1;
                while (end < content.length() && IsIdentifierPart(content.charAt(end))) {
                    ++end;
                }
                String identifier = content.substring(offset, end);
                int next = SkipWhitespaceAndComments(content, end);
                if (identifier.equalsIgnoreCase(command) && next < content.length() && content.charAt(next) == '(') {
                    return true;
                }
                offset = end;
            } else {
                ++offset;
            }
        }
        return false;
    }

    private static int SkipWhitespaceAndComments(String content, int offset) {
        int current = offset;
        while (current < content.length()) {
            if (Character.isWhitespace(content.charAt(current))) {
                ++current;
            } else if (content.charAt(current) == '#') {
                current = SkipComment(content, current);
            } else {
                break;
            }
        }
        return current;
    }

    private static int SkipComment(String content, int offset) {
        if (offset + 1 < content.length() && content.charAt(offset + 1) == '['
                && BracketEquals(content, offset + 1) >= 0) {
            return SkipBracket(content, offset + 1);
        }
        int newline = content.indexOf('\n', offset + 1);
        return newline < 0 ? content.length() : newline + 1;
    }

    private static int SkipQuoted(String content, int offset) {
        int current = offset + 1;
        while (current < content.length()) {
            if (content.charAt(current) == '\\') {
                current += 2;
            } else if (content.charAt(current) == '"') {
                return current + 1;
            } else {
                ++current;
            }
        }
        return content.length();
    }

    private static int SkipBracket(String content, int offset) {
        int equals = BracketEquals(content, offset);
        if (equals < 0) {
            return offset + 1;
        }
        String closing = "]"
                + "=".repeat(equals) + "]";
        int end = content.indexOf(closing, offset + equals + 2);
        return end < 0 ? content.length() : end + closing.length();
    }

    private static int BracketEquals(String content, int offset) {
        if (offset >= content.length() || content.charAt(offset) != '[') {
            return -1;
        }
        int current = offset + 1;
        while (current < content.length() && content.charAt(current) == '=') {
            ++current;
        }
        return current < content.length() && content.charAt(current) == '[' ? current - offset - 1 : -1;
    }

    private static boolean IsIdentifierStart(char character) {
        return character == '_' || Character.isLetter(character);
    }

    private static boolean IsIdentifierPart(char character) {
        return character == '_' || Character.isLetterOrDigit(character);
    }

    public List<HuxerUIDevice> Devices() {
        return devices_;
    }

    boolean MarkSdkPromptShown() {
        return sdk_prompt_shown_.compareAndSet(false, true);
    }

    void ResetSdkPrompt() {
        sdk_prompt_shown_.set(false);
    }

    public void EnsureDevicesLoaded() {
        if (!devices_.isEmpty() || !HuxerUISettings.getInstance().HasValidSdk()
                || !refresh_scheduled_.compareAndSet(false, true)) {
            return;
        }
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                if (!project_.isDisposed()) {
                    RefreshDevices();
                }
            } catch (Exception ignored) {
                // Explicit refresh reports diagnostics; automatic discovery remains non-intrusive.
            } finally {
                refresh_scheduled_.set(false);
            }
        });
    }

    public List<HuxerUIDevice> RefreshDevices() throws Exception {
        List<HuxerUIDevice> result = new ArrayList<>();
        List<String> enabled = EnabledPlatforms();
        Exception failure = null;
        if (enabled.contains("android")) {
            try {
                result.addAll(DeviceOutputParser.Parse(
                        "android",
                        HuxerUICommand.Capture(HuxerUICommand.ProjectRoot(project_), "devices", "android").getStdout()
                ));
            } catch (Exception error) {
                failure = error;
            }
        }
        if (enabled.contains("ios") && HostPlatformId().equals("macos")) {
            try {
                result.addAll(DeviceOutputParser.Parse(
                        "ios",
                        HuxerUICommand.Capture(HuxerUICommand.ProjectRoot(project_), "devices", "ios").getStdout()
                ));
            } catch (Exception error) {
                if (failure == null) {
                    failure = error;
                } else {
                    failure.addSuppressed(error);
                }
            }
        }
        String desktop = HostPlatformId();
        if (enabled.contains(desktop)) {
            result.add(new HuxerUIDevice(desktop, "local", "This Computer", "ready"));
        }
        if (enabled.contains("web")) {
            result.add(new HuxerUIDevice("web", "chrome", "Chrome", "ready"));
        }
        devices_ = List.copyOf(result);
        boolean selected_available = result.stream().anyMatch(device -> device.IsReady()
                && device.platform().equals(state_.platform)
                && device.id().equals(state_.device_id));
        if (!selected_available) {
            state_.platform = "";
            state_.device_id = "";
            state_.device_name = "";
        }
        ApplicationManager.getApplication().invokeLater(() -> {
            if (!project_.isDisposed() && IsProject()) {
                DeviceSelectorAction.SyncRunConfigurations(project_, devices_);
            }
        });
        if (failure != null) {
            throw failure;
        }
        return devices_;
    }

    public void SelectDevice(HuxerUIDevice device) {
        state_.platform = device.platform();
        state_.device_id = device.id();
        state_.device_name = device.name();
    }

    public HuxerUIDevice SelectedDevice() {
        return new HuxerUIDevice(state_.platform, state_.device_id, state_.device_name, "ready");
    }

    public String Profile() {
        return state_.profile;
    }

    public void SetProfile(String profile) {
        state_.profile = profile.equals("release") ? "release" : "debug";
    }

    public static String HostPlatformId() {
        String os = System.getProperty("os.name", "").toLowerCase();
        return os.contains("win") ? "windows" : os.contains("mac") ? "macos" : "linux";
    }

    public static List<HuxerUIDevice> ImmediateRunDevices(List<String> platforms, String host_platform) {
        List<HuxerUIDevice> devices = new ArrayList<>(2);
        if (platforms.contains(host_platform)) {
            devices.add(new HuxerUIDevice(host_platform, "local", "This Computer", "ready"));
        }
        if (platforms.contains("web")) {
            devices.add(new HuxerUIDevice("web", "chrome", "Chrome", "ready"));
        }
        return List.copyOf(devices);
    }
}

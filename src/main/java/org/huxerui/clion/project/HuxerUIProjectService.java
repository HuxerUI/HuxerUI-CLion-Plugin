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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Service(Service.Level.PROJECT)
@State(name = "HuxerUIProject", storages = @Storage("huxerui.xml"))
public final class HuxerUIProjectService implements PersistentStateComponent<HuxerUIProjectService.StateData> {
    public static final List<String> all_platforms = List.of("android", "ios", "windows", "macos", "linux", "web");

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
        return base_path != null && FindApplicationRoot(Path.of(base_path)) != null;
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
        for (Path candidate : List.of(project_root, project_root.resolve("examples/preview"))) {
            if (Files.isRegularFile(candidate.resolve("CMakeLists.txt"))
                    && Files.isRegularFile(candidate.resolve("src/app.cpp"))
                    && HasGeneratedPlatformShell(candidate.resolve("platform"))) {
                return candidate;
            }
        }
        return null;
    }

    private static boolean HasGeneratedPlatformShell(Path platforms) {
        for (String platform : all_platforms) {
            Path directory = platforms.resolve(platform);
            if (Files.isRegularFile(directory.resolve("huxerui.cmake"))) {
                return true;
            }
            if (platform.equals("ios")
                    && Files.isRegularFile(directory.resolve("App/main.mm"))
                    && Files.isRegularFile(directory.resolve("Config/Base.xcconfig"))) {
                return true;
            }
        }
        return false;
    }

    public List<HuxerUIDevice> Devices() {
        return devices_;
    }

    public void EnsureDevicesLoaded() {
        if (!devices_.isEmpty()
                || HuxerUISettings.getInstance().GetSdkHome().isBlank()
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

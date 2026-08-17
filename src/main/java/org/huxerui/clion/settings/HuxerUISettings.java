package org.huxerui.clion.settings;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Service
@State(name = "HuxerUISettings", storages = @Storage("HuxerUI.xml"))
public final class HuxerUISettings implements PersistentStateComponent<HuxerUISettings.StateData> {
    public static final class StateData {
        public String sdk_home = "";
    }

    private StateData state_ = new StateData();

    public static HuxerUISettings getInstance() {
        return ApplicationManager.getApplication().getService(HuxerUISettings.class);
    }

    @Override
    public @Nullable StateData getState() {
        return state_;
    }

    @Override
    public void loadState(@NotNull StateData state) {
        XmlSerializerUtil.copyBean(state, state_);
    }

    public String GetSdkHome() {
        return state_.sdk_home;
    }

    public void SetSdkHome(String sdk_home) {
        state_.sdk_home = sdk_home == null ? "" : sdk_home.trim();
    }

    public @Nullable Path GetValidSdkHome() {
        if (state_.sdk_home.isBlank()) {
            return null;
        }
        try {
            Path home = Path.of(state_.sdk_home).toAbsolutePath().normalize();
            return SdkLayout.IsValid(home) ? home : null;
        } catch (java.nio.file.InvalidPathException ignored) {
            return null;
        }
    }

    public boolean HasValidSdk() {
        return GetValidSdkHome() != null;
    }

    public Path RequireSdkHome() {
        Path home = GetValidSdkHome();
        if (home != null) {
            return home;
        }
        if (state_.sdk_home.isBlank()) {
            throw new IllegalStateException("HuxerUI SDK is not configured. Install one from Tools | HuxerUI.");
        }
        throw new IllegalStateException("Configured HUXERUI_HOME is not a valid SDK: " + state_.sdk_home);
    }

    public Path RequireCli() {
        Path home = RequireSdkHome();
        Path executable = SdkLayout.FindCli(home);
        if (executable == null) {
            throw new IllegalStateException("Configured HUXERUI_HOME has no built huxerui CLI: " + home);
        }
        return executable;
    }

    public static final class SdkLayout {
        private SdkLayout() {}

        public static boolean IsValid(Path home) {
            return Files.isRegularFile(home.resolve("include/huxerui/huxerui.h"))
                    && (Files.isRegularFile(home.resolve("cmake/HuxerUIApp.cmake")) || HasInstalledConfig(home));
        }

        public static @Nullable Path FindCli(Path home) {
            String executable = System.getProperty("os.name", "").toLowerCase().contains("win")
                    ? "huxerui.exe"
                    : "huxerui";
            for (String relative : List.of(
                    "bin/" + executable,
                    "build/bin/" + executable,
                    "cmake-build-debug/bin/" + executable,
                    "cmake-build-release/bin/" + executable
            )) {
                Path candidate = home.resolve(relative);
                if (Files.isRegularFile(candidate)) {
                    return candidate;
                }
            }
            return null;
        }

        private static boolean HasInstalledConfig(Path home) {
            for (String directory : List.of("lib", "lib64", "share")) {
                if (Files.isRegularFile(home.resolve(directory).resolve("cmake/HuxerUI/HuxerUIConfig.cmake"))) {
                    return true;
                }
            }
            Path library = home.resolve("lib");
            if (!Files.isDirectory(library)) {
                return false;
            }
            try (var children = Files.list(library)) {
                return children.anyMatch(path ->
                        Files.isRegularFile(path.resolve("cmake/HuxerUI/HuxerUIConfig.cmake"))
                );
            } catch (java.io.IOException ignored) {
                return false;
            }
        }
    }
}

package org.huxerui.clion.run;

import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.jetbrains.cidr.cpp.execution.CMakeAppRunConfiguration;
import com.jetbrains.cidr.cpp.execution.CMakeAppRunConfigurationType;
import org.huxerui.clion.PlatformNames;
import org.huxerui.clion.cli.HuxerUICommand;
import org.jdom.Element;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class HuxerUICMakeRunConfiguration {
    private static final Pattern project_expression = Pattern.compile(
            "(?m)^\\s*project\\s*\\(\\s*([A-Za-z_][A-Za-z0-9_.+-]*)");
    private static final Pattern target_expression = Pattern.compile(
            "(?m)^\\s*huxerui_add_app\\s*\\(\\s*([A-Za-z_][A-Za-z0-9_.+-]*)");

    private HuxerUICMakeRunConfiguration() {}

    static @Nullable RunnerAndConfigurationSettings Ensure(
            RunManager manager,
            com.intellij.openapi.project.Project project,
            HuxerUIDevice device
    ) {
        if (!device.platform().equals(org.huxerui.clion.project.HuxerUIProjectService.HostPlatformId())) {
            return null;
        }
        CMakeIdentity identity;
        try {
            identity = ParseCMakeIdentity(Files.readString(
                    HuxerUICommand.ProjectRoot(project).resolve("CMakeLists.txt")));
        } catch (IOException error) {
            return null;
        }
        if (identity == null) {
            return null;
        }

        String name = DeviceSelectorAction.ConfigurationName(device);
        RunnerAndConfigurationSettings settings = manager.getAllSettings().stream()
                .filter(item -> item.getName().equals(name)
                        && item.getConfiguration() instanceof CMakeAppRunConfiguration)
                .findFirst()
                .orElseGet(() -> {
                    RunnerAndConfigurationSettings created = manager.createConfiguration(
                            name, CMakeAppRunConfigurationType.getInstance().getFactory());
                    manager.addConfiguration(created);
                    return created;
                });
        settings.setName(name);
        CMakeAppRunConfiguration configuration = (CMakeAppRunConfiguration) settings.getConfiguration();
        Configure(configuration, identity, device.platform());
        return settings;
    }

    static @Nullable CMakeIdentity ParseCMakeIdentity(String cmake) {
        Matcher project = project_expression.matcher(cmake);
        Matcher target = target_expression.matcher(cmake);
        if (!project.find() || !target.find()) {
            return null;
        }
        return new CMakeIdentity(project.group(1), target.group(1));
    }

    private static void Configure(
            CMakeAppRunConfiguration configuration,
            CMakeIdentity identity,
            String platform
    ) {
        try {
            Element state = new Element("configuration");
            configuration.writeExternal(state);
            state.setAttribute("PROJECT_NAME", identity.project());
            state.setAttribute("TARGET_NAME", identity.target());
            state.setAttribute("CONFIG_NAME", "HuxerUI " + PlatformNames.DisplayName(platform) + " Debug");
            state.setAttribute("RUN_TARGET_PROJECT_NAME", identity.project());
            state.setAttribute("RUN_TARGET_NAME", identity.target());
            configuration.readExternal(state);
            configuration.setExplicitBuildTargetName(identity.target());
        } catch (com.intellij.openapi.util.InvalidDataException
                 | com.intellij.openapi.util.WriteExternalException ignored) {
            // The generated CMake configuration remains usable with CLion's current active profile.
        }
    }

    record CMakeIdentity(String project, String target) {}
}

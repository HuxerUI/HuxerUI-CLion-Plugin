package org.huxerui.clion.cli;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.project.Project;
import org.huxerui.clion.settings.HuxerUISettings;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class HuxerUICommand {
    private HuxerUICommand() {}

    public static GeneralCommandLine Create(Path working_directory, List<String> arguments) {
        Path sdk_home = HuxerUISettings.getInstance().RequireSdkHome();
        GeneralCommandLine command = new GeneralCommandLine(HuxerUISettings.getInstance().RequireCli().toString());
        command.withWorkDirectory(working_directory.toFile());
        command.withEnvironment("HUXERUI_SDK_ROOT", sdk_home.toString());
        command.addParameters(arguments);
        command.withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE);
        return command;
    }

    public static ProcessOutput Capture(Path working_directory, String... arguments) throws Exception {
        List<String> values = new ArrayList<>(List.of(arguments));
        ProcessOutput output = new CapturingProcessHandler(Create(working_directory, values)).runProcess(120_000);
        if (output.isTimeout()) {
            throw new IllegalStateException("HuxerUI command timed out");
        }
        if (output.getExitCode() != 0) {
            String message = output.getStderr().isBlank() ? output.getStdout() : output.getStderr();
            throw new IllegalStateException(message.strip());
        }
        return output;
    }

    public static Path ProjectRoot(Project project) {
        String base_path = project.getBasePath();
        if (base_path == null) {
            throw new IllegalStateException("The project has no local base directory");
        }
        return Path.of(base_path);
    }
}

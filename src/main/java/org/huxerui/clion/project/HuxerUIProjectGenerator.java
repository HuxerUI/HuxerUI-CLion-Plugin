package org.huxerui.clion.project;

import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessListener;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.Key;
import org.huxerui.clion.cli.HuxerUICommand;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class HuxerUIProjectGenerator {
    private static final int creation_timeout_ms = 120_000;

    private HuxerUIProjectGenerator() {}

    static void CreateApplication(Path destination, String project_name, String project_id, List<String> platforms,
            ProgressIndicator indicator) throws Exception {
        if (platforms.isEmpty()) {
            throw new IllegalArgumentException("A HuxerUI application requires at least one platform");
        }
        CreateProject(destination, project_name, "app", project_id, platforms, indicator);
    }

    static void CreateModule(Path destination, String project_name, List<String> platforms,
            ProgressIndicator indicator) throws Exception {
        if (!project_name.matches("[A-Za-z][A-Za-z0-9]*(?:[-_][A-Za-z0-9]+)*")) {
            throw new IllegalArgumentException(
                    "Module name must contain non-empty letter or digit segments separated by '-' or '_'");
        }
        CreateProject(destination, project_name, "module", "", platforms, indicator);
    }

    static List<String> CreateArguments(String kind, String project_name, String project_id, List<String> platforms) {
        List<String> arguments = new ArrayList<>(List.of("create", kind, project_name));
        if (!project_id.isBlank()) {
            arguments.add("--id");
            arguments.add(project_id.trim());
        }
        if (!platforms.isEmpty()) {
            arguments.add("--platform");
            arguments.add(String.join(",", platforms));
        }
        return List.copyOf(arguments);
    }

    private static void CreateProject(Path destination, String project_name, String kind, String project_id,
            List<String> platforms, ProgressIndicator indicator) throws Exception {
        if (!project_name.matches("[A-Za-z][A-Za-z0-9_-]*")) {
            throw new IllegalArgumentException(
                    "Project name must start with a letter and contain only letters, digits, underscores, or hyphens");
        }

        Path parent = RequireParent(destination);
        Path staging = Files.createTempDirectory(parent, "." + project_name + ".huxerui-create-");
        try {
            SetProgress(indicator, 0.05, "Preparing HuxerUI project", destination.toString());
            indicator.checkCanceled();

            CapturingProcessHandler handler = new CapturingProcessHandler(HuxerUICommand.Create(
                    staging, CreateArguments(kind, project_name, project_id, platforms)));
            handler.addProcessListener(new ProcessListener() {
                @Override
                public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key output_type) {
                    String line = event.getText().strip();
                    if (!line.isBlank()) {
                        indicator.setText("Generating HuxerUI project — " + line);
                    }
                }
            });

            SetProgress(indicator, 0.20, "Generating HuxerUI " + kind, "Running huxerui create " + kind);
            ProcessOutput output = handler.runProcessWithProgressIndicator(indicator, creation_timeout_ms, true);
            if (output.isCancelled()) {
                throw new ProcessCanceledException();
            }
            if (output.isTimeout()) {
                throw new IOException("HuxerUI project creation timed out after 120 seconds");
            }
            if (output.getExitCode() != 0) {
                String message = (output.getStderr() + output.getStdout()).strip();
                throw new IOException(message.isBlank() ? "huxerui create " + kind + " failed" : message);
            }

            SetProgress(indicator, 0.85, "Installing generated project files", destination.toString());
            indicator.checkCanceled();
            ProjectTreeInstaller.Install(staging.resolve(project_name), destination);
            SetProgress(indicator, 1.0, "HuxerUI " + kind + " created", destination.toString());
        } finally {
            ProjectTreeInstaller.DeleteTree(staging);
        }
    }

    private static Path RequireParent(Path destination) throws IOException {
        Path parent = destination.toAbsolutePath().normalize().getParent();
        if (parent == null || !Files.isDirectory(parent)) {
            throw new IOException("HuxerUI project parent directory does not exist: " + destination);
        }
        return parent;
    }

    private static void SetProgress(ProgressIndicator indicator, double fraction, String text, String detail) {
        indicator.setIndeterminate(false);
        indicator.setFraction(fraction);
        indicator.setText(text + " — " + detail);
    }
}

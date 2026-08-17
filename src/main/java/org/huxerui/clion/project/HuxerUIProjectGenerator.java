package org.huxerui.clion.project;

import com.intellij.openapi.progress.ProgressIndicator;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Locale;

final class HuxerUIProjectGenerator {
    private static final String app_template = "/projectTemplates/app/";
    private static final String module_template = "/projectTemplates/module/";

    private HuxerUIProjectGenerator() {}

    static void CreateApplication(Path destination, String project_name, String project_id, List<String> platforms,
            ProgressIndicator indicator) throws Exception {
        if (platforms.isEmpty()) {
            throw new IllegalArgumentException("A HuxerUI application requires at least one platform");
        }
        if (!project_name.matches("[A-Za-z][A-Za-z0-9_-]*")) {
            throw new IllegalArgumentException("Project name must start with a letter and contain only "
                    + "letters, digits, underscores, or hyphens");
        }
        String target_name = project_name.toLowerCase(Locale.ROOT).replace('-', '_');
        String resolved_id = project_id.isBlank()
                ? "com.example." + project_name.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT)
                : project_id.trim();
        if (!HuxerUIDirectoryProjectGenerator.IsValidApplicationId(resolved_id)) {
            throw new IllegalArgumentException("Application ID must be a lowercase reverse-domain "
                    + "identifier with letter-prefixed segments");
        }
        TemplateContext context = new TemplateContext(project_name, target_name, resolved_id, "", "", "", "");
        CreateProject(destination, project_name, app_template, context, platforms, indicator, false);
    }

    static void CreateModule(Path destination, String project_name, List<String> platforms, ProgressIndicator indicator)
            throws Exception {
        if (!project_name.matches("[A-Za-z][A-Za-z0-9]*(?:[-_][A-Za-z0-9]+)*")) {
            throw new IllegalArgumentException(
                    "Module name must contain non-empty letter or digit segments separated by '-' or '_'");
        }
        String target_name = NormalizeModuleIdentifier(project_name);
        String product_name = ModuleProductName(project_name);
        String project_id = "com.example." + project_name.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
        TemplateContext context = new TemplateContext(project_name, target_name, project_id, product_name,
                project_name + " Preview", "example_" + target_name, project_id + ".preview");
        CreateProject(destination, project_name, module_template, context, platforms, indicator, true);
    }

    static String NormalizeModuleIdentifier(String name) {
        StringBuilder identifier = new StringBuilder(name.length());
        for (int index = 0; index < name.length(); ++index) {
            char character = name.charAt(index);
            if (character == '-' || character == '_') {
                identifier.append('_');
                continue;
            }
            if (character >= 'A' && character <= 'Z') {
                char previous = index == 0 ? '\0' : name.charAt(index - 1);
                char next = index + 1 == name.length() ? '\0' : name.charAt(index + 1);
                if (!identifier.isEmpty() && previous != '-' && previous != '_'
                        && (IsAsciiLower(previous) || IsAsciiDigit(previous)
                                || (IsAsciiUpper(previous) && IsAsciiLower(next)))) {
                    identifier.append('_');
                }
                identifier.append((char) (character - 'A' + 'a'));
            } else {
                identifier.append(character);
            }
        }
        return identifier.toString();
    }

    static String ModuleProductName(String name) {
        StringBuilder product = new StringBuilder(name.length());
        boolean capitalize = true;
        for (int index = 0; index < name.length(); ++index) {
            char character = name.charAt(index);
            if (character == '-' || character == '_') {
                capitalize = true;
            } else if (capitalize) {
                product.append(IsAsciiLower(character) ? (char) (character - 'a' + 'A') : character);
                capitalize = false;
            } else {
                product.append(character);
            }
        }
        return product.toString();
    }

    private static void CreateProject(Path destination, String project_name, String template_root,
            TemplateContext context, List<String> platforms, ProgressIndicator indicator, boolean module)
            throws Exception {
        Path parent = RequireParent(destination);
        Path staging = Files.createTempDirectory(parent, "." + project_name + ".huxerui-create-");
        try {
            SetProgress(indicator, 0.05, "Preparing HuxerUI project", destination.toString());
            Path generated = staging.resolve(project_name);
            Files.createDirectories(generated);
            WriteTemplate(generated, template_root, context, platforms, indicator, module);
            CreateResourceDirectories(generated);
            if (module) {
                CreateResourceDirectories(generated.resolve("examples/preview"));
            }

            SetProgress(indicator, 0.90, "Installing generated project files", destination.toString());
            indicator.checkCanceled();
            ProjectTreeInstaller.Install(generated, destination);
            SetProgress(indicator, 1.0, "HuxerUI project created", destination.toString());
        } finally {
            ProjectTreeInstaller.DeleteTree(staging);
        }
    }

    private static void WriteTemplate(Path generated, String template_root, TemplateContext context,
            List<String> platforms, ProgressIndicator indicator, boolean module) throws IOException {
        List<String> files =
                ReadResource(template_root + "files.list").lines().filter(line -> !line.isBlank()).toList();
        int written = 0;
        for (String template_path : files) {
            indicator.checkCanceled();
            if (!IncludePlatform(template_path, platforms, module)) {
                continue;
            }
            String relative = context.Render(OutputPath(template_path), module);
            Path output = generated.resolve(relative).normalize();
            if (!output.startsWith(generated) || output.equals(generated)) {
                throw new IOException("HuxerUI project template contains an unsafe path: " + relative);
            }
            Files.createDirectories(output.getParent());
            String content = context.Render(ReadResource(template_root + template_path), module);
            Files.writeString(
                    output, content, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            ++written;
            SetProgress(indicator, 0.10 + 0.75 * written / files.size(), "Generating HuxerUI project", relative);
        }
    }

    private static boolean IncludePlatform(String path, List<String> platforms, boolean module) {
        for (String platform : HuxerUIProjectService.all_platforms) {
            if (path.startsWith("platform/" + platform + "/")
                    || (module && path.startsWith("examples/preview/platform/" + platform + "/"))) {
                return platforms.contains(platform);
            }
        }
        return true;
    }

    private static String OutputPath(String template_path) {
        if (template_path.endsWith("gitignore.template")) {
            return template_path.substring(0, template_path.length() - "gitignore.template".length()) + ".gitignore";
        }
        if (template_path.endsWith("gitkeep.template")) {
            return template_path.substring(0, template_path.length() - "gitkeep.template".length()) + ".gitkeep";
        }
        return template_path;
    }

    private static String ReadResource(String path) throws IOException {
        try (InputStream input = HuxerUIProjectGenerator.class.getResourceAsStream(path)) {
            if (input == null) {
                throw new IOException("Missing HuxerUI project template resource: " + path);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void CreateResourceDirectories(Path root) throws IOException {
        Files.createDirectories(root.resolve("resources/images"));
        Files.createDirectories(root.resolve("resources/raw"));
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

    private static boolean IsAsciiLower(char character) {
        return character >= 'a' && character <= 'z';
    }

    private static boolean IsAsciiUpper(char character) {
        return character >= 'A' && character <= 'Z';
    }

    private static boolean IsAsciiDigit(char character) {
        return character >= '0' && character <= '9';
    }

    private record TemplateContext(String project_name, String target_name, String project_id, String module_product,
            String preview_name, String preview_target, String preview_id) {
        String Render(String value, boolean module) {
            if (!module) {
                return value.replace("dev/example/template", "${HUXERUI_APP_ID_PATH}")
                        .replace("dev.example.template", "${HUXERUI_APP_ID}")
                        .replace("TemplateApp", "${HUXERUI_APP_NAME}")
                        .replace("templateapp", "${HUXERUI_APP_TARGET}")
                        .replace("${HUXERUI_APP_ID_PATH}", project_id.replace('.', '/'))
                        .replace("${HUXERUI_APP_ID}", project_id)
                        .replace("${HUXERUI_APP_NAME}", project_name)
                        .replace("${HUXERUI_APP_TARGET}", target_name);
            }
            return value.replace("dev/example/module/preview", "${HUXERUI_PREVIEW_ID_PATH}")
                    .replace("dev.example.module.preview", "${HUXERUI_PREVIEW_ID}")
                    .replace("example_template_module", "${HUXERUI_PREVIEW_TARGET}")
                    .replace("Template-Module Preview", "${HUXERUI_PREVIEW_NAME}")
                    .replace("TemplateModule", "${HUXERUI_MODULE_PRODUCT}")
                    .replace("Template-Module", "${HUXERUI_MODULE_NAME}")
                    .replace("template_module", "${HUXERUI_MODULE_TARGET}")
                    .replace("dev.example.module", "${HUXERUI_MODULE_ID}")
                    .replace("${HUXERUI_PREVIEW_ID_PATH}", preview_id.replace('.', '/'))
                    .replace("${HUXERUI_PREVIEW_ID}", preview_id)
                    .replace("${HUXERUI_PREVIEW_TARGET}", preview_target)
                    .replace("${HUXERUI_PREVIEW_NAME}", preview_name)
                    .replace("${HUXERUI_MODULE_PRODUCT}", module_product)
                    .replace("${HUXERUI_MODULE_NAME}", project_name)
                    .replace("${HUXERUI_MODULE_TARGET}", target_name)
                    .replace("${HUXERUI_MODULE_ID}", project_id);
        }
    }
}

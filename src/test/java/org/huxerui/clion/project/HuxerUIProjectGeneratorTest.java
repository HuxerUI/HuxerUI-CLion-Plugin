package org.huxerui.clion.project;

import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.progress.ProgressIndicator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class HuxerUIProjectGeneratorTest {
    @Test
    void createsApplicationsFromBundledTemplatesWithoutTheCli(@TempDir Path root) throws Exception {
        Path destination = root.resolve("Hello-Huxer");

        HuxerUIProjectGenerator.CreateApplication(destination, "Hello-Huxer", "dev.example.hello",
                List.of("android", "linux", "web"), new TestProgressIndicator());

        String cmake = Files.readString(destination.resolve("CMakeLists.txt"));
        assertTrue(cmake.contains("huxerui_add_app(hello_huxer"));
        assertTrue(cmake.contains("\"id\": \"dev.example.hello\""));
        assertTrue(Files.isRegularFile(destination.resolve("platform/linux/main.cpp")));
        assertTrue(Files.isRegularFile(destination.resolve("platform/web/index.html.in")));
        assertTrue(Files.isRegularFile(
                destination.resolve("platform/android/app/src/main/java/dev/example/hello/MainActivity.java")));
        assertFalse(Files.exists(destination.resolve("platform/ios")));
        assertTrue(Files.isDirectory(destination.resolve("resources/images")));
        assertTrue(Files.isDirectory(destination.resolve("resources/raw")));
        assertFalse(Files.exists(destination.resolve(".huxerui")));
        AssertNoTemplateTokens(destination, "TemplateApp", "templateapp", "dev.example.template");
    }

    @Test
    void createsModulesAndPreviewApplicationsFromBundledTemplates(@TempDir Path root) throws Exception {
        Path destination = root.resolve("HuxerUI-Camera");

        HuxerUIProjectGenerator.CreateModule(
                destination, "HuxerUI-Camera", List.of("android", "linux"), new TestProgressIndicator());

        String module_cmake = Files.readString(destination.resolve("CMakeLists.txt"));
        String preview_cmake = Files.readString(destination.resolve("examples/preview/CMakeLists.txt"));
        assertTrue(module_cmake.contains("huxerui_add_module(huxer_ui_camera"));
        assertTrue(module_cmake.contains("HuxerUICamera::HuxerUICamera"));
        assertTrue(preview_cmake.contains("huxerui_add_app(example_huxer_ui_camera"));
        assertTrue(Files.isRegularFile(destination.resolve("include/huxer_ui_camera/huxer_ui_camera.h")));
        assertTrue(Files.isRegularFile(destination.resolve("platform/android/build.gradle")));
        assertTrue(Files.isRegularFile(destination.resolve("examples/preview/platform/linux/main.cpp")));
        assertFalse(Files.exists(destination.resolve("platform/ios")));
        assertFalse(Files.exists(destination.resolve(".huxerui")));
        AssertNoTemplateTokens(destination, "Template-Module", "TemplateModule", "template_module",
                "example_template_module", "dev.example.module");
    }

    @Test
    void matchesCliModuleIdentifierNormalization() {
        assertEquals("huxer_ui_camera", HuxerUIProjectGenerator.NormalizeModuleIdentifier("HuxerUI-Camera"));
        assertEquals("HuxerUICamera", HuxerUIProjectGenerator.ModuleProductName("HuxerUI-Camera"));
    }

    @Test
    void preservesNamesAndIdsContainingTemplateWords(@TempDir Path root) throws Exception {
        Path destination = root.resolve("Atemplateapp");

        HuxerUIProjectGenerator.CreateApplication(destination, "Atemplateapp", "dev.templateapp.product",
                List.of("android"), new TestProgressIndicator());

        assertTrue(Files.isDirectory(
                destination.resolve("platform/android/app/src/main/java/dev/templateapp/product")));
        String cmake = Files.readString(destination.resolve("CMakeLists.txt"));
        assertTrue(cmake.contains("\"name\": \"Atemplateapp\""));
        assertTrue(cmake.contains("project(atemplateapp"));
        assertTrue(Files.readString(destination.resolve("platform/android/app/build.gradle"))
                .contains("namespace = \"dev.templateapp.product\""));
    }

    @Test
    void rendersEveryBundledPlatformTemplate(@TempDir Path root) throws Exception {
        List<String> platforms = HuxerUIProjectService.all_platforms;
        Path application = root.resolve("AllPlatformsApp");
        Path module = root.resolve("All-Platforms-Module");

        HuxerUIProjectGenerator.CreateApplication(application, "AllPlatformsApp", "dev.example.allplatforms",
                platforms, new TestProgressIndicator());
        HuxerUIProjectGenerator.CreateModule(
                module, "All-Platforms-Module", platforms, new TestProgressIndicator());

        AssertNoTemplateTokens(application, "TemplateApp", "templateapp", "dev.example.template");
        AssertNoTemplateTokens(module, "Template-Module", "TemplateModule", "template_module",
                "example_template_module", "dev.example.module");
        for (String platform : platforms) {
            assertTrue(Files.isDirectory(application.resolve("platform/" + platform)), platform);
            assertTrue(Files.isDirectory(module.resolve("examples/preview/platform/" + platform)), platform);
        }
        for (String platform : List.of("android", "ios", "linux")) {
            assertTrue(Files.isDirectory(module.resolve("platform/" + platform)), platform);
        }
    }

    private static void AssertNoTemplateTokens(Path root, String... tokens) throws Exception {
        try (var paths = Files.walk(root)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                String relative = root.relativize(path).toString();
                String content = Files.readString(path);
                for (String token : tokens) {
                    assertFalse(relative.contains(token), relative);
                    assertFalse(content.contains(token), path.toString());
                }
            }
        }
    }

    private static final class TestProgressIndicator implements ProgressIndicator {
        private String text_ = "";
        private String text2_ = "";
        private double fraction_;
        private boolean running_;
        private boolean canceled_;
        private boolean indeterminate_;

        @Override
        public void start() {
            running_ = true;
        }

        @Override
        public void stop() {
            running_ = false;
        }

        @Override
        public boolean isRunning() {
            return running_;
        }

        @Override
        public void cancel() {
            canceled_ = true;
        }

        @Override
        public boolean isCanceled() {
            return canceled_;
        }

        @Override
        public void setText(String text) {
            text_ = text;
        }

        @Override
        public String getText() {
            return text_;
        }

        @Override
        public void setText2(String text) {
            text2_ = text;
        }

        @Override
        public String getText2() {
            return text2_;
        }

        @Override
        public double getFraction() {
            return fraction_;
        }

        @Override
        public void setFraction(double fraction) {
            fraction_ = fraction;
        }

        @Override
        public void pushState() {}

        @Override
        public void popState() {}

        @Override
        public boolean isModal() {
            return false;
        }

        @Override
        public ModalityState getModalityState() {
            return ModalityState.any();
        }

        @Override
        public void setModalityProgress(ProgressIndicator modality_progress) {}

        @Override
        public boolean isIndeterminate() {
            return indeterminate_;
        }

        @Override
        public void setIndeterminate(boolean indeterminate) {
            indeterminate_ = indeterminate;
        }

        @Override
        public void checkCanceled() {
            if (canceled_) {
                throw new com.intellij.openapi.progress.ProcessCanceledException();
            }
        }

        @Override
        public boolean isPopupWasShown() {
            return false;
        }

        @Override
        public boolean isShowing() {
            return false;
        }
    }
}

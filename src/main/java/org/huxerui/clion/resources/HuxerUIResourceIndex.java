package org.huxerui.clion.resources;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.huxerui.clion.project.HuxerUIProjectService;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class HuxerUIResourceIndex {
    private static final Pattern string_entry = Pattern.compile("(?m)^\\s*([^#=\\r\\n]+?)\\s*=");

    private HuxerUIResourceIndex() {}

    record Entry(String identifier, VirtualFile file, int offset) {}

    record ParsedEntry(String identifier, int offset) {}

    static List<Entry> Find(Project project, String kind, @Nullable VirtualFile source_file) {
        String base_path = project.getBasePath();
        if (base_path == null) {
            return List.of();
        }
        VirtualFile project_root = LocalFileSystem.getInstance().findFileByNioFile(Path.of(base_path));
        if (project_root == null) {
            return List.of();
        }
        VirtualFile resource_owner = FindResourceOwner(project_root, source_file);
        if (resource_owner == null) {
            return List.of();
        }
        VirtualFile resource_root = resource_owner.findFileByRelativePath("resources/" + kind);
        if (resource_root == null || !resource_root.isDirectory()) {
            return List.of();
        }

        List<Entry> result = new ArrayList<>();
        VfsUtilCore.iterateChildrenRecursively(resource_root, null, file -> {
            if (file.isDirectory()) {
                return true;
            }
            String relative = VfsUtilCore.getRelativePath(file, resource_root, '/');
            if (relative == null) {
                return true;
            }
            String content = "";
            if (kind.equals("strings")) {
                Document document = FileDocumentManager.getInstance().getDocument(file);
                if (document == null) {
                    return true;
                }
                content = document.getText();
            }
            for (ParsedEntry entry : ParseFile(kind, relative, content)) {
                result.add(new Entry(entry.identifier(), file, entry.offset()));
            }
            return true;
        });
        result.sort(Comparator.comparing(entry -> entry.file().getPath()));
        return List.copyOf(result);
    }

    static @Nullable Entry SelectNavigationEntry(List<Entry> entries, String kind) {
        return entries.stream().min(Comparator
                .comparingInt((Entry entry) -> NavigationPriority(kind, entry.file().getName()))
                .thenComparing(entry -> entry.file().getPath())).orElse(null);
    }

    static int NavigationPriority(String kind, String file_name) {
        String normalized = file_name.toLowerCase(Locale.ROOT);
        if (kind.equals("strings")) {
            return normalized.equals("default.properties") ? 0 : 1;
        }
        if (kind.equals("images")) {
            return normalized.matches(".*@[0-9]+(?:\\.[0-9]+)?x\\.[^.]+$") ? 1 : 0;
        }
        return 0;
    }

    private static @Nullable VirtualFile FindResourceOwner(
            VirtualFile project_root, @Nullable VirtualFile source_file) {
        Path root_path = project_root.toNioPath();
        Path resource_path =
                HuxerUIProjectService.FindResourceRoot(root_path, source_file == null ? null : source_file.toNioPath());
        if (resource_path == null) {
            return null;
        }
        Path relative = root_path.relativize(resource_path);
        return relative.toString().isEmpty()
                ? project_root
                : project_root.findFileByRelativePath(relative.toString().replace('\\', '/'));
    }

    static List<ParsedEntry> ParseFile(String kind, String relative, String content) {
        if (kind.equals("strings")) {
            if (!relative.endsWith(".properties")) {
                return List.of();
            }
            List<ParsedEntry> result = new ArrayList<>();
            Matcher entries = string_entry.matcher(content);
            while (entries.find()) {
                result.add(new ParsedEntry(
                        ResourceNameCodec.Identifier(entries.group(1).trim()), entries.start(1)));
            }
            return List.copyOf(result);
        }
        if (kind.equals("images")) {
            String normalized = relative.toLowerCase(Locale.ROOT);
            if (!normalized.endsWith(".png") && !normalized.endsWith(".jpg")
                    && !normalized.endsWith(".jpeg") && !normalized.endsWith(".svg")) {
                return List.of();
            }
            return List.of(new ParsedEntry(
                    ResourceNameCodec.Identifier(ResourceNameCodec.ImageLogicalName(relative)), 0));
        }
        if (kind.equals("raw")) {
            return List.of(new ParsedEntry(ResourceNameCodec.Identifier(relative), 0));
        }
        return List.of();
    }
}

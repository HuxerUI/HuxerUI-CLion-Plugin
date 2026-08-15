package org.huxerui.clion.project;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class ProjectTreeInstaller {
    private ProjectTreeInstaller() {}

    static void Install(Path generated, Path destination) throws IOException {
        if (!Files.isDirectory(generated)) {
            throw new IOException("Generated HuxerUI project does not exist: " + generated);
        }
        Files.createDirectories(destination);

        List<Path> children;
        try (var entries = Files.list(generated)) {
            children = entries.sorted().toList();
        }
        for (Path child : children) {
            Path target = destination.resolve(child.getFileName());
            if (Files.exists(target)) {
                throw new IOException("Project destination already contains: " + target);
            }
        }

        List<Path> installed = new ArrayList<>();
        try {
            for (Path child : children) {
                Path target = destination.resolve(child.getFileName());
                Files.move(child, target);
                installed.add(target);
            }
        } catch (IOException error) {
            for (int index = installed.size() - 1; index >= 0; --index) {
                Path target = installed.get(index);
                try {
                    Files.move(target, generated.resolve(target.getFileName()));
                } catch (IOException rollback_error) {
                    error.addSuppressed(rollback_error);
                }
            }
            throw error;
        }
    }

    static void DeleteTree(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.comparingInt(Path::getNameCount).reversed()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}

package org.huxerui.clion.resources;

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class HuxerUIResourceGotoHandler implements GotoDeclarationHandler {
    private static final Pattern expression = Pattern.compile(
            "(?:[A-Za-z_][A-Za-z0-9_]*::)*(images|strings|raw)::([A-Za-z_][A-Za-z0-9_]*)"
    );
    private static final Pattern string_entry = Pattern.compile("(?m)^\\s*([^#=\\r\\n]+?)\\s*=");

    @Override
    public PsiElement @Nullable [] getGotoDeclarationTargets(
            @Nullable PsiElement source_element,
            int offset,
            Editor editor
    ) {
        if (source_element == null) {
            return null;
        }
        Document document = editor.getDocument();
        ResourceReference reference = FindReference(document.getText(), offset);
        if (reference == null) {
            return null;
        }

        Project project = source_element.getProject();
        List<PsiElement> targets = new ArrayList<>();
        String target_kind = reference.kind();
        String target_identifier = reference.identifier();
        ProjectFileIndex.getInstance(project).iterateContent(file -> {
            String marker = "/resources/" + target_kind + "/";
            String path = file.getPath().replace('\\', '/');
            int resource = path.indexOf(marker);
            if (resource < 0 || file.isDirectory()) {
                return true;
            }
            String relative = path.substring(resource + marker.length());
            if (target_kind.equals("strings")) {
                AddStringTarget(project, file, target_identifier, targets);
            } else {
                String logical = target_kind.equals("images")
                        ? ResourceNameCodec.ImageLogicalName(relative)
                        : relative;
                if (ResourceNameCodec.Identifier(logical).equals(target_identifier)) {
                    PsiFile psi_file = PsiManager.getInstance(project).findFile(file);
                    if (psi_file != null) {
                        targets.add(psi_file);
                    }
                }
            }
            return true;
        });
        return targets.isEmpty() ? null : targets.toArray(PsiElement[]::new);
    }

    @Override
    public @Nullable String getActionText(@Nullable com.intellij.openapi.actionSystem.DataContext context) {
        return "Go to HuxerUI Resource";
    }

    static @Nullable ResourceReference FindReference(String text, int offset) {
        int start = Math.max(0, offset - 256);
        int end = Math.min(text.length(), offset + 128);
        Matcher matcher = expression.matcher(text.substring(start, end));
        while (matcher.find()) {
            int absolute_start = start + matcher.start();
            int absolute_end = start + matcher.end();
            if (offset >= absolute_start && offset <= absolute_end) {
                return new ResourceReference(matcher.group(1), matcher.group(2));
            }
        }
        return null;
    }

    record ResourceReference(String kind, String identifier) {}

    private static void AddStringTarget(
            Project project,
            VirtualFile file,
            String identifier,
            List<PsiElement> targets
    ) {
        if (!file.getName().endsWith(".properties")) {
            return;
        }
        Document document = FileDocumentManager.getInstance().getDocument(file);
        PsiFile psi_file = PsiManager.getInstance(project).findFile(file);
        if (document == null || psi_file == null) {
            return;
        }
        Matcher entries = string_entry.matcher(document.getText());
        while (entries.find()) {
            String key = entries.group(1).trim();
            if (ResourceNameCodec.Identifier(key).equals(identifier)) {
                TextRange range = new TextRange(entries.start(1), entries.end(1));
                PsiElement target = psi_file.findElementAt(range.getStartOffset());
                targets.add(target == null ? psi_file : target);
            }
        }
    }
}

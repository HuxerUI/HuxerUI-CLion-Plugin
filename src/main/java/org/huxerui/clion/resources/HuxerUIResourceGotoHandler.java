package org.huxerui.clion.resources;

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.huxerui.clion.project.HuxerUIProjectService;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class HuxerUIResourceGotoHandler implements GotoDeclarationHandler {
    private static final Pattern expression = Pattern.compile(
            "(?:[A-Za-z_][A-Za-z0-9_]*::)*(images|strings|raw)::([A-Za-z_][A-Za-z0-9_]*)"
    );

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
        if (!HuxerUIProjectService.Get(project).IsProject()) {
            return null;
        }
        PsiFile source_file = source_element.getContainingFile();
        List<HuxerUIResourceIndex.Entry> matches =
                HuxerUIResourceIndex
                        .Find(project, reference.kind(), source_file == null ? null : source_file.getVirtualFile())
                        .stream()
                        .filter(entry -> entry.identifier().equals(reference.identifier()))
                        .toList();
        HuxerUIResourceIndex.Entry entry = HuxerUIResourceIndex.SelectNavigationEntry(matches, reference.kind());
        if (entry == null) {
            return null;
        }
        PsiFile psi_file = PsiManager.getInstance(project).findFile(entry.file());
        if (psi_file == null) {
            return null;
        }
        return new PsiElement[]{new HuxerUIResourceNavigationElement(
                psi_file, reference.identifier(), entry.offset())};
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
}

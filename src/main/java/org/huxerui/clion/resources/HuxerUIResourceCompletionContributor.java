package org.huxerui.clion.resources;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.project.DumbAware;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.util.ProcessingContext;
import org.huxerui.clion.project.HuxerUIProjectService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class HuxerUIResourceCompletionContributor extends CompletionContributor implements DumbAware {
    private static final Pattern prefix = Pattern.compile(
            "(?:[A-Za-z_][A-Za-z0-9_]*::)*(images|strings|raw)::([A-Za-z_][A-Za-z0-9_]*)?$"
    );

    public HuxerUIResourceCompletionContributor() {
        extend(CompletionType.BASIC, PlatformPatterns.psiElement(), new CompletionProvider<>() {
            @Override
            protected void addCompletions(
                    @NotNull CompletionParameters parameters,
                    @NotNull ProcessingContext context,
                    @NotNull CompletionResultSet result
            ) {
                var project = parameters.getPosition().getProject();
                if (!HuxerUIProjectService.Get(project).IsProject()) {
                    return;
                }
                ResourcePrefix resource = FindPrefix(
                        parameters.getEditor().getDocument().getText(), parameters.getOffset());
                if (resource == null) {
                    return;
                }
                Set<String> identifiers = new TreeSet<>();
                for (HuxerUIResourceIndex.Entry entry : HuxerUIResourceIndex.Find(project, resource.kind())) {
                    identifiers.add(entry.identifier());
                }
                CompletionResultSet matching = result.withPrefixMatcher(resource.identifier());
                for (String identifier : identifiers) {
                    matching.addElement(LookupElementBuilder.create(identifier)
                            .withTypeText("HuxerUI " + resource.kind(), true));
                }
            }
        });
    }

    static @Nullable ResourcePrefix FindPrefix(String text, int offset) {
        if (offset < 0 || offset > text.length()) {
            return null;
        }
        int start = Math.max(0, offset - 256);
        Matcher matcher = prefix.matcher(text.substring(start, offset));
        if (!matcher.find()) {
            return null;
        }
        return new ResourcePrefix(matcher.group(1), matcher.group(2) == null ? "" : matcher.group(2));
    }

    record ResourcePrefix(String kind, String identifier) {}
}

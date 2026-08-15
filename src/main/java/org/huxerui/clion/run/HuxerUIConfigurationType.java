package org.huxerui.clion.run;

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationTypeBase;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NotNullLazyValue;
import org.jetbrains.annotations.NotNull;

public final class HuxerUIConfigurationType extends ConfigurationTypeBase {
    public static final String id = "HuxerUIRunConfiguration";

    public HuxerUIConfigurationType() {
        super(
                id,
                "HuxerUI",
                "Run a HuxerUI application on the selected device",
                NotNullLazyValue.createValue(() -> AllIcons.Actions.Execute)
        );
        addFactory(new ConfigurationFactory(this) {
            @Override
            public @NotNull HuxerUIRunConfiguration createTemplateConfiguration(@NotNull Project project) {
                return new HuxerUIRunConfiguration(project, this, "HuxerUI");
            }

            @Override
            public boolean isApplicable(@NotNull Project project) {
                return org.huxerui.clion.project.HuxerUIProjectService.Get(project).IsProject();
            }

            @Override
            public @NotNull String getId() {
                return HuxerUIConfigurationType.id;
            }
        });
    }
}

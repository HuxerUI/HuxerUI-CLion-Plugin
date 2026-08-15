package org.huxerui.clion.project;

import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class HuxerUIProjectGeneratorTest {
    @Test
    void createsApplicationsThroughTheCurrentCliContract() {
        assertEquals(
                List.of(
                        "create", "app", "Hello-Huxer", "--id", "dev.example.hello",
                        "--platform", "android,linux,web"),
                HuxerUIProjectGenerator.CreateArguments(
                        "app", "Hello-Huxer", "dev.example.hello", List.of("android", "linux", "web"))
        );
    }

    @Test
    void createsModulesThroughTheCurrentCliContract() {
        assertEquals(
                List.of("create", "module", "HuxerUI-Camera", "--platform", "android,linux"),
                HuxerUIProjectGenerator.CreateArguments(
                        "module", "HuxerUI-Camera", "", List.of("android", "linux"))
        );
        assertEquals(
                List.of("create", "module", "HuxerUI-Camera"),
                HuxerUIProjectGenerator.CreateArguments("module", "HuxerUI-Camera", "", List.of())
        );
    }
}

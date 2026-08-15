package org.huxerui.clion.run;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class HuxerUIRunConfigurationTest {
    @Test
    void delegatesWebRunToTheCli() {
        assertEquals(
                List.of("run", "web", "--profile", "debug"),
                HuxerUIRunConfiguration.BuildRunArguments("web", "chrome", "debug"));
    }

    @Test
    void forwardsPhysicalDeviceIdsOnlyForMobilePlatforms() {
        assertEquals(
                List.of("run", "android", "--profile", "release", "--device", "device-1"),
                HuxerUIRunConfiguration.BuildRunArguments("android", "device-1", "release"));
        assertEquals(
                List.of("run", "linux", "--profile", "debug"),
                HuxerUIRunConfiguration.BuildRunArguments("linux", "local", "debug"));
    }
}

package org.huxerui.clion.run;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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

    @Test
    void givesEveryRunTargetAnIndependentName() {
        assertEquals(
                "HuxerUI Linux",
                DeviceSelectorAction.ConfigurationName(new HuxerUIDevice(
                        "linux", "local", "This Computer", "ready")));
        assertEquals(
                "HuxerUI Web",
                DeviceSelectorAction.ConfigurationName(new HuxerUIDevice(
                        "web", "chrome", "Chrome", "ready")));
        assertEquals(
                "HuxerUI Android — Pixel 9",
                DeviceSelectorAction.ConfigurationName(new HuxerUIDevice(
                        "android", "emulator-5554", "Pixel 9", "ready")));
    }

    @Test
    void readsTheNativeCMakeApplicationIdentity() {
        assertEquals(
                new HuxerUICMakeRunConfiguration.CMakeIdentity("sample", "sample_app"),
                HuxerUICMakeRunConfiguration.ParseCMakeIdentity("""
                        project(sample VERSION 1.0 LANGUAGES CXX)
                        huxerui_add_app(sample_app SOURCES src/app.cpp)
                        """));
        assertNull(HuxerUICMakeRunConfiguration.ParseCMakeIdentity("project(sample LANGUAGES CXX)"));
    }
}

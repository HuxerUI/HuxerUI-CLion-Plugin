package org.huxerui.clion.run;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class DeviceOutputParserTest {
    @Test
    void parsesCliDeviceOutput() {
        String output = "Platform android:\n"
                + "  [ready] emulator-5554 (Pixel_8)\n"
                + "  [unauthorized] ABC\n";
        assertEquals(
                List.of(
                        new HuxerUIDevice("android", "emulator-5554", "Pixel_8", "ready"),
                        new HuxerUIDevice("android", "ABC", "", "unauthorized")
                ),
                DeviceOutputParser.Parse("android", output)
        );
    }

    @Test
    void displaysApplePlatformNamesWithOfficialCapitalization() {
        assertEquals("iOS — iPhone", new HuxerUIDevice("ios", "iphone", "iPhone", "ready").DisplayName());
        assertEquals("macOS — This Mac", new HuxerUIDevice("macos", "local", "This Mac", "ready").DisplayName());
    }
}

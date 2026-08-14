package org.huxerui.clion.run;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DeviceOutputParser {
    private static final Pattern device_line = Pattern.compile("^\\s*\\[([^]]+)]\\s+(\\S+)(?:\\s+\\((.*)\\))?\\s*$");

    private DeviceOutputParser() {}

    public static List<HuxerUIDevice> Parse(String platform, String output) {
        List<HuxerUIDevice> devices = new ArrayList<>();
        for (String line : output.split("\\R")) {
            Matcher match = device_line.matcher(line);
            if (match.matches()) {
                devices.add(new HuxerUIDevice(
                        platform,
                        match.group(2),
                        match.group(3) == null ? "" : match.group(3),
                        match.group(1)
                ));
            }
        }
        return devices;
    }
}

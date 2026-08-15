package org.huxerui.clion.run;

import org.huxerui.clion.PlatformNames;

public record HuxerUIDevice(String platform, String id, String name, String state) {
    public boolean IsReady() {
        return state.equals("ready");
    }

    public String DisplayName() {
        String platform_name = PlatformNames.DisplayName(platform);
        return name.isBlank() ? platform_name + " — " + id : platform_name + " — " + name;
    }
}

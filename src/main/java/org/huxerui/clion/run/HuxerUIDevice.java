package org.huxerui.clion.run;

public record HuxerUIDevice(String platform, String id, String name, String state) {
    public boolean IsReady() {
        return state.equals("ready");
    }

    public String DisplayName() {
        String platform_name = platform.equals("ios")
                ? "iOS"
                : Character.toUpperCase(platform.charAt(0)) + platform.substring(1);
        return name.isBlank() ? platform_name + " — " + id : platform_name + " — " + name;
    }
}

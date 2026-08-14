package org.huxerui.clion.sdk;

import java.util.Locale;

public record HostPlatform(String os, String architecture, String archive_suffix) {
    public static HostPlatform Current() {
        String os_name = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String os = os_name.contains("win") ? "windows" : os_name.contains("mac") ? "macos" : "linux";
        String arch_name = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        String architecture = arch_name.equals("aarch64") || arch_name.equals("arm64") ? "arm64" : "x86_64";
        return new HostPlatform(os, architecture, os.equals("windows") ? ".zip" : ".tar.gz");
    }
}

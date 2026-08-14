package org.huxerui.clion.sdk;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class ReleaseAssetSelector {
    public record Asset(String name, String download_url, String digest, long size) {}

    private ReleaseAssetSelector() {}

    public static Optional<Asset> Select(List<Asset> assets, HostPlatform host) {
        String sdk = "huxerui-sdk-";
        return assets.stream().filter(asset -> {
            String name = asset.name().toLowerCase(Locale.ROOT);
            return name.startsWith(sdk)
                    && name.contains("-" + host.os() + "-")
                    && name.contains("-" + host.architecture())
                    && (name.endsWith(host.archive_suffix()) || name.endsWith(".zip"));
        }).findFirst();
    }
}

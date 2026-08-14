package org.huxerui.clion.sdk;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ReleaseAssetSelectorTest {
    @Test
    void selectsMatchingHostAsset() {
        List<ReleaseAssetSelector.Asset> assets = List.of(
                new ReleaseAssetSelector.Asset("huxerui-sdk-v1-windows-x86_64.zip", "windows", "sha256:a", 1),
                new ReleaseAssetSelector.Asset("huxerui-sdk-v1-linux-x86_64.tar.gz", "linux", "sha256:b", 1)
        );
        assertEquals(
                "linux",
                ReleaseAssetSelector.Select(assets, new HostPlatform("linux", "x86_64", ".tar.gz"))
                        .orElseThrow()
                        .download_url()
        );
    }

    @Test
    void rejectsOtherArchitectures() {
        List<ReleaseAssetSelector.Asset> assets = List.of(
                new ReleaseAssetSelector.Asset("huxerui-sdk-v1-linux-arm64.tar.gz", "arm", "sha256:a", 1)
        );
        assertTrue(ReleaseAssetSelector.Select(assets, new HostPlatform("linux", "x86_64", ".tar.gz")).isEmpty());
    }
}

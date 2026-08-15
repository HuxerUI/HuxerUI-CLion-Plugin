package org.huxerui.clion.sdk;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.util.io.HttpRequests;
import org.huxerui.clion.settings.HuxerUISettings;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class SdkInstaller {
    private static final String latest_release = "https://api.github.com/repos/HuxerUI/HuxerUI/releases/latest";

    public Path InstallLatest(ProgressIndicator indicator) throws IOException {
        indicator.setText("Reading the latest HuxerUI release");
        String body;
        try {
            body = HttpRequests.request(latest_release)
                    .useProxy(true)
                    .followRedirects(true)
                    .connectTimeout(20_000)
                    .readTimeout(30_000)
                    .accept("application/vnd.github+json")
                    .userAgent("HuxerUI-CLion-Plugin")
                    .tuner(connection -> connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28"))
                    .readString(indicator);
        } catch (HttpRequests.HttpStatusException error) {
            if (error.getStatusCode() == 404) {
                throw new IOException("HuxerUI has no published SDK release yet.", error);
            }
            throw new IOException("GitHub release request failed with HTTP " + error.getStatusCode(), error);
        }

        JsonObject release = JsonParser.parseString(body).getAsJsonObject();
        String version = release.get("tag_name").getAsString();
        List<ReleaseAssetSelector.Asset> assets = ParseAssets(release.getAsJsonArray("assets"));
        ReleaseAssetSelector.Asset asset = ReleaseAssetSelector.Select(assets, HostPlatform.Current())
                .orElseThrow(() -> new IOException("The latest release has no SDK for " + HostPlatform.Current()));
        if (asset.digest() == null || !asset.digest().startsWith("sha256:")) {
            throw new IOException("SDK asset is missing its GitHub SHA-256 digest: " + asset.name());
        }

        Path sdk_root = Path.of(System.getProperty("user.home"), ".huxerui", "sdk");
        Path destination = sdk_root.resolve(SafeVersion(version));
        if (IsUsableSdk(destination)) {
            return destination;
        }
        Files.createDirectories(sdk_root);
        if (Files.exists(destination)) {
            throw new IOException("SDK destination already exists but is incomplete: " + destination);
        }
        Path archive = Files.createTempFile(sdk_root, ".download-", asset.name().endsWith(".zip") ? ".zip" : ".tar.gz");
        Path staging = sdk_root.resolve(".install-" + UUID.randomUUID());
        try {
            indicator.setText("Downloading HuxerUI SDK " + version);
            Download(asset.download_url(), archive, indicator);
            VerifyDigest(archive, asset.digest().substring("sha256:".length()));
            indicator.setText("Installing HuxerUI SDK " + version);
            Files.createDirectories(staging);
            if (asset.name().endsWith(".zip")) {
                ExtractZip(archive, staging, indicator);
            } else {
                ExtractTarGz(archive, staging, indicator);
            }
            Path extracted_home = FindSdkHome(staging);
            MakeCliExecutable(extracted_home);
            Move(extracted_home, destination);
            if (!IsUsableSdk(destination)) {
                throw new IOException("Installed archive does not contain the standard HUXERUI_HOME layout");
            }
            return destination;
        } finally {
            Files.deleteIfExists(archive);
            if (Files.exists(staging)) {
                DeleteTree(staging);
            }
        }
    }

    private static List<ReleaseAssetSelector.Asset> ParseAssets(JsonArray values) {
        List<ReleaseAssetSelector.Asset> assets = new ArrayList<>();
        for (JsonElement value : values) {
            JsonObject asset = value.getAsJsonObject();
            JsonElement digest = asset.get("digest");
            assets.add(new ReleaseAssetSelector.Asset(
                    asset.get("name").getAsString(),
                    asset.get("browser_download_url").getAsString(),
                    digest == null || digest.isJsonNull() ? null : digest.getAsString(),
                    asset.get("size").getAsLong()
            ));
        }
        return assets;
    }

    private static void Download(String url, Path destination, ProgressIndicator indicator) throws IOException {
        HttpRequests.request(url)
                .useProxy(true)
                .followRedirects(true)
                .connectTimeout(20_000)
                .readTimeout(600_000)
                .accept("application/octet-stream")
                .userAgent("HuxerUI-CLion-Plugin")
                .saveToFile(destination, indicator);
    }

    private static void VerifyDigest(Path archive, String expected) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(archive)) {
                byte[] buffer = new byte[64 * 1024];
                int count;
                while ((count = input.read(buffer)) >= 0) {
                    digest.update(buffer, 0, count);
                }
            }
            String actual = HexFormat.of().formatHex(digest.digest());
            if (!actual.equalsIgnoreCase(expected)) {
                throw new IOException("SDK SHA-256 mismatch: expected " + expected + ", received " + actual);
            }
        } catch (NoSuchAlgorithmException error) {
            throw new IOException("SHA-256 is unavailable", error);
        }
    }

    private static void ExtractZip(Path archive, Path destination, ProgressIndicator indicator) throws IOException {
        try (ZipInputStream input = new ZipInputStream(Files.newInputStream(archive))) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                indicator.checkCanceled();
                Path output = SafeOutput(destination, entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(output);
                } else {
                    Files.createDirectories(output.getParent());
                    Files.copy(input, output, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static void ExtractTarGz(Path archive, Path destination, ProgressIndicator indicator) throws IOException {
        try (InputStream input = new GZIPInputStream(new BufferedInputStream(Files.newInputStream(archive)))) {
            byte[] header = new byte[512];
            while (ReadFully(input, header)) {
                indicator.checkCanceled();
                if (AllZero(header)) {
                    return;
                }
                String name = TarText(header, 0, 100);
                String prefix = TarText(header, 345, 155);
                if (!prefix.isEmpty()) {
                    name = prefix + "/" + name;
                }
                long size = TarOctal(header, 124, 12);
                int type = header[156] & 0xff;
                Path output = SafeOutput(destination, name);
                if (type == '5') {
                    Files.createDirectories(output);
                } else if (type == 0 || type == '0') {
                    Files.createDirectories(output.getParent());
                    try (var file = Files.newOutputStream(output)) {
                        CopyExactly(input, file, size);
                    }
                } else {
                    SkipExactly(input, size);
                }
                long padding = (512 - (size % 512)) % 512;
                SkipExactly(input, padding);
            }
        }
    }

    private static Path FindSdkHome(Path staging) throws IOException {
        if (IsUsableSdk(staging)) {
            return staging;
        }
        try (var children = Files.list(staging)) {
            List<Path> directories = children.filter(Files::isDirectory).toList();
            if (directories.size() == 1 && IsUsableSdk(directories.getFirst())) {
                return directories.getFirst();
            }
        }
        throw new IOException("SDK archive does not contain a standard HUXERUI_HOME directory");
    }

    private static boolean IsUsableSdk(Path home) {
        return HuxerUISettings.SdkLayout.IsValid(home) && HuxerUISettings.SdkLayout.FindCli(home) != null;
    }

    private static void MakeCliExecutable(Path home) throws IOException {
        if (System.getProperty("os.name", "").toLowerCase().contains("win")) {
            return;
        }
        Path cli = HuxerUISettings.SdkLayout.FindCli(home);
        if (cli == null) {
            return;
        }
        try {
            Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(cli);
            permissions.add(PosixFilePermission.OWNER_EXECUTE);
            permissions.add(PosixFilePermission.GROUP_EXECUTE);
            permissions.add(PosixFilePermission.OTHERS_EXECUTE);
            Files.setPosixFilePermissions(cli, permissions);
        } catch (UnsupportedOperationException ignored) {
            if (!cli.toFile().setExecutable(true, false)) {
                throw new IOException("Unable to make the HuxerUI CLI executable: " + cli);
            }
        }
    }

    private static Path SafeOutput(Path root, String name) throws IOException {
        Path output = root.resolve(name.replace('\\', '/')).normalize();
        if (!output.startsWith(root)) {
            throw new IOException("SDK archive contains an unsafe path: " + name);
        }
        return output;
    }

    private static String SafeVersion(String version) throws IOException {
        if (!version.matches("[A-Za-z0-9._-]+")) {
            throw new IOException("Release tag cannot be used as an SDK directory: " + version);
        }
        return version;
    }

    private static void Move(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, destination);
        }
    }

    private static void DeleteTree(Path root) throws IOException {
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted((left, right) -> right.getNameCount() - left.getNameCount()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static boolean ReadFully(InputStream input, byte[] buffer) throws IOException {
        int offset = 0;
        while (offset < buffer.length) {
            int count = input.read(buffer, offset, buffer.length - offset);
            if (count < 0) {
                return offset == 0 ? false : ThrowTruncated();
            }
            offset += count;
        }
        return true;
    }

    private static boolean ThrowTruncated() throws IOException {
        throw new IOException("SDK tar archive is truncated");
    }

    private static boolean AllZero(byte[] bytes) {
        for (byte value : bytes) {
            if (value != 0) {
                return false;
            }
        }
        return true;
    }

    private static String TarText(byte[] header, int offset, int length) {
        int end = offset;
        while (end < offset + length && header[end] != 0) {
            ++end;
        }
        return new String(header, offset, end - offset, StandardCharsets.UTF_8);
    }

    private static long TarOctal(byte[] header, int offset, int length) throws IOException {
        String value = TarText(header, offset, length).trim();
        try {
            return value.isEmpty() ? 0 : Long.parseLong(value, 8);
        } catch (NumberFormatException error) {
            throw new IOException("SDK tar archive contains an invalid size", error);
        }
    }

    private static void CopyExactly(InputStream input, java.io.OutputStream output, long bytes) throws IOException {
        byte[] buffer = new byte[64 * 1024];
        long remaining = bytes;
        while (remaining > 0) {
            int count = input.read(buffer, 0, (int) Math.min(buffer.length, remaining));
            if (count < 0) {
                throw new IOException("SDK tar archive is truncated");
            }
            output.write(buffer, 0, count);
            remaining -= count;
        }
    }

    private static void SkipExactly(InputStream input, long bytes) throws IOException {
        long remaining = bytes;
        while (remaining > 0) {
            long skipped = input.skip(remaining);
            if (skipped <= 0) {
                if (input.read() < 0) {
                    throw new IOException("SDK tar archive is truncated");
                }
                skipped = 1;
            }
            remaining -= skipped;
        }
    }
}

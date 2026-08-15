package org.gradle.wrapper;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Minimal self-contained Gradle distribution bootstrap used only because the
 * original M0-M5 local snapshot did not contain a generated Gradle wrapper JAR.
 * It reads gradle-wrapper.properties, downloads the pinned distribution into
 * GRADLE_USER_HOME, safely extracts it, and delegates to its Gradle launcher.
 */
public final class GradleWrapperMain {
    private GradleWrapperMain() {}

    public static void main(String[] args) throws Exception {
        Path jarPath = Path.of(
            GradleWrapperMain.class.getProtectionDomain().getCodeSource().getLocation().toURI()
        ).toAbsolutePath().normalize();
        Path root = jarPath.getParent().getParent().getParent();
        Path propertiesPath = root.resolve("gradle/wrapper/gradle-wrapper.properties");

        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(propertiesPath)) {
            properties.load(in);
        }

        String distributionUrl = required(properties, "distributionUrl");
        URI distribution = URI.create(distributionUrl);
        Path gradleUserHome = resolveGradleUserHome();
        String zipName = Path.of(distribution.getPath()).getFileName().toString();
        String archiveStem = zipName.endsWith(".zip") ? zipName.substring(0, zipName.length() - 4) : zipName;
        String gradleDirName = archiveStem.replaceFirst("-(bin|all)$", "");
        String urlHash = shortSha256(distributionUrl);
        Path installRoot = gradleUserHome.resolve("wrapper/dists").resolve(archiveStem).resolve(urlHash);
        Path gradleHome = installRoot.resolve(gradleDirName);

        Files.createDirectories(installRoot);
        Path lockPath = installRoot.resolve(".lumen-wrapper.lock");
        try (FileChannel lockChannel = FileChannel.open(
                lockPath,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE
            ); FileLock ignored = lockChannel.lock()) {
            if (!isInstalled(gradleHome)) {
                install(distribution, installRoot, gradleHome);
            }
        }

        int exit = launch(root, gradleHome, args);
        System.exit(exit);
    }

    private static String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing " + key + " in gradle-wrapper.properties");
        }
        return value.trim();
    }

    private static Path resolveGradleUserHome() {
        String systemProperty = System.getProperty("gradle.user.home");
        if (systemProperty != null && !systemProperty.isBlank()) {
            return Path.of(systemProperty).toAbsolutePath().normalize();
        }
        String environment = System.getenv("GRADLE_USER_HOME");
        if (environment != null && !environment.isBlank()) {
            return Path.of(environment).toAbsolutePath().normalize();
        }
        return Path.of(System.getProperty("user.home"), ".gradle").toAbsolutePath().normalize();
    }

    private static boolean isInstalled(Path gradleHome) {
        return Files.isRegularFile(gradleHome.resolve(isWindows() ? "bin/gradle.bat" : "bin/gradle"));
    }

    private static void install(URI distribution, Path installRoot, Path gradleHome) throws Exception {
        Path partial = installRoot.resolve("distribution.zip.part");
        Path zip = installRoot.resolve("distribution.zip");
        Files.deleteIfExists(partial);
        Files.deleteIfExists(zip);

        System.out.println("Downloading Gradle distribution: " + distribution);
        HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
        HttpRequest request = HttpRequest.newBuilder(distribution).GET().build();
        HttpResponse<Path> response = client.send(request, HttpResponse.BodyHandlers.ofFile(partial));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            Files.deleteIfExists(partial);
            throw new IOException("Gradle distribution download failed with HTTP " + response.statusCode());
        }
        Files.move(partial, zip, StandardCopyOption.REPLACE_EXISTING);

        Path extractTemp = installRoot.resolve("extracting");
        deleteRecursively(extractTemp);
        Files.createDirectories(extractTemp);
        unzipSafely(zip, extractTemp);

        Path extractedHome = extractTemp.resolve(gradleHome.getFileName());
        if (!Files.isDirectory(extractedHome)) {
            throw new IOException("Expected Gradle directory not found after extraction: " + extractedHome);
        }
        deleteRecursively(gradleHome);
        Files.move(extractedHome, gradleHome, StandardCopyOption.REPLACE_EXISTING);
        deleteRecursively(extractTemp);
        Files.deleteIfExists(zip);
    }

    private static void unzipSafely(Path zip, Path destination) throws IOException {
        try (ZipInputStream zin = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry entry;
            while ((entry = zin.getNextEntry()) != null) {
                Path output = destination.resolve(entry.getName()).normalize();
                if (!output.startsWith(destination)) {
                    throw new IOException("Unsafe path in Gradle distribution: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(output);
                } else {
                    Files.createDirectories(output.getParent());
                    Files.copy(zin, output, StandardCopyOption.REPLACE_EXISTING);
                }
                zin.closeEntry();
            }
        }
    }

    private static int launch(Path root, Path gradleHome, String[] args) throws Exception {
        boolean windows = isWindows();
        Path executable = gradleHome.resolve(windows ? "bin/gradle.bat" : "bin/gradle");
        if (!windows) {
            executable.toFile().setExecutable(true, false);
        }

        List<String> command = new ArrayList<>();
        if (windows) {
            command.add("cmd.exe");
            command.add("/d");
            command.add("/c");
        }
        command.add(executable.toString());
        for (String arg : args) command.add(arg);

        Process process = new ProcessBuilder(command)
            .directory(root.toFile())
            .inheritIO()
            .start();
        return process.waitFor();
    }

    private static String shortSha256(String value) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash).substring(0, 16);
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) return;
        try (var stream = Files.walk(path)) {
            stream.sorted((a, b) -> b.getNameCount() - a.getNameCount())
                .forEach(p -> {
                    try { Files.deleteIfExists(p); }
                    catch (IOException e) { throw new RuntimeException(e); }
                });
        } catch (RuntimeException e) {
            if (e.getCause() instanceof IOException io) throw io;
            throw e;
        }
    }
}

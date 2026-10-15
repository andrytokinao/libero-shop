package com.houssen.liberoshop.update;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Where, on a delivered Windows install, the pieces an update must touch actually live.
 *
 * <p>A jpackage install looks like:
 * <pre>
 * &lt;INSTALLDIR&gt;\
 *   &lt;AppName&gt;.exe           the native launcher
 *   app\&lt;AppName&gt;.cfg        app.classpath points at the payload below
 *   runtime\lib\resources.bin the fat jar, renamed and hidden (the payload to replace)
 * </pre>
 *
 * <p>Everything is derived from the running JVM: {@code java.home} is {@code <INSTALLDIR>\runtime},
 * so its parent is the install root; the payload is the single entry on the class path (the
 * launcher resolved {@code app.classpath} to it); the launcher's base name is read from the only
 * {@code .cfg} under {@code app\}. In development (IDE, {@code mvn}, tests) the class path is a
 * directory or a long list, no {@code .cfg} exists, and {@link #locate()} returns empty -- an
 * in-place update is then simply refused, as it must be.
 *
 * @param installDir   the install root
 * @param payload      the fat jar to overwrite (its {@code .new}/{@code .bak} siblings sit beside it)
 * @param launcherExe  the native launcher to start the new version with
 */
public record InstallLayout(Path installDir, Path payload, Path launcherExe) {

    /** Resolves the layout from the running JVM, or empty when this is not a delivered Windows install. */
    public static Optional<InstallLayout> locate() {
        if (!isWindows()) {
            return Optional.empty();
        }
        Path payload = singleClasspathFile().orElse(null);
        if (payload == null || !Files.isRegularFile(payload)) {
            return Optional.empty();
        }
        String javaHome = System.getProperty("java.home", "");
        if (javaHome.isBlank()) {
            return Optional.empty();
        }
        Path installDir = Path.of(javaHome).getParent();
        if (installDir == null || !Files.isDirectory(installDir)) {
            return Optional.empty();
        }
        return appName(installDir.resolve("app"))
                .map(name -> new InstallLayout(installDir, payload, installDir.resolve(name + ".exe")))
                .filter(layout -> Files.isRegularFile(layout.launcherExe()));
    }

    /** The {@code .new} staging sibling the payload is written to before the swap. */
    public Path staged() {
        return sibling(".new");
    }

    /** The {@code .bak} sibling the previous payload is kept in, for rollback. */
    public Path backup() {
        return sibling(".bak");
    }

    private Path sibling(String suffix) {
        return payload.resolveSibling(payload.getFileName().toString() + suffix);
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
    }

    private static Optional<Path> singleClasspathFile() {
        String classpath = System.getProperty("java.class.path", "");
        String[] entries = classpath.split(File.pathSeparator);
        if (entries.length != 1 || entries[0].isBlank()) {
            return Optional.empty();
        }
        Path entry = Path.of(entries[0]);
        return Files.isRegularFile(entry) ? Optional.of(entry.toAbsolutePath()) : Optional.empty();
    }

    private static Optional<String> appName(Path appDir) {
        if (!Files.isDirectory(appDir)) {
            return Optional.empty();
        }
        try (Stream<Path> files = Files.list(appDir)) {
            return files
                    .map(p -> p.getFileName().toString())
                    .filter(n -> n.toLowerCase(Locale.ROOT).endsWith(".cfg"))
                    .map(n -> n.substring(0, n.length() - ".cfg".length()))
                    .findFirst();
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}

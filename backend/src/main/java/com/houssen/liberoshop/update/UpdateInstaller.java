package com.houssen.liberoshop.update;

import com.houssen.liberoshop.service.exception.BusinessRuleException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Replaces the running application's payload jar with a verified new one and relaunches.
 *
 * <p>The payload cannot be swapped from inside the live JVM: it is the jar the JVM loaded its
 * classes from, and Windows keeps it locked while the process runs. So the swap is handed to a
 * tiny external script that outlives this process: it waits for the lock to clear (it simply keeps
 * trying to rename the file -- no process polling, no locale-dependent output), moves the new jar
 * into place keeping the old one as {@code .bak}, and starts the launcher again. This process then
 * closes its context (flushing and closing the H2 database) and exits, which releases the lock.
 *
 * <p>Only possible on a delivered Windows install, where {@link InstallLayout#locate()} finds the
 * launcher and the payload. Anywhere else {@link #canApply()} is false and applying is refused.
 */
@Component
public class UpdateInstaller {

    private static final Logger log = LoggerFactory.getLogger(UpdateInstaller.class);

    /** Lets the HTTP answer reach the browser before the process goes down. */
    private static final long GRACE_MILLIS = 1500;

    private final ConfigurableApplicationContext context;

    public UpdateInstaller(ConfigurableApplicationContext context) {
        this.context = context;
    }

    /** The resolved install layout, when this is a delivered Windows install. */
    public Optional<InstallLayout> layout() {
        return InstallLayout.locate();
    }

    /** Whether an in-place update can be applied here. */
    public boolean canApply() {
        return layout().isPresent();
    }

    /** A short reason an update cannot be applied, for the screen. {@code null} when it can. */
    public String unavailableReason() {
        if (canApply()) {
            return null;
        }
        boolean windows = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).startsWith("windows");
        if (!windows) {
            return "La mise a jour en place n'est disponible que sur l'installation Windows.";
        }
        return "La mise a jour en place n'est possible que sur une application installee "
                + "(pas depuis l'environnement de developpement).";
    }

    /**
     * Applies a <em>verified</em> package: stages the payload, writes the swap script, answers the
     * caller's grace period, then closes the context and exits so the script can take over.
     *
     * @param verifiedPackage a package already checked by {@link UpdateVerifier}
     */
    public void apply(UpdatePackage verifiedPackage) {
        InstallLayout layout = layout().orElseThrow(() -> new BusinessRuleException(
                "UPDATE_UNAVAILABLE", unavailableReason()));
        Path script = prepare(verifiedPackage, layout);
        log.warn("Mise a jour prete : remplacement de {} puis relance de {}.", layout.payload(), layout.launcherExe());
        Thread relaunch = new Thread(() -> {
            try {
                Thread.sleep(GRACE_MILLIS);
                launch(script);
                context.close();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (RuntimeException e) {
                log.error("Echec du lancement de la mise a jour.", e);
                return;
            }
            // The script is now waiting for this process to release the payload lock.
            Runtime.getRuntime().halt(0);
        }, "liberoshop-update");
        relaunch.setDaemon(false);
        relaunch.start();
    }

    /** Stages the payload beside the current one and writes the swap script. Returns the script path. */
    Path prepare(UpdatePackage verifiedPackage, InstallLayout layout) {
        try {
            Path staged = layout.staged();
            Files.deleteIfExists(staged);
            verifiedPackage.copyPayloadTo(staged);
            Path script = Files.createTempFile("liberoshop-update-", ".cmd");
            Files.writeString(script, swapScript(layout), StandardCharsets.US_ASCII);
            return script;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void launch(Path script) {
        try {
            new ProcessBuilder("cmd.exe", "/c", script.toString())
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * The batch script that performs the swap after this process has let go of the payload.
     *
     * <p>Synchronises on the file lock itself: it keeps trying to rename the payload aside, which
     * fails while the JVM holds it and succeeds the moment the process exits -- no PID polling and
     * no reliance on {@code tasklist} wording, which is localised. Caps the wait at about two
     * minutes, after which it relaunches the untouched old version rather than hang.
     */
    private String swapScript(InstallLayout layout) {
        String payload = layout.payload().toString();
        String staged = layout.staged().toString();
        String backup = layout.backup().toString();
        String launcher = layout.launcherExe().toString();
        return """
                @echo off
                setlocal enabledelayedexpansion
                if exist "%BACKUP%" del /F /Q "%BACKUP%" >NUL 2>NUL
                set TRIES=0
                ping -n 3 127.0.0.1 >NUL
                :wait
                set /a TRIES+=1
                move /Y "%PAYLOAD%" "%BACKUP%" >NUL 2>NUL
                if not errorlevel 1 goto swap
                if !TRIES! GEQ 60 goto giveup
                ping -n 2 127.0.0.1 >NUL
                goto wait
                :swap
                move /Y "%STAGED%" "%PAYLOAD%" >NUL 2>NUL
                if errorlevel 1 move /Y "%BACKUP%" "%PAYLOAD%" >NUL 2>NUL
                :giveup
                start "" "%LAUNCHER%"
                (goto) 2>nul & del "%~f0"
                """
                .replace("%BACKUP%", backup)
                .replace("%PAYLOAD%", payload)
                .replace("%STAGED%", staged)
                .replace("%LAUNCHER%", launcher);
    }
}

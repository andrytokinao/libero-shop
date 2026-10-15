package com.houssen.liberoshop.update;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;

import static org.assertj.core.api.Assertions.assertThat;

class UpdateInstallerTest {

    private final KeyPair publisher = TestUpdatePackages.generateKeyPair();
    private final UpdateVerifier verifier = new UpdateVerifier(publisher.getPublic());
    private final UpdateInstaller installer = new UpdateInstaller(null);

    @Test
    void cannotApplyOutsideADeliveredInstall() {
        assertThat(installer.canApply()).isFalse();
        assertThat(installer.unavailableReason()).isNotBlank();
    }

    @Test
    void stagesThePayloadAndWritesASwapScript(@TempDir Path dir) throws Exception {
        byte[] jar = "le nouveau jar".getBytes(StandardCharsets.UTF_8);
        Path lsu = TestUpdatePackages.update(publisher).payload(jar).writeTo(dir.resolve("u.lsu"));
        UpdatePackage pkg = UpdatePackage.open(lsu);
        verifier.verify(pkg);

        Path libDir = Files.createDirectories(dir.resolve("install/runtime/lib"));
        Path payload = libDir.resolve("resources.bin");
        Files.writeString(payload, "ancien jar");
        InstallLayout layout = new InstallLayout(
                dir.resolve("install"), payload, dir.resolve("install/Libero Shop.exe"));

        Path script = installer.prepare(pkg, layout);

        assertThat(layout.staged()).hasBinaryContent(jar);
        String content = Files.readString(script);
        assertThat(content)
                .contains(payload.toString())
                .contains(layout.staged().toString())
                .contains(layout.backup().toString())
                .contains(layout.launcherExe().toString())
                .contains("move /Y")
                .contains("start \"\"");
        // Placeholders must all be substituted.
        assertThat(content).doesNotContain("%PAYLOAD%").doesNotContain("%LAUNCHER%");
    }
}

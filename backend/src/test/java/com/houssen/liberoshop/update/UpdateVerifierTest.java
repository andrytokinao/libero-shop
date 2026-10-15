package com.houssen.liberoshop.update;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.KeyPair;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UpdateVerifierTest {

    private final KeyPair publisher = TestUpdatePackages.generateKeyPair();
    private final UpdateVerifier verifier = new UpdateVerifier(publisher.getPublic());

    @Test
    void acceptsAGenuinePackage(@TempDir Path dir) {
        Path file = TestUpdatePackages.update(publisher).version("1.3.0").writeTo(dir.resolve("u.lsu"));

        UpdateManifest manifest = verifier.verify(UpdatePackage.open(file));

        assertThat(manifest.appVersion()).isEqualTo("1.3.0");
        assertThat(manifest.payloadSha256()).hasSize(64);
    }

    @Test
    void rejectsAPackageSignedByAnotherKey(@TempDir Path dir) {
        KeyPair attacker = TestUpdatePackages.generateKeyPair();
        Path file = TestUpdatePackages.update(attacker).writeTo(dir.resolve("u.lsu"));

        assertThatThrownBy(() -> verifier.verify(UpdatePackage.open(file)))
                .isInstanceOf(UpdateInvalidException.class)
                .hasMessageContaining("signature");
    }

    @Test
    void rejectsABrokenSignature(@TempDir Path dir) {
        Path file = TestUpdatePackages.update(publisher).withBrokenSignature().writeTo(dir.resolve("u.lsu"));

        assertThatThrownBy(() -> verifier.verify(UpdatePackage.open(file)))
                .isInstanceOf(UpdateInvalidException.class);
    }

    @Test
    void rejectsAPayloadSwappedAfterSigning(@TempDir Path dir) {
        Path file = TestUpdatePackages.update(publisher)
                .withPayloadTamperedAfterSigning().writeTo(dir.resolve("u.lsu"));

        assertThatThrownBy(() -> verifier.verify(UpdatePackage.open(file)))
                .isInstanceOf(UpdateInvalidException.class)
                .hasMessageContaining("ne correspond pas");
    }

    @Test
    void rejectsAnUnsupportedAlgorithm(@TempDir Path dir) {
        Path file = TestUpdatePackages.update(publisher).algorithm("RSA").writeTo(dir.resolve("u.lsu"));

        assertThatThrownBy(() -> verifier.verify(UpdatePackage.open(file)))
                .isInstanceOf(UpdateInvalidException.class)
                .hasMessageContaining("Algorithme");
    }

    @Test
    void refusesAnOlderOrEqualVersionWhenApplying(@TempDir Path dir) {
        Path file = TestUpdatePackages.update(publisher).version("1.2.0").writeTo(dir.resolve("u.lsu"));

        assertThatThrownBy(() -> verifier.verifyNewerThan(UpdatePackage.open(file), "1.2.0"))
                .isInstanceOf(UpdateInvalidException.class)
                .hasMessageContaining("plus recente");
    }

    @Test
    void acceptsANewerVersionWhenApplying(@TempDir Path dir) {
        Path file = TestUpdatePackages.update(publisher).version("1.3.0").writeTo(dir.resolve("u.lsu"));

        UpdateManifest manifest = verifier.verifyNewerThan(UpdatePackage.open(file), "1.2.0");

        assertThat(manifest.appVersion()).isEqualTo("1.3.0");
    }

    @Test
    void rejectsAFileThatIsNotAnUpdate(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("not-an-update.lsu");
        java.nio.file.Files.write(file, "pas une archive".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> UpdatePackage.open(file))
                .isInstanceOf(UpdateInvalidException.class);
    }

    @Test
    void copiesThePayloadOut(@TempDir Path dir) {
        byte[] jar = "le vrai jar".getBytes(StandardCharsets.UTF_8);
        Path file = TestUpdatePackages.update(publisher).payload(jar).writeTo(dir.resolve("u.lsu"));
        UpdatePackage pkg = UpdatePackage.open(file);
        verifier.verify(pkg);

        Path out = dir.resolve("out.bin");
        pkg.copyPayloadTo(out);

        assertThat(out).hasBinaryContent(jar);
    }
}

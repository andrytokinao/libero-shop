package com.houssen.liberoshop.update;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class InstallLayoutTest {

    @Test
    void doesNotResolveUnderTheTestClasspath() {
        // Tests run from many classpath entries (or target/classes), never a single packaged jar,
        // so an in-place update is correctly judged impossible here.
        Optional<InstallLayout> layout = InstallLayout.locate();
        assertThat(layout).isEmpty();
    }

    @Test
    void namesTheStagingAndBackupSiblings() {
        Path payload = Path.of("C:", "App", "runtime", "lib", "resources.bin");
        InstallLayout layout = new InstallLayout(Path.of("C:", "App"), payload, Path.of("C:", "App", "App.exe"));

        assertThat(layout.staged().getFileName().toString()).isEqualTo("resources.bin.new");
        assertThat(layout.backup().getFileName().toString()).isEqualTo("resources.bin.bak");
        assertThat(layout.staged().getParent()).isEqualTo(payload.getParent());
    }
}

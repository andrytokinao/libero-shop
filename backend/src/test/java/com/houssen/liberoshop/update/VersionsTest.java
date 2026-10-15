package com.houssen.liberoshop.update;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VersionsTest {

    @Test
    void comparesNumericallyNotLexically() {
        assertThat(Versions.isNewer("1.10.0", "1.9.0")).isTrue();
        assertThat(Versions.isNewer("1.9.0", "1.10.0")).isFalse();
    }

    @Test
    void treatsMissingTrailingPartsAsZero() {
        assertThat(Versions.compare("1.2", "1.2.0")).isZero();
        assertThat(Versions.isNewer("1.2.1", "1.2")).isTrue();
    }

    @Test
    void equalVersionsAreNotNewer() {
        assertThat(Versions.isNewer("2.0.0", "2.0.0")).isFalse();
    }

    @Test
    void higherMajorWins() {
        assertThat(Versions.isNewer("2.0.0", "1.99.99")).isTrue();
    }
}

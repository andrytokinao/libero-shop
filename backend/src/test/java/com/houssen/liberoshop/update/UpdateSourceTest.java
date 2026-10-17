package com.houssen.liberoshop.update;

import com.houssen.liberoshop.service.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UpdateSourceTest {

    @Test
    void refusesEveryOnlineOperationWithoutAUrl() {
        UpdateProperties noUrl = new UpdateProperties(true, "", null, true, null);
        UpdateSource source = new UpdateSource(noUrl);

        assertThatThrownBy(source::fetchManifest)
                .isInstanceOf(BusinessRuleException.class)
                .extracting(e -> ((BusinessRuleException) e).code())
                .isEqualTo("UPDATE_NO_URL");
    }

    @Test
    void automaticIsOffWhenEnabledButNoUrl() {
        assertThat(new UpdateProperties(true, "", null, true, null).automaticEnabled()).isFalse();
        assertThat(new UpdateProperties(true, "https://example.com/u.json", null, true, null)
                .automaticEnabled()).isTrue();
    }

    @Test
    void anUnreachableServerIsToldWithoutTheSystemsMessage() {
        // A port nothing listens on: the connection is refused at once.
        UpdateSource source = new UpdateSource(
                new UpdateProperties(true, "http://127.0.0.1:1/manifest.json", null, true, null));

        assertThatThrownBy(source::fetchManifest)
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> {
                    assertThat(((BusinessRuleException) e).code()).isEqualTo("UPDATE_UNREACHABLE");
                    assertThat(e.getMessage()).doesNotContain("Exception").doesNotContain("127.0.0.1");
                });
    }
}

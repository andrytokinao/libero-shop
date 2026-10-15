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
}

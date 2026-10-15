package com.houssen.liberoshop.update;

import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class UpdateSchedulerTest {

    private static final long DAY_MS = 24 * 60 * 60 * 1000L;

    @Test
    void nextOccurrenceIsAlwaysInTheFutureAndWithinADay() {
        long inOneMinute = UpdateScheduler.millisUntilNext(LocalTime.now().plusMinutes(1).withNano(0));
        assertThat(inOneMinute).isPositive().isLessThanOrEqualTo(DAY_MS);

        // A time just passed rolls to tomorrow: still positive, close to a full day.
        long justPassed = UpdateScheduler.millisUntilNext(LocalTime.now().minusMinutes(1).withNano(0));
        assertThat(justPassed).isPositive().isLessThanOrEqualTo(DAY_MS);
    }
}

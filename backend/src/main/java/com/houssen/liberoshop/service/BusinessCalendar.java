package com.houssen.liberoshop.service;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * The single definition of "today" for the whole application.
 *
 * <p>Built on the injectable {@link Clock} the license module already declares, so a test
 * can move the application to any date and every dashboard follows. Day windows are
 * expressed as half-open ranges {@code [start, end)}: comparing against a start-of-day and
 * a start-of-next-day never drops a sale recorded at 23:59:59.999.
 */
@Component
public class BusinessCalendar {

    private final Clock clock;

    public BusinessCalendar(Clock clock) {
        this.clock = clock;
    }

    public LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    public LocalDateTime startOfToday() {
        return today().atStartOfDay();
    }

    /** Exclusive upper bound of today. */
    public LocalDateTime startOfTomorrow() {
        return today().plusDays(1).atStartOfDay();
    }

    public LocalDateTime startOfDaysAgo(int days) {
        return today().minusDays(days).atStartOfDay();
    }

    public boolean isToday(LocalDateTime moment) {
        return moment != null
                && !moment.isBefore(startOfToday())
                && moment.isBefore(startOfTomorrow());
    }
}

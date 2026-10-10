package com.houssen.liberoshop.security;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Slows down someone guessing passwords at the sign-in.
 *
 * <p>Failures are counted per address <em>and</em> login name, not per account: the shop's
 * customers sit on the same Wi-Fi as its tills, and a count kept on the account alone would let
 * any of them lock the manager out by typing her name five times. Counted this way the phone
 * that guesses is the one that waits, and the till next to it signs in as usual.
 *
 * <p>The first mistakes cost nothing -- a cashier mistyping at the start of a shift is the
 * ordinary case. The {@value #FREE_FAILURES}th in a row brings a wait of {@link #FIRST_WAIT},
 * and each one after that doubles it, up to {@link #LONGEST_WAIT}. A second, looser count per address alone
 * catches the caller who walks through the login names instead of the passwords.
 *
 * <p>Kept in memory: a restart forgets it, which costs an attacker a restart they cannot
 * trigger, and spares a table that would be written on every wrong password.
 */
@Component
public class LoginThrottle {

    static final int FREE_FAILURES = 5;
    /** Per address, whatever the names tried: several people share a till, so well above the above. */
    static final int FREE_FAILURES_PER_ADDRESS = 30;
    static final Duration FIRST_WAIT = Duration.ofSeconds(30);
    static final Duration LONGEST_WAIT = Duration.ofMinutes(15);
    /** A count nobody added to for this long starts again from zero. */
    static final Duration FORGOTTEN_AFTER = Duration.ofMinutes(30);

    /** Beyond this the oldest counts are dropped before a new one is kept, so the map stays bounded. */
    private static final int MAX_TRACKED = 10_000;

    private final Map<String, Failures> failures = new ConcurrentHashMap<>();
    private final Clock clock;

    public LoginThrottle(Clock clock) {
        this.clock = clock;
    }

    /**
     * To call before the password is even looked at, so a caller made to wait learns nothing
     * from the attempt.
     *
     * @throws TooManyLoginAttemptsException while this caller has to wait
     */
    public void check(String address, String username) {
        Instant now = clock.instant();
        Duration wait = longest(waitOf(pairKey(address, username), now), waitOf(addressKey(address), now));
        if (!wait.isZero()) {
            throw new TooManyLoginAttemptsException(wait);
        }
    }

    public void failed(String address, String username) {
        Instant now = clock.instant();
        if (failures.size() >= MAX_TRACKED) {
            failures.values().removeIf(entry -> entry.forgottenAt(now));
        }
        record(pairKey(address, username), FREE_FAILURES, now);
        record(addressKey(address), FREE_FAILURES_PER_ADDRESS, now);
    }

    /** The right password clears that name's count; the address keeps its own until it is forgotten. */
    public void succeeded(String address, String username) {
        failures.remove(pairKey(address, username));
    }

    /** @param allowed the failure from which this count makes the caller wait */
    private void record(String key, int allowed, Instant now) {
        failures.compute(key, (ignored, known) -> {
            int count = known == null || known.forgottenAt(now) ? 1 : known.count() + 1;
            return new Failures(count, now, now.plus(waitAfter(count - allowed + 1)));
        });
    }

    private Duration waitOf(String key, Instant now) {
        Failures known = failures.get(key);
        if (known == null || !known.waitUntil().isAfter(now)) {
            return Duration.ZERO;
        }
        return Duration.between(now, known.waitUntil());
    }

    /** @param excess 0 or less costs nothing, 1 the first wait, each one more doubles it */
    private static Duration waitAfter(int excess) {
        if (excess <= 0) {
            return Duration.ZERO;
        }
        // Capped before the shift, which would overflow long before anyone typed that often.
        long factor = 1L << Math.min(excess - 1, 10);
        Duration wait = FIRST_WAIT.multipliedBy(factor);
        return wait.compareTo(LONGEST_WAIT) > 0 ? LONGEST_WAIT : wait;
    }

    private static Duration longest(Duration one, Duration other) {
        return one.compareTo(other) >= 0 ? one : other;
    }

    private static String pairKey(String address, String username) {
        String name = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        return address + '|' + name;
    }

    private static String addressKey(String address) {
        return address + '|';
    }

    private record Failures(int count, Instant last, Instant waitUntil) {

        boolean forgottenAt(Instant now) {
            return last.plus(FORGOTTEN_AFTER).isBefore(now) && !waitUntil.isAfter(now);
        }
    }
}

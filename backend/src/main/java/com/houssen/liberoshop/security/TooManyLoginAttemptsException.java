package com.houssen.liberoshop.security;

import java.time.Duration;

/** Sign-in refused without looking at the password: this caller failed too often. Maps to 429. */
public class TooManyLoginAttemptsException extends RuntimeException {

    private final Duration wait;

    public TooManyLoginAttemptsException(Duration wait) {
        super("Trop de tentatives de connexion. Reessayez dans " + describe(wait) + ".");
        this.wait = wait;
    }

    public Duration waitTime() {
        return wait;
    }

    /** Rounded up, so "dans 1 minute" is never said with 59 seconds still to go. */
    private static String describe(Duration wait) {
        long seconds = Math.max(1, wait.toSeconds() + (wait.toMillisPart() > 0 ? 1 : 0));
        if (seconds < 60) {
            return seconds + " seconde" + (seconds > 1 ? "s" : "");
        }
        long minutes = (seconds + 59) / 60;
        return minutes + " minute" + (minutes > 1 ? "s" : "");
    }
}

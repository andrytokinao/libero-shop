package com.houssen.liberoshop.update;

/**
 * What the server broadcasts to every screen around an update, on the {@code maintenance} topic.
 *
 * <ul>
 *   <li>{@code CONFIRM_REQUEST} — an update is ready; each screen is asked whether its till can be
 *       cut off now.</li>
 *   <li>{@code APPLYING} — the server is about to stop and come back on the new version; screens
 *       show the "coming back" overlay.</li>
 *   <li>{@code CANCELLED} — the round was dropped (someone declined, or not everyone confirmed in
 *       time); screens dismiss the prompt.</li>
 * </ul>
 *
 * @param kind    which of the three this is
 * @param version the version concerned; may be {@code null} on a cancellation
 * @param message a sentence to show the user
 */
public record MaintenanceNotice(Kind kind, String version, String message) {

    public enum Kind {
        CONFIRM_REQUEST,
        APPLYING,
        CANCELLED
    }

    /** The topic every screen listens to for these. */
    public static final String TOPIC = "maintenance";
}

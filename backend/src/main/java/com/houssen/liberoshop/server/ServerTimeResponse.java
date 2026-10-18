package com.houssen.liberoshop.server;

import java.time.LocalDateTime;

/**
 * The shop's clock, for the screens: "today" is the server's, not the phone's -- a phone set to
 * the wrong day or zone must not show yesterday's sales as today's.
 *
 * @param now  the shop's local date and time
 * @param zone the zone it is counted in, e.g. "Indian/Antananarivo"
 */
public record ServerTimeResponse(LocalDateTime now, String zone) {
}

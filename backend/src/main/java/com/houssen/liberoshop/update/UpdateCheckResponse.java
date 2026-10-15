package com.houssen.liberoshop.update;

/**
 * The outcome of looking at the update URL: what the latest published version is, and whether it
 * is newer than the one running.
 *
 * @param available      whether {@code latestVersion} is strictly newer than {@code currentVersion}
 * @param latestVersion  the newest published version
 * @param releasedOn     the day it was released; may be {@code null}
 * @param notes          its release note; may be {@code null}
 * @param currentVersion the version running now
 */
public record UpdateCheckResponse(
        boolean available,
        String latestVersion,
        String releasedOn,
        String notes,
        String currentVersion) {
}

package com.houssen.liberoshop.update;

/**
 * What the hidden update page shows: the running version, whether an in-place update is possible
 * here, and a summary of the automatic channel's settings.
 *
 * @param currentVersion      the version running now
 * @param canApply            whether an update can be applied in place (delivered Windows install)
 * @param unavailableReason   why not, when it cannot; {@code null} when it can
 * @param canRollback         whether a previous version is kept and can be restored
 * @param automaticEnabled    whether the automatic channel is on and has a URL
 * @param urlConfigured       whether an update URL is set (online check and download possible)
 * @param checkTime           the daily automatic check time, {@code HH:mm}
 * @param requireConfirmation whether an automatic update waits for connected screens to agree
 */
public record UpdateStatusResponse(
        String currentVersion,
        boolean canApply,
        String unavailableReason,
        boolean canRollback,
        boolean automaticEnabled,
        boolean urlConfigured,
        String checkTime,
        boolean requireConfirmation) {
}

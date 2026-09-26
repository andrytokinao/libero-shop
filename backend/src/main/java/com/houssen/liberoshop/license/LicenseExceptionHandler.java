package com.houssen.liberoshop.license;

import com.houssen.liberoshop.license.exception.LicenseException;
import com.houssen.liberoshop.license.exception.LicenseExpiredException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

/**
 * Turns license failures into HTTP responses the UI can act on.
 *
 * <p>Expiry maps to {@code 402 Payment Required}, which is exactly what it means and
 * gives the front end a single condition to watch for in order to show the renewal
 * banner and disable the "new sale" buttons. Everything else maps to {@code 403}.
 */
@RestControllerAdvice
public class LicenseExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(LicenseExceptionHandler.class);

    @ExceptionHandler(LicenseExpiredException.class)
    public ResponseEntity<LicenseErrorResponse> handleExpired(LicenseExpiredException e) {
        // Expected once a customer stops paying: a warning, not a stack trace.
        log.warn("Write refused, license expired on {}.", e.getExpiredOn());
        return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).body(response(e));
    }

    @ExceptionHandler(LicenseException.class)
    public ResponseEntity<LicenseErrorResponse> handleLicenseFailure(LicenseException e) {
        log.error("License failure ({}): {}", e.code(), e.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response(e));
    }

    private static LicenseErrorResponse response(LicenseException e) {
        return new LicenseErrorResponse(e.code(), e.getMessage(), Instant.now());
    }

    /**
     * @param code      stable identifier the front end can branch on
     * @param message   end-user text, already written in French and safe to display as is
     * @param timestamp when the failure was produced
     */
    public record LicenseErrorResponse(String code, String message, Instant timestamp) {
    }
}

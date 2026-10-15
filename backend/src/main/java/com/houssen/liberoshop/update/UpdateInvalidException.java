package com.houssen.liberoshop.update;

import com.houssen.liberoshop.service.exception.BusinessRuleException;

/**
 * An update file that is empty, malformed, not signed by the publisher, or whose embedded
 * payload does not match its signed manifest.
 *
 * <p>From the administrator's point of view a corrupt file and a forged one need the same
 * remedy -- a fresh file from the publisher -- so they share one exception and one stable code.
 * Routed through {@code ApiExceptionHandler} as a business refusal (HTTP 409).
 */
public class UpdateInvalidException extends BusinessRuleException {

    public static final String CODE = "UPDATE_INVALID";

    public UpdateInvalidException(String message) {
        super(CODE, message);
    }
}

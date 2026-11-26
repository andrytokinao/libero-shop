package com.houssen.liberoshop.exception;

/**
 * A request that is well formed but refused by a business rule: a quantity too precise,
 * a loss larger than what was received, a feature not available yet.
 *
 * <p>{@code code} is stable and meant for the client (to pick a message or a field to highlight);
 * the message is meant for the person, in French.
 */
public class BusinessRuleException extends RuntimeException {

    private final String code;

    public BusinessRuleException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
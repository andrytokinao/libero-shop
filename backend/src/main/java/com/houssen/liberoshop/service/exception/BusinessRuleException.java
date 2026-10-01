package com.houssen.liberoshop.service.exception;

/**
 * A request that is well formed but that the business refuses -- delivering an order
 * twice, remitting cash you do not hold, selling more units than are in stock.
 *
 * <p>Carries a stable {@code code} next to the French message so the UI can react to the
 * kind of failure without parsing wording that may be reworded.
 */
public class BusinessRuleException extends RuntimeException {

    private final String code;

    public BusinessRuleException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }

    /**
     * What the screen needs to act on the refusal beyond showing it -- which lines, which
     * products -- serialized as the error's {@code data}. Null for the refusals a message
     * says all about; a subclass with more to tell overrides this.
     */
    public Object data() {
        return null;
    }
}

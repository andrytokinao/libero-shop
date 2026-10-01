package com.houssen.liberoshop.service.exception;

/**
 * A request only the shop's own network may make came from elsewhere. Maps to 403.
 */
public class OutsideLocalNetworkException extends RuntimeException {

    public OutsideLocalNetworkException(String message) {
        super(message);
    }
}

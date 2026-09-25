package com.houssen.libertyshop.service.exception;

/** Something was addressed by id and does not exist. Maps to 404. */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public static ResourceNotFoundException of(String what, Object id) {
        return new ResourceNotFoundException(what + " introuvable (id " + id + ").");
    }
}

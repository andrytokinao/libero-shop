package com.houssen.liberoshop.service;

/**
 * What an account may see or do has just been narrowed, or one of its sessions closed: it was
 * disabled, its roles changed, its password changed, or it signed out.
 *
 * <p>The REST API needs no telling -- it reads the account again on every request. This is for
 * what stays open between two requests: the WebSocket connections, which were authenticated
 * once and would otherwise go on listening.
 */
public record AccountAccessChangedEvent(Long userId) {
}

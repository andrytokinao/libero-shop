package com.houssen.liberoshop.server;

/**
 * @param application always {@link ServerController#APPLICATION}: how a client recognises this
 *                    server among whatever else answers on the network
 * @param version     the backend's build version, {@code "dev"} outside a Maven build
 */
public record ServerInfoResponse(String application, String name, String version) {
}

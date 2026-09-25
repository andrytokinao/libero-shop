package com.houssen.libertyshop.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;


@ConfigurationProperties(prefix = "libertyshop.demo-data")
public record DemoDataProperties(boolean enabled, String password) {

    public static final String DEFAULT_PASSWORD = "vaha2026";

    public DemoDataProperties {
        password = (password == null || password.isBlank()) ? DEFAULT_PASSWORD : password;
    }

    public boolean usesDefaultPassword() {
        return DEFAULT_PASSWORD.equals(password);
    }
}

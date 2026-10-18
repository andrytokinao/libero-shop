package com.houssen.liberoshop.service;

import com.houssen.liberoshop.entity.UserApp;

import java.util.Optional;

/**
 * Who is acting, for the services that keep a trace of it -- told by the security layer, so a
 * service reads it without knowing about tokens or requests. Empty for work no one signed in
 * for: start-up, the demo data, a test.
 */
public interface ActingUser {

    Optional<UserApp> current();
}

package com.houssen.libertyshop.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Materialises the CSRF token on every request so the {@code XSRF-TOKEN} cookie is
 * actually written.
 *
 * <p>Since Spring Security 6 the token is loaded lazily: without touching it, a browser
 * that has only ever issued GETs never receives the cookie, and its first POST is
 * rejected. Reading {@link CsrfToken#getToken()} here is what triggers the repository to
 * save it — one line, and the Angular client can rely on the cookie always being present.
 */
public class CsrfCookieFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (request.getAttribute(CsrfToken.class.getName()) instanceof CsrfToken token) {
            token.getToken();
        }
        filterChain.doFilter(request, response);
    }
}

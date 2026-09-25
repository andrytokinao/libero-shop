package com.houssen.libertyshop.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Security for the API consumed by the Angular front end.
 *
 * <p>Authentication is session based: the browser holds a {@code JSESSIONID} cookie and
 * the API stays stateless from the client's point of view. Compared with a token kept in
 * {@code localStorage}, an {@code HttpOnly} session cookie cannot be read by injected
 * script, and revoking a shift is a matter of invalidating the session. CSRF is therefore
 * on — a cookie is sent automatically by the browser, so it must be paired with a header
 * only same-origin script can set.
 *
 * <p>Authorisation is expressed twice on purpose: coarse-grained here, per URL, and
 * per-role on each handler with {@code @PreAuthorize}. The rule here is the safety net --
 * everything under {@code /api} needs a session — while the handler annotation states
 * which role the operation belongs to, right where a reader looks for it.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityConfiguration {

    /** Read by the license screen before anyone can log in, so it stays public. */
    private static final String[] PUBLIC_LICENSE_ENDPOINTS = {
            "/api/license/status", "/api/license/fingerprint", "/api/license/renewal-code"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   RestAuthenticationHandlers restHandlers,
                                                   SecurityProperties properties) throws Exception {
        CookieCsrfTokenRepository csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfRepository.setCookieCustomizer(cookie -> cookie.secure(properties.secureCookies()));

        http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfRepository)
                        // Plain handler, not the XOR one: Angular's HttpClient echoes the
                        // raw cookie value back in X-XSRF-TOKEN, unmasked.
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/session").permitAll()
                        .requestMatchers(HttpMethod.GET, PUBLIC_LICENSE_ENDPOINTS).permitAll()
                        // Installing or renewing a license is an administrative act.
                        .requestMatchers("/api/license/**").hasRole("SUPER_ADMIN")
                        .requestMatchers("/api/**").authenticated()
                        // Anything else is the packaged SPA: index.html, JS, CSS, icons.
                        .anyRequest().permitAll())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(restHandlers)
                        .accessDeniedHandler(restHandlers))
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        // 204 rather than the default redirect: there is no page to go to.
                        .logoutSuccessHandler((request, response, authentication) ->
                                response.setStatus(HttpStatus.NO_CONTENT.value()))
                        .deleteCookies("JSESSIONID")
                        .invalidateHttpSession(true))
                // Runs after the CSRF filter has put the token in the request attributes.
                .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AppUserDetailsService userDetailsService,
                                                       PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        // Keeps the "user not found" and "bad password" paths indistinguishable to the caller.
        provider.setHideUserNotFoundExceptions(true);
        return new ProviderManager(provider);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Only needed while the UI is served by {@code ng serve} on another port. In
     * production the SPA is packaged with the jar, the origin list is empty, and no
     * cross-origin call is allowed at all.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(SecurityProperties properties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        // Required for the session and CSRF cookies to travel cross-origin.
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        if (!properties.allowedOrigins().isEmpty()) {
            source.registerCorsConfiguration("/api/**", configuration);
        }
        return source;
    }
}

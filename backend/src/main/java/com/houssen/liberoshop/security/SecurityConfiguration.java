package com.houssen.liberoshop.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.RequestCacheConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Security for the API consumed by the Angular front end.
 *
 * <p>Authentication is by bearer token: {@code POST /api/auth/login} returns a signed JWT
 * and the client sends it back in the {@code Authorization} header on every call. Nothing
 * about the caller is kept on the server -- no session, no token table -- so any instance
 * can answer any request, and a restart does not sign anyone out (as long as the signing
 * secret is kept rather than generated). The one thing remembered is the tokens signed out
 * before their time -- see {@link SessionRevocation}.
 *
 * <p>A signed token cannot be taken back by itself, so the token is only trusted for who it
 * names: {@link AccountAuthenticationConverter} re-reads the account on every request. A
 * disabled account, a role taken away, a changed password or a device signed out are refused
 * on the next call, reads included, rather than when the token runs out.
 *
 * <p>CSRF is off, and that follows from the above rather than being a shortcut: the
 * identity no longer travels in a cookie the browser attaches by itself, and a cross-site
 * form cannot set an {@code Authorization} header.
 *
 * <p>Authorisation is expressed twice on purpose: coarse-grained here, per URL, and
 * per-role on each handler with {@code @PreAuthorize}. The rule here is the safety net --
 * everything under {@code /api} needs a token -- while the handler annotation states which
 * role the operation belongs to, right where a reader looks for it.
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
                                                   JwtDecoder jwtDecoder,
                                                   AccountAuthenticationConverter jwtAuthenticationConverter)
            throws Exception {
        http
                .cors(Customizer.withDefaults())
                // Safe only because the identity is in a header the client sets itself.
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        // Open, so a client holding no token can still call it; with a
                        // token still good, that token is closed -- see AuthController.
                        .requestMatchers(HttpMethod.POST, "/api/auth/logout").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/session").permitAll()
                        .requestMatchers(HttpMethod.GET, PUBLIC_LICENSE_ENDPOINTS).permitAll()
                        // The first start: nobody can sign in before the first account exists.
                        // The controller refuses once there is one, and from any other machine.
                        .requestMatchers(HttpMethod.GET, "/api/setup/status").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/setup/admin").permitAll()
                        // Asked by the mobile app to test an address before anyone signs in.
                        .requestMatchers(HttpMethod.GET, "/api/server/info").permitAll()
                        // The download page: a phone is equipped before its owner has an account.
                        .requestMatchers(HttpMethod.GET, "/api/server/connection").permitAll()
                        // The app's own pages, checked for updates at launch, before sign-in.
                        .requestMatchers(HttpMethod.GET, "/api/mobile/update", "/api/mobile/bundle/*").permitAll()
                        // Ordering from a table's QR code: the customer has no account, the
                        // table's token is the credential (see OnlineOrderService).
                        .requestMatchers("/api/public/**").permitAll()
                        // Installing or renewing a license is an administrative act.
                        .requestMatchers("/api/license/**").hasRole("SUPER_ADMIN")
                        .requestMatchers("/api/**").authenticated()
                        // The WebSocket handshake cannot carry a bearer header, so it is open
                        // here and the token is checked on the STOMP CONNECT frame instead --
                        // see StompAuthenticationInterceptor. No frame is served before that.
                        .requestMatchers("/ws").permitAll()
                        // Anything else is the packaged SPA: index.html, JS, CSS, icons.
                        .anyRequest().permitAll())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .decoder(jwtDecoder)
                                .jwtAuthenticationConverter(jwtAuthenticationConverter))
                        // Set here as well as below: the resource server installs its own
                        // entry point for bearer-token failures, which answers with an
                        // empty body and a WWW-Authenticate header the SPA cannot read.
                        .authenticationEntryPoint(restHandlers)
                        .accessDeniedHandler(restHandlers))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(restHandlers)
                        .accessDeniedHandler(restHandlers))
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // The default cache stores the refused request in an HTTP session, which
                // would create the very session the policy above says we do not keep.
                .requestCache(RequestCacheConfigurer::disable);

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
     * Two kinds of cross-origin callers, and only two: {@code ng serve} on another port during
     * development, and the mobile app, whose pages are bundled in the phone rather than served
     * from here. The SPA packaged with the jar is same-origin and needs none of this.
     *
     * <p>Credentials are not allowed: with the token in a header there is no cookie left
     * for the browser to attach, and asking for credentialed CORS would forbid the
     * {@code *} header list below for nothing.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(SecurityProperties properties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.crossOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        if (!properties.crossOrigins().isEmpty()) {
            source.registerCorsConfiguration("/api/**", configuration);
        }
        return source;
    }
}

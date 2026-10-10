package com.houssen.liberoshop.web;

import com.houssen.liberoshop.entity.UserApp;
import com.houssen.liberoshop.security.AppUserDetails;
import com.houssen.liberoshop.security.CurrentUser;
import com.houssen.liberoshop.security.JwtService;
import com.houssen.liberoshop.security.JwtService.IssuedToken;
import com.houssen.liberoshop.security.LoginThrottle;
import com.houssen.liberoshop.security.SessionRevocation;
import com.houssen.liberoshop.service.RolePolicy;
import com.houssen.liberoshop.service.ShopSettingsService;
import com.houssen.liberoshop.web.dto.LoginRequest;
import com.houssen.liberoshop.web.dto.LoginResponse;
import com.houssen.liberoshop.web.dto.SessionResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Sign-in, sign-out and "who am I".
 *
 * <p>The login is done by hand rather than with the form-login filter so the answer is
 * JSON the SPA can consume directly, and so a successful call returns the token and the
 * session payload in the same round trip instead of forcing a follow-up request.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final String ROLE_PREFIX = "ROLE_";

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final CurrentUser currentUser;
    private final ShopSettingsService settingsService;
    private final LoginThrottle loginThrottle;
    private final SessionRevocation sessionRevocation;

    public AuthController(AuthenticationManager authenticationManager, JwtService jwtService,
                          CurrentUser currentUser, ShopSettingsService settingsService,
                          LoginThrottle loginThrottle, SessionRevocation sessionRevocation) {
        this.loginThrottle = loginThrottle;
        this.sessionRevocation = sessionRevocation;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.currentUser = currentUser;
        this.settingsService = settingsService;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        String address = http.getRemoteAddr();
        loginThrottle.check(address, request.username());
        Authentication credentials;
        try {
            credentials = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(request.username(), request.password()));
        } catch (AuthenticationException refused) {
            loginThrottle.failed(address, request.username());
            throw refused;
        }
        loginThrottle.succeeded(address, request.username());

        IssuedToken token = jwtService.issue((AppUserDetails) credentials.getPrincipal());

        // The session is then read back through the token that was just minted -- the
        // exact path every later request takes. A login that answered from the
        // credentials instead could hand out a token that does not produce that session.
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(jwtService.authenticationOf(token.value()));
        SecurityContextHolder.setContext(context);

        return LoginResponse.of(token, sessionOf(context.getAuthentication()));
    }

    /**
     * Bootstraps the SPA: called before routing so the guards know whether the stored
     * token is still good and which role it carries. Public, and simply reports "not
     * authenticated" when no token is presented, so the client has one code path on
     * start-up. A token that is presented but invalid is answered 401 by the resource
     * server before this method is reached, which is what tells the client to drop it.
     */
    @GetMapping("/session")
    public ResponseEntity<SessionResponse> session() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt)) {
            return ResponseEntity.ok(SessionResponse.anonymous());
        }
        return ResponseEntity.ok(sessionOf(authentication));
    }

    /**
     * Signs this device out: the token it sent is refused from now on, so a copy of it -- in a
     * browser's storage, in a phone's backup -- opens nothing any more. The account's sessions
     * on other devices stand.
     *
     * <p>Called without a token it simply answers 204; called with one that is no longer good,
     * the resource server answers 401 before this method, and the client drops it all the same.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Jwt token) {
            sessionRevocation.revoke(token);
        }
        SecurityContextHolder.clearContext();
        return ResponseEntity.noContent().build();
    }

    private SessionResponse sessionOf(Authentication authentication) {
        UserApp user = currentUser.require();
        // Roles only. Spring Security also grants FACTOR_BEARER, describing *how* the
        // caller authenticated; the front end decides what to show from *who* they are,
        // and sending it would invite the client to branch on the mechanism.
        List<String> authorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith(ROLE_PREFIX))
                .toList();
        return SessionResponse.of(user, RolePolicy.labelOf(user.getRoles()),
                RolePolicy.homePathOf(user.getRoles()), authorities, settingsService.current());
    }
}

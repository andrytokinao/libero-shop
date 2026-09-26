package com.houssen.liberoshop.web;

import com.houssen.liberoshop.entity.UserApp;
import com.houssen.liberoshop.security.AppUserDetails;
import com.houssen.liberoshop.security.CurrentUser;
import com.houssen.liberoshop.security.JwtService;
import com.houssen.liberoshop.security.JwtService.IssuedToken;
import com.houssen.liberoshop.service.RolePolicy;
import com.houssen.liberoshop.web.dto.LoginRequest;
import com.houssen.liberoshop.web.dto.LoginResponse;
import com.houssen.liberoshop.web.dto.SessionResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
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

    public AuthController(AuthenticationManager authenticationManager, JwtService jwtService,
                          CurrentUser currentUser) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.currentUser = currentUser;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        Authentication credentials = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(request.username(), request.password()));

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
     * Signing out is the client dropping its token; nothing is held here to invalidate.
     *
     * <p>The endpoint is kept all the same so the front end has one place to call and one
     * thing to change should a revocation list ever be added.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
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
                RolePolicy.homePathOf(user.getRoles()), authorities);
    }
}

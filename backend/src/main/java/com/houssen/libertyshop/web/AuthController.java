package com.houssen.libertyshop.web;

import com.houssen.libertyshop.entity.UserApp;
import com.houssen.libertyshop.security.CurrentUser;
import com.houssen.libertyshop.service.RolePolicy;
import com.houssen.libertyshop.web.dto.LoginRequest;
import com.houssen.libertyshop.web.dto.SessionResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
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
 * JSON the SPA can consume directly, and so a successful call returns the session payload
 * in the same round trip instead of forcing a follow-up request.
 *
 * <p>Sign-out is handled by Spring Security's own logout filter on {@code POST
 * /api/auth/logout} -- see {@code SecurityConfiguration} -- which is what clears the
 * context, invalidates the session and drops the cookie.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final CurrentUser currentUser;
    private final SecurityContextRepository contextRepository = new HttpSessionSecurityContextRepository();

    public AuthController(AuthenticationManager authenticationManager, CurrentUser currentUser) {
        this.authenticationManager = authenticationManager;
        this.currentUser = currentUser;
    }

    @PostMapping("/login")
    public SessionResponse login(@Valid @RequestBody LoginRequest request,
                                 HttpServletRequest httpRequest,
                                 HttpServletResponse httpResponse) {
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(request.username(), request.password()));

        // Session fixation defence: if the caller already carried a session, the
        // authenticated context gets a brand new id, so a pre-login id someone else may
        // know becomes worthless. With no session yet there is nothing to rotate --
        // saveContext below creates a fresh one.
        if (httpRequest.getSession(false) != null) {
            httpRequest.changeSessionId();
        }

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, httpRequest, httpResponse);

        return sessionOf(authentication);
    }

    /**
     * Bootstraps the SPA: called before routing so the guards know whether there is a
     * session and which role it carries. Public, and simply reports "not authenticated"
     * rather than failing, so the client has one code path on start-up.
     */
    @GetMapping("/session")
    public ResponseEntity<SessionResponse> session() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof org.springframework.security.authentication.AnonymousAuthenticationToken) {
            return ResponseEntity.ok(SessionResponse.anonymous());
        }
        return ResponseEntity.ok(sessionOf(authentication));
    }

    private SessionResponse sessionOf(Authentication authentication) {
        UserApp user = currentUser.require();
        List<String> authorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
        return SessionResponse.of(user, RolePolicy.labelOf(user.getRole()),
                RolePolicy.homePathOf(user.getRole()), authorities);
    }
}

package com.houssen.liberoshop.web;

import com.houssen.liberoshop.server.LocalNetwork;
import com.houssen.liberoshop.service.InitialSetupService;
import com.houssen.liberoshop.service.exception.BusinessRuleException;
import com.houssen.liberoshop.web.dto.CreateFirstAdminRequest;
import com.houssen.liberoshop.web.dto.UserResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * The first start of an installation: is there an account yet, and if not, create the first.
 *
 * <p>Both endpoints are public -- there is nobody to authenticate -- so the creation is held
 * to where the caller sits instead: it is accepted only from the computer the server runs on.
 * Until that account exists anyone on the shop's Wi-Fi could otherwise win the race and make
 * themselves administrator, and the owner installing the software is at that computer anyway.
 */
@RestController
@RequestMapping("/api/setup")
public class SetupController {

    private final InitialSetupService setup;

    public SetupController(InitialSetupService setup) {
        this.setup = setup;
    }

    /** Asked by the login page, which gives way to the first-account form when there is none. */
    @GetMapping("/status")
    public SetupStatus status(HttpServletRequest request) {
        boolean required = setup.isRequired();
        return new SetupStatus(required, required && isServerItself(request));
    }

    @PostMapping("/admin")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse createFirstAdmin(@Valid @RequestBody CreateFirstAdminRequest body,
                                         HttpServletRequest request) {
        if (!isServerItself(request)) {
            throw new BusinessRuleException("SETUP_ONLY_ON_SERVER",
                    "Le premier compte se cree sur l'ordinateur ou Libero Shop est installe.");
        }
        return setup.createFirstAdmin(body);
    }

    /**
     * True when the request comes from this very machine and through nothing else: a relay
     * running here -- a tunnel, a reverse proxy -- makes every caller look like the machine
     * itself, and names the real one in a header. Any such header is enough to say no.
     */
    private static boolean isServerItself(HttpServletRequest request) {
        if (request.getHeader("X-Forwarded-For") != null || request.getHeader("X-Real-IP") != null
                || request.getHeader("Forwarded") != null) {
            return false;
        }
        String address = request.getRemoteAddr();
        if (!LocalNetwork.isLocalAddress(address)) {
            return false;
        }
        try {
            // An IP literal, checked just above: parsed, never looked up.
            return InetAddress.getByName(address.trim()).isLoopbackAddress();
        } catch (UnknownHostException e) {
            return false;
        }
    }

    /**
     * @param required    no account exists yet
     * @param allowedHere the first account can be created from where this caller sits; false
     *                    once it is no longer required
     */
    public record SetupStatus(boolean required, boolean allowedHere) {
    }
}

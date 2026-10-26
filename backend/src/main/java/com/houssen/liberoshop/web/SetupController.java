package com.houssen.liberoshop.web;

import com.houssen.liberoshop.server.LocalNetwork;
import com.houssen.liberoshop.service.AdminRecoveryService;
import com.houssen.liberoshop.service.InitialSetupService;
import com.houssen.liberoshop.service.exception.BusinessRuleException;
import com.houssen.liberoshop.web.dto.RecoverAdminRequest;
import com.houssen.liberoshop.web.dto.UserResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * The first start of an installation: the server has opened an administrator account by itself,
 * and this says with which password -- to someone at the server's own computer only.
 *
 * <p>The endpoint is public -- there is nobody to authenticate yet -- so the password is held to
 * where the caller sits instead. Anyone on the shop's Wi-Fi could otherwise read it and sign in
 * as administrator before the owner does, and the owner installing the software is at that
 * computer anyway. From anywhere else the answer only says that a first password is waiting
 * there.
 *
 * <p>The same door serves the day the administrator's password is lost: again nobody can be
 * authenticated, and again it only opens at the server's computer -- see
 * {@link AdminRecoveryService} for the second proof it asks.
 */
@RestController
@RequestMapping("/api/setup")
public class SetupController {

    private final InitialSetupService setup;
    private final AdminRecoveryService recovery;

    public SetupController(InitialSetupService setup, AdminRecoveryService recovery) {
        this.recovery = recovery;
        this.setup = setup;
    }

    /** Asked by the login page, which shows the first password while it is the one in service. */
    @GetMapping("/status")
    public SetupStatus status(HttpServletRequest request) {
        return setup.firstPassword()
                .map(first -> isServerItself(request)
                        ? new SetupStatus(true, first.username(), first.password())
                        : new SetupStatus(true, null, null))
                .orElse(new SetupStatus(false, null, null));
    }

    /**
     * A lost administrator password, first step: writes a code into a file on this computer,
     * without saying where -- the publisher's support does. Reading it back is the proof asked by {@link #recover}.
     */
    @PostMapping("/recovery/code")
    public AdminRecoveryService.Started startRecovery(HttpServletRequest request) {
        requireServerItselfForRecovery(request);
        return recovery.start();
    }

    /** Second step: the code, and the new password of a super-admin account. */
    @PostMapping("/recovery/password")
    public UserResponse recover(@Valid @RequestBody RecoverAdminRequest body, HttpServletRequest request) {
        requireServerItselfForRecovery(request);
        return recovery.reset(body.code(), body.username(), body.password());
    }

    private static void requireServerItselfForRecovery(HttpServletRequest request) {
        if (!isServerItself(request)) {
            throw new BusinessRuleException("RECOVERY_ONLY_ON_SERVER",
                    "Le mot de passe administrateur se retrouve sur l'ordinateur ou Moussa Shop est installe.");
        }
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
     * @param firstPassword the administrator account opened at installation still has the
     *                      password it was given then
     * @param username      that account, or null when the caller is not at the server's computer
     * @param password      its password, or null likewise
     */
    public record SetupStatus(boolean firstPassword, String username, String password) {
    }
}

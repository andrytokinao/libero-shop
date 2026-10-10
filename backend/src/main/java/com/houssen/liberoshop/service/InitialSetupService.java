package com.houssen.liberoshop.service;

import com.houssen.liberoshop.entity.RoleApp;
import com.houssen.liberoshop.repository.UserAppRepository;
import com.houssen.liberoshop.service.exception.BusinessRuleException;
import com.houssen.liberoshop.web.dto.CreateFirstAdminRequest;
import com.houssen.liberoshop.web.dto.CreateUserRequest;
import com.houssen.liberoshop.web.dto.UserResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * The one thing an installation with no account lets a stranger do: create its first
 * super-admin.
 *
 * <p>A shipped installation starts empty -- the demonstration accounts, whose password is
 * printed in the documentation, are a developer's convenience and off by default. So nobody
 * can sign in, and the first person at the screen has to be allowed to create the account
 * everything else will be administered from. That door closes for good with the first
 * account, whoever created it and whatever its role: from then on accounts are made by a
 * super-admin, like any other.
 *
 * <p>No {@code @RequiresActiveLicense}, like the rest of the accounts: the installation is
 * minutes old, and its licence may well be the first thing this account installs.
 */
@Service
public class InitialSetupService {

    /** The account that opens everything gets more than the counter's six characters. */
    public static final int MIN_ADMIN_PASSWORD_LENGTH = 8;

    private static final Logger log = LoggerFactory.getLogger(InitialSetupService.class);

    private final UserAppRepository users;
    private final UserAccountService accounts;

    public InitialSetupService(UserAppRepository users, UserAccountService accounts) {
        this.users = users;
        this.accounts = accounts;
    }

    /** True while the installation has no account at all. */
    public boolean isRequired() {
        return users.count() == 0;
    }

    /**
     * Creates the first super-admin, once.
     *
     * <p>Synchronized, and deliberately not transactional itself: two requests arriving
     * together must not both find the table empty, and the second has to see the row the
     * first one committed. One server, one JVM -- the monitor is all it takes.
     *
     * @throws BusinessRuleException {@code SETUP_ALREADY_DONE} once any account exists
     */
    public synchronized UserResponse createFirstAdmin(CreateFirstAdminRequest request) {
        if (!isRequired()) {
            throw new BusinessRuleException("SETUP_ALREADY_DONE",
                    "Cette installation a deja un compte : connectez-vous, ou demandez un acces "
                            + "a son administrateur.");
        }
        UserResponse admin = accounts.create(new CreateUserRequest(request.fullName(), request.username(),
                request.password(), Set.of(RoleApp.SUPER_ADMIN)));
        log.info("Premier administrateur cree : {}.", admin.username());
        return admin;
    }
}

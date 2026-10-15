package com.houssen.liberoshop.web;

import com.houssen.liberoshop.security.CurrentUser;
import com.houssen.liberoshop.update.UpdateCoordinator;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A screen's answer to an update's "can this till be cut off now?".
 *
 * <p>Open to any signed-in account, not the super-admin alone: each person at a till answers for
 * their own screen. The identity is taken from the token, never the payload, so one screen cannot
 * answer for another.
 */
@RestController
@RequestMapping("/api/update")
public class MaintenanceController {

    private final UpdateCoordinator coordinator;
    private final CurrentUser currentUser;

    public MaintenanceController(UpdateCoordinator coordinator, CurrentUser currentUser) {
        this.coordinator = coordinator;
        this.currentUser = currentUser;
    }

    @PostMapping("/confirm")
    public void confirm() {
        coordinator.confirm(currentUser.require().getId());
    }

    @PostMapping("/decline")
    public void decline() {
        coordinator.decline(currentUser.require().getId());
    }
}

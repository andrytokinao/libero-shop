package com.houssen.liberoshop.web;

import com.houssen.liberoshop.security.CurrentUser;
import com.houssen.liberoshop.service.UserAccountService;
import com.houssen.liberoshop.web.dto.CreateUserRequest;
import com.houssen.liberoshop.web.dto.SetPasswordRequest;
import com.houssen.liberoshop.web.dto.UpdateUserRequest;
import com.houssen.liberoshop.web.dto.UserActivityResponse;
import com.houssen.liberoshop.web.dto.UserResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Account administration: the whole of it belongs to the super-admin, hence the one
 * annotation on the class rather than six on the methods.
 *
 * <p>No endpoint deletes an account, because the sales and hand-overs it signed must keep
 * naming someone -- {@code /disable} is what a departure looks like here.
 *
 * <p>No endpoint takes an actor either. The super-admin doing the edit is read from the
 * session and passed to the service, which is what lets it refuse the two self-inflicted
 * lockouts: switching off your own account, or taking away your own super-admin role.
 */
@RestController
@RequestMapping("/api/users")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class UserController {

    private final UserAccountService userAccountService;
    private final CurrentUser currentUser;

    public UserController(UserAccountService userAccountService, CurrentUser currentUser) {
        this.userAccountService = userAccountService;
        this.currentUser = currentUser;
    }

    @GetMapping
    public List<UserResponse> users() {
        return userAccountService.findAll();
    }

    @GetMapping("/activity")
    public List<UserActivityResponse> activity() {
        return userAccountService.findActivity();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(@Valid @RequestBody CreateUserRequest request) {
        return userAccountService.create(request);
    }

    /** Name and roles. The login handle and the password have their own rules elsewhere. */
    @PutMapping("/{id}")
    public UserResponse update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest request) {
        return userAccountService.update(id, request, currentUser.require());
    }

    @PostMapping("/{id}/enable")
    public UserResponse enable(@PathVariable Long id) {
        return userAccountService.setEnabled(id, true, currentUser.require());
    }

    @PostMapping("/{id}/disable")
    public UserResponse disable(@PathVariable Long id) {
        return userAccountService.setEnabled(id, false, currentUser.require());
    }

    /**
     * Returns the account rather than 204: the screen that called it re-renders the row, and
     * a body it can put straight back saves it a second round trip.
     */
    @PutMapping("/{id}/password")
    public UserResponse setPassword(@PathVariable Long id,
                                    @Valid @RequestBody SetPasswordRequest request) {
        return userAccountService.setPassword(id, request);
    }
}

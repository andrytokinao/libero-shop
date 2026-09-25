package com.houssen.libertyshop.web;

import com.houssen.libertyshop.service.UserAccountService;
import com.houssen.libertyshop.web.dto.UserActivityResponse;
import com.houssen.libertyshop.web.dto.UserResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class UserController {

    private final UserAccountService userAccountService;

    public UserController(UserAccountService userAccountService) {
        this.userAccountService = userAccountService;
    }

    @GetMapping
    public List<UserResponse> users() {
        return userAccountService.findAll();
    }

    @GetMapping("/activity")
    public List<UserActivityResponse> activity() {
        return userAccountService.findActivity();
    }
}

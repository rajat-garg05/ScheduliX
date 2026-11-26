package com.schedulix.auth.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.schedulix.auth.security.CurrentUser;
import com.schedulix.auth.dto.UserSummary;

@RestController
@RequestMapping("/api")
public class MeController {

    private final CurrentUser currentUser;

    public MeController(CurrentUser currentUser) {
        this.currentUser = currentUser;
    }

    @GetMapping("/me")
    public UserSummary me() {
        return UserSummary.from(currentUser.principal());
    }
}
package com.bsep.pki_system.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.Authentication;

@RestController
@RequestMapping("/api/test")
public class TestController {

    @GetMapping
    public String testConnection() {
        return "Backend server je uspešno pokrenut!";
    }

    @GetMapping("/protected")
    public String protectedRoute(Authentication authentication) {
        return "Hello " + authentication.getName() + "! You are authenticated!";
    }

    @GetMapping("/public")
    public String publicRoute() {
        return "This is a public route - no authentication needed";
    }
}

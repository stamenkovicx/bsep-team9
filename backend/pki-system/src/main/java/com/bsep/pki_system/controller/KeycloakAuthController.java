package com.bsep.pki_system.controller;

import com.bsep.pki_system.model.User;
import com.bsep.pki_system.model.UserRole;
import com.bsep.pki_system.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth/keycloak")
@CrossOrigin(origins = "*")
public class KeycloakAuthController {

    @Autowired
    private UserService userService;

    @GetMapping("/userinfo")
    public ResponseEntity<?> getUserInfo(@AuthenticationPrincipal OAuth2User principal) {
        try {
            String email = principal.getAttribute("email");
            String name = principal.getAttribute("given_name");
            String surname = principal.getAttribute("family_name");
            String organization = principal.getAttribute("organization");

            if (email == null) {
                return ResponseEntity.badRequest().body(Map.of("message", "Email not found in Keycloak token"));
            }

            // Check if user exists, if not create one
            User user = userService.findByEmail(email).orElse(null);
            if (user == null) {
                user = new User();
                user.setEmail(email);
                user.setName(name != null ? name : "");
                user.setSurname(surname != null ? surname : "");
                user.setOrganization(organization != null ? organization : "");
                user.setRole(UserRole.BASIC);
                user.setEnabled(true); // Keycloak users are pre-verified
                user.setPassword(""); // No password needed for Keycloak users
                
                user = userService.registerUser(user);
            }

            return ResponseEntity.ok(Map.of(
                    "userId", user.getId(),
                    "email", user.getEmail(),
                    "name", user.getName(),
                    "surname", user.getSurname(),
                    "role", user.getRole().toString(),
                    "organization", user.getOrganization()
            ));

        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("message", "Error processing user info: " + e.getMessage()));
        }
    }

    @GetMapping("/login-success")
    public ResponseEntity<?> loginSuccess(@AuthenticationPrincipal OAuth2User principal) {
        return ResponseEntity.ok(Map.of(
                "message", "Login successful",
                "user", principal.getAttributes()
        ));
    }

    @GetMapping("/login-failure")
    public ResponseEntity<?> loginFailure() {
        return ResponseEntity.status(401).body(Map.of("message", "Login failed"));
    }
}

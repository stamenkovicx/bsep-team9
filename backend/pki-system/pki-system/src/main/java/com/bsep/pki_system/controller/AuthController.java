package com.bsep.pki_system.controller;

import com.bsep.pki_system.dto.LoginDTO;
import com.bsep.pki_system.dto.LoginResponseDTO;
import com.bsep.pki_system.dto.RegisterDTO;
import com.bsep.pki_system.jwt.JwtService;
import com.bsep.pki_system.model.User;
import com.bsep.pki_system.model.UserRole;
import com.bsep.pki_system.service.EmailVerificationService;
import com.bsep.pki_system.service.UserService;
import com.bsep.pki_system.validator.PasswordValidator;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final UserService userService;
    private final JwtService jwtService;
    private final PasswordValidator passwordValidator;
    private final EmailVerificationService emailVerificationService;

    public AuthController(UserService userService,
                          JwtService jwtService,
                          PasswordValidator passwordValidator,
                          EmailVerificationService emailVerificationService) {
        this.userService = userService;
        this.jwtService = jwtService;
        this.passwordValidator = passwordValidator;
        this.emailVerificationService = emailVerificationService;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterDTO request) {
        // Provera da li email već postoji
        if (userService.existsByEmail(request.getEmail())) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "Email is already in use"
            ));
        }

        // Provera da li se lozinke poklapaju
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "Passwords do not match"
            ));
        }

        // Validacija jačine lozinke
        if (!passwordValidator.isValid(request.getPassword())) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", passwordValidator.getValidationMessage()
            ));
        }

        // Kreiranje korisnika
        User user = new User();
        user.setName(request.getName());
        user.setSurname(request.getSurname());
        user.setEmail(request.getEmail());
        user.setPassword(request.getPassword());
        user.setOrganization(request.getOrganization());
        user.setRole(UserRole.BASIC);
        user.setEnabled(false); // Neaktivan dok ne potvrdi email

        // Generisanje verification tokena
        String token = emailVerificationService.generateVerificationToken();
        user.setVerificationToken(token);
        user.setTokenExpiryDate(emailVerificationService.generateExpiryDate());

        // Čuvanje korisnika
        User savedUser = userService.registerUser(user);

        // Slanje verification email-a
        try {
            emailVerificationService.sendVerificationEmail(savedUser, token);
            return ResponseEntity.ok(Map.of(
                    "message", "Registration successful! Please check your email to verify your account."
            ));        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                    "message", "User registered but failed to send verification email. Please contact support."
            ));        }
    }

    @GetMapping("/verify")
    public ResponseEntity<?> verifyEmail(@RequestParam String token) {
        Optional<User> userOpt = userService.findByVerificationToken(token);

        if (userOpt.isEmpty()) {
            return ResponseEntity.badRequest().body("Invalid verification token");
        }

        User user = userOpt.get();

        // Provera da li je token istekao
        if (emailVerificationService.isTokenExpired(user.getTokenExpiryDate())) {
            return ResponseEntity.badRequest().body("Verification token has expired");
        }

        // Provera da li je nalog već verifikovan
        if (user.isEnabled()) {
            return ResponseEntity.badRequest().body("Account is already verified");
        }

        // Aktivacija naloga
        user.setEnabled(true);
        user.setVerificationToken(null); // Token može da se koristi samo jednom
        user.setTokenExpiryDate(null);
        userService.updateUser(user);

        return ResponseEntity.ok("Email verified successfully! You can now log in.");
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginDTO request) {
        User user = userService.login(request.getEmail(), request.getPassword());

        if (user == null) {
            return ResponseEntity.badRequest().body("Invalid credentials");
        }

        // Provera da li je nalog verifikovan
        if (!user.isEnabled()) {
            return ResponseEntity.badRequest().body("Please verify your email before logging in");
        }

        String token = jwtService.generateToken(user);
        return ResponseEntity.ok(new LoginResponseDTO(token, user.getId(), user.getEmail(), user.getRole().toString()));
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<?> resendVerification(@RequestParam String email) {
        Optional<User> userOpt = userService.findByEmail(email);

        if (userOpt.isEmpty()) {
            return ResponseEntity.badRequest().body("User not found");
        }

        User user = userOpt.get();

        if (user.isEnabled()) {
            return ResponseEntity.badRequest().body("Account is already verified");
        }

        // Generisanje novog tokena
        String token = emailVerificationService.generateVerificationToken();
        user.setVerificationToken(token);
        user.setTokenExpiryDate(emailVerificationService.generateExpiryDate());
        userService.updateUser(user);

        // Slanje novog email-a
        try {
            emailVerificationService.sendVerificationEmail(user, token);
            return ResponseEntity.ok("Verification email resent successfully");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Failed to resend verification email");
        }
    }
}
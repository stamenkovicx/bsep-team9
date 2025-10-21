package com.bsep.pki_system.controller;

import com.bsep.pki_system.dto.*;
import com.bsep.pki_system.jwt.JwtService;
import com.bsep.pki_system.jwt.UserPrincipal;
import com.bsep.pki_system.model.User;
import com.bsep.pki_system.model.UserRole;
import com.bsep.pki_system.service.EmailVerificationService;
import com.bsep.pki_system.service.RecaptchaService;
import com.bsep.pki_system.service.UserService;
import com.bsep.pki_system.validator.PasswordValidator;
import io.jsonwebtoken.Claims;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.bsep.pki_system.service.TwoFactorService;

import java.util.Map;
import java.util.Optional;
import com.bsep.pki_system.audit.AuditLogService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final UserService userService;
    private final JwtService jwtService;
    private final PasswordValidator passwordValidator;
    private final EmailVerificationService emailVerificationService;
    private final RecaptchaService recaptchaService;
    private final TwoFactorService twoFactorService;
    private final AuditLogService auditLogService;

    public AuthController(UserService userService,
                          JwtService jwtService,
                          PasswordValidator passwordValidator,
                          EmailVerificationService emailVerificationService,
                          RecaptchaService recaptchaService,
                          TwoFactorService twoFactorService,
                          AuditLogService auditLogService) {
        this.userService = userService;
        this.jwtService = jwtService;
        this.passwordValidator = passwordValidator;
        this.emailVerificationService = emailVerificationService;
        this.recaptchaService = recaptchaService;
        this.twoFactorService = twoFactorService;
        this.auditLogService = auditLogService;
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
        if (user.getEnabled()) {
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
    public ResponseEntity<?> login(@Valid @RequestBody LoginWith2FADTO request, HttpServletRequest httpRequest) {

        // Ako je twoFactorCode poslat, to je drugi korak prijave
        // Preskacemo reCAPTCHA proveru jer je stari token istekao
        boolean isSecondStep = request.getTwoFactorCode() != null && !request.getTwoFactorCode().isEmpty();

        if (!isSecondStep && !recaptchaService.verify(request.getRecaptchaToken())) {
            // AUDIT LOG: Neuspešna reCAPTCHA
            auditLogService.logSecurityEvent(AuditLogService.EVENT_LOGIN_FAILED,
                    "reCAPTCHA verification failed", false,
                    "email=" + request.getEmail(), httpRequest);

            return ResponseEntity.badRequest().body("reCAPTCHA verification failed");
        }

        // 1. Provera lozinke i postojanja korisnika
        User user = userService.login(request.getEmail(), request.getPassword());

        if (user == null) {
            // AUDIT LOG: Neuspešna prijava - pogrešni kredencijali
            auditLogService.logSecurityEvent(AuditLogService.EVENT_LOGIN_FAILED,
                    "Invalid credentials", false,
                    "email=" + request.getEmail(), httpRequest);

            return ResponseEntity.badRequest().body("Invalid credentials");
        }

        // Provjera da li korisnik mora da promijeni lozinku
        if (user.getPasswordChangeRequired() != null && user.getPasswordChangeRequired()) {
            String temporaryToken = jwtService.generateTemporaryToken(user);

            // AUDIT LOG: Potrebna promena lozinke
            auditLogService.logSecurityEvent(AuditLogService.EVENT_LOGIN,
                    "Password change required", true,
                    "userId=" + user.getId() + ", email=" + user.getEmail(), httpRequest);

            return ResponseEntity.status(401).body(Map.of(
                    "message", "Password change required",
                    "passwordChangeRequired", true,
                    "token", temporaryToken
            ));
        }

        // Provera verifikacije naloga
        if (!user.getEnabled()) {
            // AUDIT LOG: Nalog nije verifikovan
            auditLogService.logSecurityEvent(AuditLogService.EVENT_LOGIN_FAILED,
                    "Account not verified", false,
                    "userId=" + user.getId() + ", email=" + user.getEmail(), httpRequest);

            return ResponseEntity.badRequest().body("Please verify your email before logging in");
        }

        // 2. LOGIKA ZA 2FA
        if (user.getIs2faEnabled()) {
            String secret = user.getTwoFactorSecret();

            // Provera sigurnosti: ako je 2FA enabled, kljuc MORA postojati
            if (secret == null) {
                // AUDIT LOG: Greška u 2FA konfiguraciji
                auditLogService.logSecurityEvent(AuditLogService.EVENT_LOGIN_FAILED,
                        "2FA configuration error", false,
                        "userId=" + user.getId() + ", email=" + user.getEmail(), httpRequest);

                return ResponseEntity.status(500).body(Map.of("message", "Internal security error (Missing 2FA key)."));
            }

            // A. Ako 2FA kod NIJE poslat (prvi korak prijavljivanja)
            if (!isSecondStep) {
                // AUDIT LOG: Zahtevan 2FA kod
                auditLogService.logSecurityEvent(AuditLogService.EVENT_LOGIN,
                        "2FA code required", true,
                        "userId=" + user.getId() + ", email=" + user.getEmail(), httpRequest);

                // Signaliziramo frontendu da je potrebna 2FA provera
                return ResponseEntity.status(401).body(Map.of(
                        "message", "2FA code required.",
                        "twoFactorRequired", true
                ));
            }

            // B. Validacija unetog 2FA koda (drugi korak)
            try {
                int code = Integer.parseInt(request.getTwoFactorCode());

                // Provera koda koristeći TwoFactorService
                if (!twoFactorService.isCodeValid(secret, code)) {
                    // AUDIT LOG: Pogrešan 2FA kod
                    auditLogService.logSecurityEvent(AuditLogService.EVENT_LOGIN_FAILED,
                            "Invalid 2FA code", false,
                            "userId=" + user.getId() + ", email=" + user.getEmail(), httpRequest);

                    return ResponseEntity.status(401).body(Map.of("message", "Invalid 2FA code."));
                }
            } catch (NumberFormatException e) {
                // AUDIT LOG: Nevalidan format 2FA koda
                auditLogService.logSecurityEvent(AuditLogService.EVENT_LOGIN_FAILED,
                        "Invalid 2FA code format", false,
                        "userId=" + user.getId() + ", email=" + user.getEmail(), httpRequest);

                return ResponseEntity.status(401).body(Map.of("message", "Invalid 2FA code format. Only numbers allowed."));
            }
        }

        // 3. USPESNA PRIJAVA (Generisanje tokena)
        String token = jwtService.generateToken(user);

        // AUDIT LOG: Uspešna prijava - KORISTIMO USER OBJEKAT UMESTO UserPrincipal
        auditLogService.logSecurityEvent(AuditLogService.EVENT_LOGIN,
                "User logged in successfully", true,
                "userId=" + user.getId() + ", email=" + user.getEmail() + ", role=" + user.getRole(),
                httpRequest, user);

        return ResponseEntity.ok(new LoginResponseDTO(token, user.getId(), user.getEmail(), user.getRole().toString(),user.getIs2faEnabled()));
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<?> resendVerification(@RequestParam String email) {
        Optional<User> userOpt = userService.findByEmail(email);

        if (userOpt.isEmpty()) {
            return ResponseEntity.badRequest().body("User not found");
        }

        User user = userOpt.get();

        if (user.getEnabled()) {
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

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/2fa/setup")
    public ResponseEntity<?> setup2FA(@AuthenticationPrincipal UserPrincipal userPrincipal) {

        try {
            if (userPrincipal == null) {
                return ResponseEntity.status(401).body(Map.of("message", "User not authenticated"));
            }

            User user = userService.findByEmail(userPrincipal.getEmail())
                    .orElseThrow(() -> new RuntimeException("Authenticated user not found"));

            if (user.getIs2faEnabled() != null && user.getIs2faEnabled()) {
                return ResponseEntity.badRequest().body(Map.of("message", "2FA is already enabled."));
            }

            String qrCodeUrl = twoFactorService.generateSecretAndQr(user);

            return ResponseEntity.ok(Map.of("qrCodeUrl", qrCodeUrl));

        } catch (Exception e) {
            e.printStackTrace(); // ← Ovo će prikazati ceo stack trace
            return ResponseEntity.status(500).body(Map.of("message", "Error setting up 2FA: " + e.getMessage()));
        }
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/2fa/verify")
    public ResponseEntity<?> verify2FA(@RequestBody TwoFACodeDTO dto, @AuthenticationPrincipal UserPrincipal userPrincipal) {
        try {
            User user = userService.findByEmail(userPrincipal.getEmail())
                    .orElseThrow(() -> new RuntimeException("Authenticated user not found"));

            // Provera da li postoji privremeni kljuc i da 2FA nije već aktiviran
            if (user.getTwoFactorSecret() == null || user.getIs2faEnabled()) {
                return ResponseEntity.badRequest().body(Map.of("message", "2FA setup was not initiated or is already complete."));
            }

            // Pokusaj parsiranja koda
            int code = Integer.parseInt(dto.getCode());

            // Validacija koda
            if (twoFactorService.isCodeValid(user.getTwoFactorSecret(), code)) {
                // Ako je kod validan, aktiviraj 2FA
                twoFactorService.confirm2faActivation(user);
                return ResponseEntity.ok(Map.of("message", "2FA successfully enabled!"));
            } else {
                return ResponseEntity.badRequest().body(Map.of("message", "Invalid 2FA code. Please try again."));
            }

        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body(Map.of("message", "Invalid code format."));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("message", "Error verifying 2FA: " + e.getMessage()));
        }
    }

    // SAMO ADMIN može da registruje CA korisnike
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/register-ca")
    public ResponseEntity<?> registerCAUser(@Valid @RequestBody RegisterCADTO request) {
        try {
            // Provera da li email već postoji
            if (userService.existsByEmail(request.getEmail())) {
                return ResponseEntity.badRequest().body(Map.of(
                        "message", "Email is already in use"
                ));
            }

            // Kreiranje CA korisnika
            User user = new User();
            user.setName(request.getName());
            user.setSurname(request.getSurname());
            user.setEmail(request.getEmail());
            user.setOrganization(request.getOrganization());
            user.setRole(UserRole.CA);
            user.setEnabled(true); // CA korisnici su odmah aktivni
            user.setPasswordChangeRequired(true); // OBAVEZNA PROMENA LOZINKE PRI PRVOM LOGINU

            // Generisanje nasumične lozinke
            String temporaryPassword = userService.generateTemporaryPassword();
            user.setPassword(temporaryPassword);

            // Čuvanje korisnika
            User savedUser = userService.registerUser(user);

            // Slanje email-a sa privremenom lozinkom
            try {
                emailVerificationService.sendCAPasswordEmail(savedUser, temporaryPassword);
                return ResponseEntity.ok(Map.of(
                        "message", "CA user registered successfully! Temporary password sent to email."
                ));
            } catch (Exception e) {
                // Ako email ne uspe, obriši korisnika
                userService.deleteUser(savedUser.getId());
                return ResponseEntity.status(500).body(Map.of(
                        "message", "Failed to send email. CA user not created."
                ));
            }

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("message", "Error creating CA user: " + e.getMessage()));
        }
    }

    @PostMapping("/change-password-required")
    public ResponseEntity<?> changePasswordRequired(
            @Valid @RequestBody ChangePasswordRequiredDTO request,
            @RequestHeader("Authorization") String authorizationHeader) {

        System.out.println("Authorization header: " + authorizationHeader);

        try {
            if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
                return ResponseEntity.status(401).body(Map.of("message", "No token provided"));
            }

            String tempToken = authorizationHeader.substring(7); // ukloni "Bearer "

            String email = jwtService.getEmailFromTemporaryToken(tempToken);

            // Ako je token expired, generiši novi
            if (email == null) {
                try {
                    // Pokušaj da izvučeš email iz expired tokena
                    Claims expiredClaims = jwtService.getClaimsFromExpiredToken(tempToken);
                    email = expiredClaims.getSubject();

                    // Pronađi korisnika i generiši novi token
                    User user = userService.findByEmail(email)
                            .orElseThrow(() -> new RuntimeException("User not found"));

                    String newTempToken = jwtService.generateTemporaryToken(user);

                    return ResponseEntity.status(401).body(Map.of(
                            "message", "Token has expired. A new token has been generated.",
                            "newToken", newTempToken,
                            "retry", true
                    ));

                } catch (Exception e) {
                    return ResponseEntity.status(401).body(Map.of(
                            "message", "Invalid token. Please login again."
                    ));
                }
            }


            User user = userService.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            if (user.getPasswordChangeRequired() == null || !user.getPasswordChangeRequired()) {
                return ResponseEntity.badRequest().body(Map.of("message", "Password change not required"));
            }

            if (!passwordValidator.isValid(request.getNewPassword())) {
                return ResponseEntity.badRequest().body(Map.of("message", passwordValidator.getValidationMessage()));
            }

            if (!request.getNewPassword().equals(request.getConfirmPassword())) {
                return ResponseEntity.badRequest().body(Map.of("message", "Passwords do not match"));
            }

            userService.changePassword(user, request.getNewPassword());
            user.setPasswordChangeRequired(false);
            userService.updateUser(user);

            return ResponseEntity.ok(Map.of("message", "Password changed successfully"));

        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("message", "Error changing password: " + e.getMessage()));
        }
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@Valid @RequestBody ForgotPasswordDTO request) {

        try {
            Optional<User> userOpt = userService.findByEmail(request.getEmail());

            if (userOpt.isEmpty()) {
                return ResponseEntity.ok(Map.of(
                        "message", "If an account with that email exists, a password reset link has been sent."
                ));
            }
            User user = userOpt.get();
            // Proveri da li je nalog verifikovan
            if (!user.getEnabled()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "message", "Please verify your email before requesting a password reset"
                ));
            }

            // Inicijalizuj password reset (čuva token u bazi)
            userService.initiatePasswordReset(request.getEmail());

            user = userService.findByEmail(request.getEmail()).orElseThrow();
            emailVerificationService.sendPasswordResetEmail(user, user.getPasswordResetToken());

            return ResponseEntity.ok(Map.of(
                    "message", "If an account with that email exists, a password reset link has been sent."
            ));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of(
                    "message", "Error processing password reset request"
            ));
        }
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@Valid @RequestBody ResetPasswordDTO request) {
        try {
            // Proveri da li se lozinke poklapaju
            if (!request.getNewPassword().equals(request.getConfirmPassword())) {
                return ResponseEntity.badRequest().body(Map.of(
                        "message", "Passwords do not match"
                ));
            }
            // Validiraj jacinu lozinke
            if (!passwordValidator.isValid(request.getNewPassword())) {
                return ResponseEntity.badRequest().body(Map.of(
                        "message", passwordValidator.getValidationMessage()
                ));
            }
            // Resetuj lozinku
            userService.resetPassword(request.getToken(), request.getNewPassword());
            return ResponseEntity.ok(Map.of(
                    "message", "Password has been reset successfully. You can now log in with your new password."
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", e.getMessage()
            ));
        }
    }
}
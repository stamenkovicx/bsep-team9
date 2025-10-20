package com.bsep.pki_system.service;

import com.bsep.pki_system.model.User;
import com.warrenstrange.googleauth.GoogleAuthenticator;
import com.warrenstrange.googleauth.GoogleAuthenticatorKey;
import java.text.MessageFormat;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Service
public class TwoFactorService {

    private final GoogleAuthenticator gAuth;
    private final UserService userService; // Potreban za ažuriranje korisnika

    // Ime aplikacije koje će se videti u Authenticator aplikaciji
    private static final String APP_NAME = "BSEP_PKI";
    private static final String QR_URL_FORMAT = "otpauth://totp/{0}:{1}?secret={2}&issuer={0}";

    public TwoFactorService(GoogleAuthenticator gAuth, UserService userService) {
        this.gAuth = gAuth;
        this.userService = userService;
    }

    /**
     * Generiše tajni ključ za 2FA i QR kod URL za korisnika.
     * @param user Korisnik za koga se generiše ključ.
     * @return URL za QR kod.
     */
    public String generateSecretAndQr(User user) {
        System.out.println("=== GENERATING SECRET AND QR ===");
        System.out.println("User: " + user.getEmail());
        System.out.println("Current is2faEnabled: " + user.getIs2faEnabled());
        System.out.println("Current twoFactorSecret: " + user.getTwoFactorSecret());

        // 1. Generiši tajni ključ
        GoogleAuthenticatorKey key = gAuth.createCredentials();
        String secret = key.getKey();
        System.out.println("Generated secret: " + secret);

        // 2. Sačuvaj ključ u korisnika
        user.setTwoFactorSecret(secret);

        // NULL provera
        if (user.getIs2faEnabled() == null) {
            System.out.println("is2faEnabled was NULL, setting to FALSE");
            user.setIs2faEnabled(false);
        }

        System.out.println("Saving user to database...");
        userService.updateUser(user);
        System.out.println("User saved successfully");

        // 3. Kreiraj URL za QR kod
        String encodedApp = URLEncoder.encode(APP_NAME, StandardCharsets.UTF_8);
        String encodedEmail = URLEncoder.encode(user.getEmail(), StandardCharsets.UTF_8);

        String qrUrl = MessageFormat.format(
                QR_URL_FORMAT,
                encodedApp,
                encodedEmail,
                secret
        );

        System.out.println("QR URL: " + qrUrl);
        System.out.println("=== SECRET AND QR GENERATED ===");

        return qrUrl;
    }

    /**
     * Verifikuje da li je kod unet od strane korisnika ispravan.
     * @param secret Tajni ključ korisnika.
     * @param code Kod unet od strane korisnika.
     * @return true ako je kod validan, false inače.
     */
    public boolean isCodeValid(String secret, int code) {
        // GAuth automatski proverava 30-sekundni period i malu vremensku devijaciju
        return gAuth.authorize(secret, code);
    }

    /**
     * Potvrđuje aktivaciju 2FA nakon unosa koda.
     * @param user Korisnik.
     */
    public void confirm2faActivation(User user) {
        user.setIs2faEnabled(true);
        userService.updateUser(user);
    }
}
package com.bsep.pki_system.controller;

import com.bsep.pki_system.dto.CreateCertificateDTO;
import com.bsep.pki_system.jwt.UserPrincipal;
import com.bsep.pki_system.model.Certificate;
import com.bsep.pki_system.model.CertificateType;
import com.bsep.pki_system.model.User;
import com.bsep.pki_system.model.UserRole;
import com.bsep.pki_system.service.CertificateGeneratorService;
import com.bsep.pki_system.service.CertificateService;
import com.bsep.pki_system.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/certificates")
public class CertificateController {

    private final CertificateService certificateService;
    private final UserService userService;
    private final CertificateGeneratorService certificateGeneratorService;

    public CertificateController(CertificateService certificateService, UserService userService, CertificateGeneratorService certificateGeneratorService) {
        this.certificateService = certificateService;
        this.userService = userService;
        this.certificateGeneratorService = certificateGeneratorService;
    }

    // GET - Prikaz svih sertifikata (za admina)
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public ResponseEntity<List<Certificate>> getAllCertificates() {
        List<Certificate> certificates = certificateService.findAll();
        return ResponseEntity.ok(certificates);
    }

    // TODO: GET - Prikaz sertifikata iz njegovog lanca (za CA korisnika)
    // TODO: GET - Prikaz EE sertifikata (za obicnog korisnika)

    // GET - Sertifikati po tipu
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/type/{type}")
    public ResponseEntity<List<Certificate>> getCertificatesByType(@PathVariable CertificateType type) {
        List<Certificate> certificates = certificateService.findByType(type);
        return ResponseEntity.ok(certificates);
    }

    // GET - Sertifikati trenutno ulogovanog korisnika
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/my-certificates")
    public ResponseEntity<List<Certificate>> getMyCertificates(@AuthenticationPrincipal User user) {
        List<Certificate> certificates = certificateService.findByOwner(user);
        return ResponseEntity.ok(certificates);
    }

    // GET - Provjera validnosti sertifikata
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{id}/valid")
    public ResponseEntity<Map<String, Boolean>> isCertificateValid(@PathVariable Long id) {
        boolean isValid = certificateService.isCertificateValid(id);
        return ResponseEntity.ok(Map.of("valid", isValid));
    }

    // POST - Revokacija(povlacenje) sertifikata
    @PostMapping("/{id}/revoke")
    public ResponseEntity<?> revokeCertificate(@PathVariable Long id, @RequestBody Map<String, String> request, @AuthenticationPrincipal User user) {
        // Provjera autorizacije
        // TODO: ovo zavrsiti kad se bude radila revokacija
        /*if (!certificateService.canUserRevokeCertificate(id, user)) {
            return ResponseEntity.status(403).body(Map.of("message", "Not authorized to revoke this certificate"));
        }*/

        String reason = request.get("reason");
        certificateService.revokeCertificate(id, reason);
        return ResponseEntity.ok(Map.of("message", "Certificate revoked successfully"));
    }

    // GET - Pojedinačni sertifikat
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{id}")
    public ResponseEntity<Certificate> getCertificate(@PathVariable Long id,  @AuthenticationPrincipal User user) {
        if (!certificateService.canUserAccessCertificate(id, user)) {
            return ResponseEntity.status(403).build();
        }
        return certificateService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // metode za ROOT sertifikat:

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/root")
    public ResponseEntity<?> createRootCertificate(
            @Valid @RequestBody CreateCertificateDTO request,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {

        try {
            User user = userService.findByEmail(userPrincipal.getEmail())
                    .orElseThrow(() -> new RuntimeException("Authenticated user not found"));

            // Validacija datuma
            if (request.getValidFrom().after(request.getValidTo())) {
                return ResponseEntity.badRequest().body(Map.of(
                        "message", "Valid from date must be before valid to date"
                ));
            }

            if (request.getBasicConstraints() == null || !request.getBasicConstraints().toUpperCase().contains("CA:TRUE")) {
                return ResponseEntity.badRequest().body(Map.of(
                        "message", "Root certificate must have CA:TRUE basic constraints"
                ));
            }

            // Generisanje Root sertifikata
            Certificate certificate = certificateGeneratorService.generateRootCertificate(request, user);

            // Čuvanje u bazi
            Certificate savedCertificate = certificateService.saveCertificate(certificate);

            return ResponseEntity.ok(Map.of(
                    "message", "Root certificate created successfully",
                    "certificateId", savedCertificate.getId(),
                    "serialNumber", savedCertificate.getSerialNumber()
            ));

        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                    "message", "Error creating root certificate: " + e.getMessage()
            ));
        }
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'CA')")
    @PostMapping("/intermediate")
    public ResponseEntity<?> createIntermediateCertificate(
            @Valid @RequestBody CreateCertificateDTO request,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {

        try {
            // 1. Provera da li je issuer ID uopšte poslat
            if (request.getIssuerCertificateId() == null) {
                return ResponseEntity.badRequest().body(Map.of(
                        "message", "Issuer certificate ID is required for an intermediate certificate"
                ));
            }

            // 2. Pronalazak korisnika koji pravi sertifikat
            User owner = userService.findByEmail(userPrincipal.getEmail())
                    .orElseThrow(() -> new RuntimeException("Authenticated user not found"));

            // 3. Validacija datuma
            if (request.getValidFrom().after(request.getValidTo())) {
                return ResponseEntity.badRequest().body(Map.of(
                        "message", "Valid from date must be before valid to date"
                ));
            }

            // 4. Validacija da li je sertifikat namenjen za CA
            if (request.getBasicConstraints() == null || !request.getBasicConstraints().toUpperCase().contains("CA:TRUE")) {
                return ResponseEntity.badRequest().body(Map.of(
                        "message", "Intermediate certificate must have CA:TRUE basic constraints"
                ));
            }

            // 5. Validacija izdavaoca i generisanje sertifikata
            // Ovde pozivamo novu metodu iz CertificateService koja će obaviti sve
            Certificate savedCertificate = certificateService.createAndSaveIntermediateCertificate(request, owner);

            return ResponseEntity.ok(Map.of(
                    "message", "Intermediate certificate created successfully",
                    "certificateId", savedCertificate.getId(),
                    "serialNumber", savedCertificate.getSerialNumber()
            ));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                    "message", "Error creating intermediate certificate: " + e.getMessage()
            ));
        }
    }

    // GET - Svi Root sertifikati
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/root")
    public ResponseEntity<List<Certificate>> getAllRootCertificates() {
        List<Certificate> rootCertificates = certificateService.findByType(CertificateType.ROOT);
        return ResponseEntity.ok(rootCertificates);
    }

    // preuzimanje sertifikata (svi ulogovani - sa proverom autorizacije)
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{id}/download")
    public ResponseEntity<?> downloadCertificate(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal userPrincipal) {
        try {
            User user = userService.findByEmail(userPrincipal.getEmail())
                    .orElseThrow(() -> new RuntimeException("Authenticated user not found"));
            
            // Provjera autorizacije
            if (!certificateService.canUserAccessCertificate(id, user)) {
                return ResponseEntity.status(403).body(Map.of("message", "Not authorized to download this certificate"));
            }

            if (!certificateService.canUserAccessCertificate(id, user)) {
                return ResponseEntity.status(403).body(Map.of("message", "Not authorized to download this certificate"));
            }

            Certificate certificate = certificateService.findById(id)
                    .orElseThrow(() -> new RuntimeException("Certificate not found"));

            // 3. Izvlačenje stvarnog X509 sertifikata iz Keystore-a
            String alias = "CA_" + certificate.getSerialNumber();
            X509Certificate x509Cert = (X509Certificate) certificateGeneratorService.getKeystoreService().getCertificate(alias);
            if (x509Cert == null) {
                throw new RuntimeException("X509 Certificate not found in keystore for alias: " + alias);
            }
            // Generisanje PEM formata sertifikata
            String pemContent = generatePemContent(x509Cert);

            byte[] pemBytes = pemContent.getBytes(StandardCharsets.UTF_8);

            return ResponseEntity.ok()
                    .header("Content-Type", "application/pkix-cert")
                    .header("Content-Disposition",
                            "attachment; filename=" + certificate.getSerialNumber() + ".cer")
                    .body(pemBytes);

        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                    "message", "Error downloading certificate: " + e.getMessage()
            ));
        }
    }

    private String generatePemContent(X509Certificate certificate) throws Exception {
        // 1. Dohvatanje bajtova sertifikata u DER formatu
        byte[] derCert = certificate.getEncoded();

        // 2. Base64 enkodiranje
        String base64Cert = java.util.Base64.getEncoder().encodeToString(derCert);

        // 3. Formatiranje u PEM
        StringBuilder pem = new StringBuilder();
        pem.append("-----BEGIN CERTIFICATE-----\n");

        // Dodavanje Base64 kodiranog sadržaja sa prelomima redova (tipično 64 znaka po redu)
        int lineLength = 64;
        for (int i = 0; i < base64Cert.length(); i += lineLength) {
            int end = Math.min(i + lineLength, base64Cert.length());
            pem.append(base64Cert, i, end).append("\n");
        }

        pem.append("-----END CERTIFICATE-----\n");

        return pem.toString();
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'CA')")
    @GetMapping("/issuers")
    public ResponseEntity<List<Certificate>> getAvailableIssuers(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        try {
            // Pronalazimo kompletan User objekat da bismo znali njegovu organizaciju
            User user = userService.findByEmail(userPrincipal.getEmail())
                    .orElseThrow(() -> new RuntimeException("Authenticated user not found"));

            List<Certificate> issuers = certificateService.findValidIssuersForUser(user);
            return ResponseEntity.ok(issuers);

        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }
    //prikaz za CA korisnike
    @PreAuthorize("hasAnyRole('ADMIN', 'CA')")
    @GetMapping("/my-chain")
    public ResponseEntity<List<Certificate>> getMyCertificateChain(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        try {
            User user = userService.findByEmail(userPrincipal.getEmail())
                    .orElseThrow(() -> new RuntimeException("Authenticated user not found"));

            List<Certificate> chainCertificates = certificateService.findCertificateChainForUser(user);
            return ResponseEntity.ok(chainCertificates);

        } catch (Exception e) {
            return ResponseEntity.status(500).body(null);
        }
    }
}
package com.bsep.pki_system.controller;

import com.bsep.pki_system.dto.CreateCertificateDTO;
import com.bsep.pki_system.model.Certificate;
import com.bsep.pki_system.model.CertificateType;
import com.bsep.pki_system.model.User;
import com.bsep.pki_system.model.UserRole;
import com.bsep.pki_system.service.CertificateGeneratorService;
import com.bsep.pki_system.service.CertificateService;
import com.bsep.pki_system.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

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
    @GetMapping
    public ResponseEntity<List<Certificate>> getAllCertificates() {
        List<Certificate> certificates = certificateService.findAll();
        return ResponseEntity.ok(certificates);
    }

    // GET - Sertifikati po tipu
    @GetMapping("/type/{type}")
    public ResponseEntity<List<Certificate>> getCertificatesByType(@PathVariable CertificateType type) {
        List<Certificate> certificates = certificateService.findByType(type);
        return ResponseEntity.ok(certificates);
    }

    // GET - Sertifikati trenutno ulogovanog korisnika
    @GetMapping("/my-certificates")
    public ResponseEntity<List<Certificate>> getMyCertificates(@AuthenticationPrincipal User user) {
        List<Certificate> certificates = certificateService.findByOwner(user);
        return ResponseEntity.ok(certificates);
    }

    // GET - Provjera validnosti sertifikata
    @GetMapping("/{id}/valid")
    public ResponseEntity<Map<String, Boolean>> isCertificateValid(@PathVariable Long id) {
        boolean isValid = certificateService.isCertificateValid(id);
        return ResponseEntity.ok(Map.of("valid", isValid));
    }

    // POST - Revokacija(povlacenje) sertifikata
    @PostMapping("/{id}/revoke")
    public ResponseEntity<?> revokeCertificate(@PathVariable Long id, @RequestBody Map<String, String> request) {
        String reason = request.get("reason");
        certificateService.revokeCertificate(id, reason);
        return ResponseEntity.ok(Map.of("message", "Certificate revoked successfully"));
    }

    // GET - Pojedinačni sertifikat
    @GetMapping("/{id}")
    public ResponseEntity<Certificate> getCertificate(@PathVariable Long id) {
        return certificateService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // metode za ROOT sertifikat:

    @PostMapping("/root")
    public ResponseEntity<?> createRootCertificate(
            @RequestBody CreateCertificateDTO request,
            @AuthenticationPrincipal User user) {

        try {
            // Provjera da li je korisnik ADMIN (samo admin može kreirati Root)
            if (!user.getRole().equals(UserRole.ADMIN)) {
                return ResponseEntity.status(403).body(Map.of(
                        "message", "Only ADMIN users can create Root certificates"
                ));
            }

            // Validacija datuma
            if (request.getValidFrom().after(request.getValidTo())) {
                return ResponseEntity.badRequest().body(Map.of(
                        "message", "Valid from date must be before valid to date"
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

    // GET - Svi Root sertifikati
    @GetMapping("/root")
    public ResponseEntity<List<Certificate>> getAllRootCertificates() {
        List<Certificate> rootCertificates = certificateService.findByType(CertificateType.ROOT);
        return ResponseEntity.ok(rootCertificates);
    }
}
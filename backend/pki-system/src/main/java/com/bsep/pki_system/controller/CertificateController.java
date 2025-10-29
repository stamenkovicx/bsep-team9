package com.bsep.pki_system.controller;

import com.bsep.pki_system.dto.CreateCertificateDTO;
import com.bsep.pki_system.dto.CreateEECsrRequestDTO;
import com.bsep.pki_system.jwt.UserPrincipal;
import com.bsep.pki_system.model.Certificate;
import com.bsep.pki_system.model.CertificateType;
import com.bsep.pki_system.model.User;
import com.bsep.pki_system.model.UserRole;
import com.bsep.pki_system.service.CertificateGeneratorService;
import com.bsep.pki_system.service.CertificateService;
import com.bsep.pki_system.service.KeystoreService;
import com.bsep.pki_system.service.UserService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import com.bsep.pki_system.audit.AuditLogService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api/certificates")
public class CertificateController {

    private final CertificateService certificateService;
    private final UserService userService;
    private final CertificateGeneratorService certificateGeneratorService;
    private final KeystoreService keystoreService;
    private final AuditLogService auditLogService;

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    public CertificateController(CertificateService certificateService, UserService userService,
                                 CertificateGeneratorService certificateGeneratorService,
                                 KeystoreService keyStoreService,
                                 AuditLogService auditLogService) {
        this.certificateService = certificateService;
        this.userService = userService;
        this.certificateGeneratorService = certificateGeneratorService;
        this.keystoreService = keyStoreService;
        this.auditLogService = auditLogService;
    }

    // GET - Prikaz svih sertifikata (za admina)
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public ResponseEntity<List<Certificate>> getAllCertificates() {
        List<Certificate> certificates = certificateService.findAll();
        return ResponseEntity.ok(certificates);
    }



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
    public ResponseEntity<List<Certificate>> getMyCertificates(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        try {
            // Prvo nađi User entitet
            User user = userService.findByEmail(userPrincipal.getEmail())
                    .orElseThrow(() -> new RuntimeException("Authenticated user not found"));

            List<Certificate> certificates = certificateService.findByOwner(user); // Sada radi
            return ResponseEntity.ok(certificates);
        } catch (Exception e) {
            return ResponseEntity.status(404).build();
        }
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
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> revokeCertificate(
            @PathVariable Long id,
            @RequestBody Map<String, String> request,
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            HttpServletRequest httpRequest) {
        try {
            // Dohvatamo kompletan User objekat iz baze
            User user = userService.findByEmail(userPrincipal.getEmail())
                    .orElseThrow(() -> new RuntimeException("Authenticated user not found"));

            Certificate certificate = certificateService.findById(id)
                    .orElseThrow(() -> new RuntimeException("Certificate not found"));

            if (!canUserRevokeCertificate(certificate, user)) {
                // AUDIT LOG: Neovlašćen pokušaj revokacije
                auditLogService.logSecurityEvent(AuditLogService.EVENT_CERTIFICATE_REVOKED,
                        "Unauthorized revocation attempt", false,
                        "certificateId=" + id + ", userId=" + user.getId() + ", userRole=" + user.getRole(),
                        httpRequest);

                return ResponseEntity.status(403)
                        .body(Map.of("message", "Not authorized to revoke this certificate"));
            }

            String reason = request.get("reason");
            if (!isValidRevocationReason(reason)) {
                // AUDIT LOG: Nevalidan razlog revokacije
                auditLogService.logSecurityEvent(AuditLogService.EVENT_CERTIFICATE_REVOKED,
                        "Invalid revocation reason", false,
                        "certificateId=" + id + ", reason=" + reason, httpRequest);

                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Invalid revocation reason"));
            }

            certificateService.revokeCertificate(id, reason);

            // AUDIT LOG: Uspešna revokacija sertifikata
            auditLogService.logSecurityEvent(AuditLogService.EVENT_CERTIFICATE_REVOKED,
                    "Certificate revoked successfully", true,
                    "certificateId=" + id + ", serialNumber=" + certificate.getSerialNumber() +
                            ", reason=" + reason + ", revokedBy=" + user.getEmail(), httpRequest);

            // Ne vraćamo CRL URL ovde, klijent će ga sam formirati ako je potrebno
            return ResponseEntity.ok(Map.of(
                    "message", "Certificate revoked successfully"
            ));

        } catch (Exception e) {
            // AUDIT LOG: Greška pri revokaciji
            auditLogService.logSecurityEvent(AuditLogService.EVENT_CERTIFICATE_REVOKED,
                    "Certificate revocation failed", false,
                    "certificateId=" + id + ", error=" + e.getMessage(), httpRequest);

            logger.error("Error message", e);
            return ResponseEntity.status(500)
                    .body(Map.of("message", "Error revoking certificate: " + e.getMessage()));
        }
    }

    private boolean canUserRevokeCertificate(Certificate certificate, User user) {
        // ADMIN može sve
        if (user.getRole() == UserRole.ADMIN) return true;

        // Vlasnik može svoj intermediate sertifikat
        if (certificate.getOwner().getId().equals(user.getId())) {
            // Običan korisnik (USER/BASIC) sme da povuče SAMO svoj EE sertifikat
            if (user.getRole() == UserRole.BASIC) {
                return certificate.getType() == CertificateType.END_ENTITY;
            }
            // CA korisnik sme da povuče svoj Intermediate sertifikat ili EE koji je u lancu.
            return true;
        }

        // CA može sertifikate iz svoje organizacije (Intermediate i EE, ne ROOT)
        if (user.getRole() == UserRole.CA) {
            // Proveravamo da li je iz iste organizacije
            return certificate.getOwner().getOrganization().equals(user.getOrganization())
                    && certificate.getType() != CertificateType.ROOT; // Ne sme da povuče ROOT
        }
    return false;
    }


    private boolean isValidRevocationReason(String reason) {
        List<String> validReasons = List.of(
                "unspecified", "keyCompromise", "cACompromise",
                "affiliationChanged", "superseded", "cessationOfOperation",
                "certificateHold", "removeFromCRL", "privilegeWithdrawn", "aACompromise"
        );
        return reason != null && validReasons.contains(reason);
    }

    // GET - Pojedinačni sertifikat
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{id}")
    public ResponseEntity<Certificate> getCertificate(@PathVariable Long id,
                                                      @AuthenticationPrincipal UserPrincipal userPrincipal,
                                                      HttpServletRequest httpRequest) {
        try {
            // Dohvatamo kompletan User objekat iz baze
            User user = userService.findByEmail(userPrincipal.getEmail())
                    .orElseThrow(() -> new RuntimeException("Authenticated user not found"));
            // Sada provera autorizacije koristi pravi User entitet
            if (!certificateService.canUserAccessCertificate(id, user)) {
                // AUDIT LOG: Neovlašćen pristup sertifikatu
                auditLogService.logSecurityEvent(AuditLogService.EVENT_CERTIFICATE_VIEWED,
                        "Unauthorized certificate access", false,
                        "certificateId=" + id + ", userId=" + user.getId(), httpRequest);

                return ResponseEntity.status(403).build();
            }
            return certificateService.findById(id)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            return ResponseEntity.status(404).build();
        }
    }
    // metode za ROOT sertifikat:

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/root")
    public ResponseEntity<?> createRootCertificate(
            @Valid @RequestBody CreateCertificateDTO request,
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            HttpServletRequest httpRequest) {

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

            // AUDIT LOG: Root sertifikat kreiran
            auditLogService.logSecurityEvent(AuditLogService.EVENT_CERTIFICATE_ISSUED,
                    "Root certificate created", true,
                    "certificateId=" + savedCertificate.getId() + ", serialNumber=" + savedCertificate.getSerialNumber() +
                            ", subject=" + savedCertificate.getSubject(), httpRequest);

            return ResponseEntity.ok(Map.of(
                    "message", "Root certificate created successfully",
                    "certificateId", savedCertificate.getId(),
                    "serialNumber", savedCertificate.getSerialNumber()
            ));

        } catch (Exception e) {
            // AUDIT LOG: Greška pri kreiranju root sertifikata
            auditLogService.logSecurityEvent(AuditLogService.EVENT_CERTIFICATE_ISSUED,
                    "Root certificate creation failed", false,
                    "error=" + e.getMessage(), httpRequest);

            return ResponseEntity.status(500).body(Map.of(
                    "message", "Error creating root certificate: " + e.getMessage()
            ));
        }
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'CA')")
    @PostMapping("/intermediate")
    public ResponseEntity<?> createIntermediateCertificate(
            @Valid @RequestBody CreateCertificateDTO request,
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            HttpServletRequest httpRequest) {

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

            // AUDIT LOG: Intermediate sertifikat kreiran
            auditLogService.logSecurityEvent(AuditLogService.EVENT_CERTIFICATE_ISSUED,
                    "Intermediate certificate created", true,
                    "certificateId=" + savedCertificate.getId() + ", serialNumber=" + savedCertificate.getSerialNumber() +
                            ", subject=" + savedCertificate.getSubject() + ", issuerId=" + request.getIssuerCertificateId(),
                    httpRequest);

            return ResponseEntity.ok(Map.of(
                    "message", "Intermediate certificate created successfully",
                    "certificateId", savedCertificate.getId(),
                    "serialNumber", savedCertificate.getSerialNumber()
            ));

        } catch (IllegalArgumentException e) {
            // AUDIT LOG: Neuspešno kreiranje intermediate sertifikata
            auditLogService.logSecurityEvent(AuditLogService.EVENT_CERTIFICATE_ISSUED,
                    "Intermediate certificate creation failed", false,
                    "error=" + e.getMessage(), httpRequest);

            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            // AUDIT LOG: Greška pri kreiranju intermediate sertifikata
            auditLogService.logSecurityEvent(AuditLogService.EVENT_CERTIFICATE_ISSUED,
                    "Intermediate certificate creation error", false,
                    "error=" + e.getMessage(), httpRequest);

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
    public ResponseEntity<?> downloadCertificate(@PathVariable Long id,
                                                 @AuthenticationPrincipal UserPrincipal userPrincipal,
                                                 HttpServletRequest httpRequest) {
        try {
            User user = userService.findByEmail(userPrincipal.getEmail())
                    .orElseThrow(() -> new RuntimeException("Authenticated user not found"));
            
            // Provjera autorizacije
            if (!certificateService.canUserAccessCertificate(id, user)) {
                // AUDIT LOG: Neovlašćen download
                auditLogService.logSecurityEvent(AuditLogService.EVENT_CERTIFICATE_VIEWED,
                        "Unauthorized download attempt", false,
                        "certificateId=" + id + ", userId=" + user.getId(), httpRequest);

                return ResponseEntity.status(403).body(Map.of("message", "Not authorized to download this certificate"));
            }

            Certificate certificate = certificateService.findById(id)
                    .orElseThrow(() -> new RuntimeException("Certificate not found"));

            byte[] pemBytes;
            String pemContent;
            
            // Check if certificate has PEM data stored in database (for EE certificates)
            if (certificate.getPemData() != null && !certificate.getPemData().isEmpty()) {
                // Use PEM data from database
                pemContent = certificate.getPemData();
                pemBytes = pemContent.getBytes(StandardCharsets.UTF_8);
            } else {
                // For CA certificates (ROOT/INTERMEDIATE), retrieve from keystore
                String alias;
                if (certificate.getType() == CertificateType.END_ENTITY) {
                    alias = "EE_" + certificate.getSerialNumber();
                } else {
                    alias = "CA_" + certificate.getSerialNumber();
                }
                
                X509Certificate x509Cert = (X509Certificate) certificateGeneratorService.getKeystoreService().getCertificate(alias);
                if (x509Cert == null) {
                    throw new RuntimeException("X509 Certificate not found in keystore for alias: " + alias);
                }
                
                // Generate PEM format from X509 certificate
                pemContent = generatePemContent(x509Cert);
                pemBytes = pemContent.getBytes(StandardCharsets.UTF_8);
            }

            // AUDIT LOG: Uspešan download sertifikata
            auditLogService.logSecurityEvent(AuditLogService.EVENT_CERTIFICATE_VIEWED,
                    "Certificate downloaded", true,
                    "certificateId=" + id + ", serialNumber=" + certificate.getSerialNumber() +
                            ", type=" + certificate.getType(), httpRequest);

            return ResponseEntity.ok()
                    .header("Content-Type", "application/pkix-cert")
                    .header("Content-Disposition",
                            "attachment; filename=" + certificate.getSerialNumber() + ".cer")
                    .body(pemBytes);

        } catch (Exception e) {
            // AUDIT LOG: Greška pri downloadu
            auditLogService.logSecurityEvent(AuditLogService.EVENT_CERTIFICATE_VIEWED,
                    "Certificate download failed", false,
                    "certificateId=" + id + ", error=" + e.getMessage(), httpRequest);

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

    @PreAuthorize("hasAnyRole('ADMIN', 'CA', 'BASIC')")
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

    @PreAuthorize("hasAnyRole('ADMIN', 'BASIC', 'CA')")
    @PostMapping("/end-entity/csr")
    public ResponseEntity<?> createEECertificateFromCsr(
            @Valid @RequestBody CreateEECsrRequestDTO request, // 🔥 Korišćenje novog DTO-a
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            HttpServletRequest httpRequest) {

        try {
            User owner = userService.findByEmail(userPrincipal.getEmail())
                    .orElseThrow(() -> new RuntimeException("Authenticated user not found"));

            // Jackson je automatski parsirao sva polja u ispravne tipove (String, Date, Long)
            String csrPem = request.getCsrPem();
            Date validTo = request.getValidTo();
            Long issuerCertificateId = request.getIssuerCertificateId();

            Date validFrom = new Date();

            if (validTo.before(validFrom)) {
                // AUDIT LOG: Nevalidan datum
                auditLogService.logSecurityEvent(AuditLogService.EVENT_CERTIFICATE_ISSUED,
                        "Invalid certificate date", false,
                        "validTo=" + validTo + ", validFrom=" + validFrom, httpRequest);

                return ResponseEntity.badRequest().body(Map.of("message", "Valid to date must be after today's date."));
            }

            // Poziv servisa za generisanje i čuvanje EE sertifikata
            Certificate savedCertificate = certificateService.createAndSaveEECertificateFromCsr(
                    csrPem, validFrom, validTo, issuerCertificateId, owner);

            // AUDIT LOG: EE sertifikat kreiran iz CSR-a
            auditLogService.logSecurityEvent(AuditLogService.EVENT_CERTIFICATE_ISSUED,
                    "End-Entity certificate created from CSR", true,
                    "certificateId=" + savedCertificate.getId() + ", serialNumber=" + savedCertificate.getSerialNumber() +
                            ", subject=" + savedCertificate.getSubject() + ", issuerId=" + issuerCertificateId,
                    httpRequest);


            return ResponseEntity.ok(Map.of(
                    "message", "End-Entity certificate created successfully from CSR",
                    "certificateId", savedCertificate.getId(),
                    "serialNumber", savedCertificate.getSerialNumber()
            ));

        } catch (IllegalArgumentException e) {
            // AUDIT LOG: Neuspešno kreiranje EE sertifikata
            auditLogService.logSecurityEvent(AuditLogService.EVENT_CERTIFICATE_ISSUED,
                    "End-Entity certificate creation failed", false,
                    "error=" + e.getMessage(), httpRequest);

            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            // AUDIT LOG: Greška pri kreiranju EE sertifikata
            auditLogService.logSecurityEvent(AuditLogService.EVENT_CERTIFICATE_ISSUED,
                    "End-Entity certificate creation error", false,
                    "error=" + e.getMessage(), httpRequest);
            logger.error("Error message", e);

            logger.error("Error message", e);
            return ResponseEntity.status(500).body(Map.of(
                    "message", "Error creating End-Entity certificate: " + e.getMessage()
            ));
        }
    }

    @PreAuthorize("hasAnyRole('BASIC')")
    @GetMapping("/end-entity")
    public ResponseEntity<?> getMyEESertificates(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        try {
            User user = userService.findByEmail(userPrincipal.getEmail())
                    .orElseThrow(() -> new RuntimeException("Authenticated user not found"));

            // Vraća sve sertifikate gde je 'owner' trenutni korisnik i tip je END_ENTITY
            List<Certificate> certificates = certificateService.findByOwnerIdAndType(user.getId(), CertificateType.END_ENTITY);
            return ResponseEntity.ok(certificates);
        } catch (Exception e) {
            logger.error("Error loading End-Entity certificates for BASIC user", e);
            // Return empty list instead of crashing
            return ResponseEntity.ok(List.of());
        }
    }

    // GET - Get certificate PEM data for display/download
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{id}/pem")
    public ResponseEntity<?> getCertificatePem(@PathVariable Long id,
                                               @AuthenticationPrincipal UserPrincipal userPrincipal,
                                               HttpServletRequest httpRequest) {
        try {
            User user = userService.findByEmail(userPrincipal.getEmail())
                    .orElseThrow(() -> new RuntimeException("Authenticated user not found"));

            // Provjera autorizacije
            if (!certificateService.canUserAccessCertificate(id, user)) {
                auditLogService.logSecurityEvent(AuditLogService.EVENT_CERTIFICATE_VIEWED,
                        "Unauthorized access to PEM data", false,
                        "certificateId=" + id + ", userId=" + user.getId(), httpRequest);
                return ResponseEntity.status(403).body(Map.of("message", "Not authorized to access this certificate"));
            }

            Certificate certificate = certificateService.findById(id)
                    .orElseThrow(() -> new RuntimeException("Certificate not found"));

            String pemData;
            
            // Check if certificate has PEM data stored in database
            if (certificate.getPemData() != null && !certificate.getPemData().isEmpty()) {
                pemData = certificate.getPemData();
            } else {
                // Retrieve from keystore and convert to PEM
                String alias;
                if (certificate.getType() == CertificateType.END_ENTITY) {
                    alias = "EE_" + certificate.getSerialNumber();
                } else {
                    alias = "CA_" + certificate.getSerialNumber();
                }
                
                X509Certificate x509Cert = (X509Certificate) certificateGeneratorService.getKeystoreService().getCertificate(alias);
                if (x509Cert == null) {
                    throw new RuntimeException("X509 Certificate not found in keystore for alias: " + alias);
                }
                
                pemData = generatePemContent(x509Cert);
            }

            auditLogService.logSecurityEvent(AuditLogService.EVENT_CERTIFICATE_VIEWED,
                    "PEM data retrieved", true,
                    "certificateId=" + id + ", serialNumber=" + certificate.getSerialNumber(), httpRequest);

            return ResponseEntity.ok(Map.of("pemData", pemData));

        } catch (Exception e) {
            auditLogService.logSecurityEvent(AuditLogService.EVENT_CERTIFICATE_VIEWED,
                    "Failed to retrieve PEM data", false,
                    "certificateId=" + id + ", error=" + e.getMessage(), httpRequest);
            
            logger.error("Error retrieving PEM data", e);
            return ResponseEntity.status(500).body(Map.of("message", "Error retrieving certificate PEM data: " + e.getMessage()));
        }
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'BASIC', 'CA')")
    @GetMapping("/end-entity/download/{serialNumber}")
    public ResponseEntity<byte[]> downloadEECertificate(@PathVariable String serialNumber,
                                                        @AuthenticationPrincipal UserPrincipal userPrincipal,
                                                        HttpServletRequest httpRequest) {
        try {
            User user = userService.findByEmail(userPrincipal.getEmail())
                    .orElseThrow(() -> new RuntimeException("Authenticated user not found"));

            Certificate eeCertificate = certificateService.findBySerialNumber(serialNumber)
                    .orElseThrow(() -> new IllegalArgumentException("Certificate not found"));

            // Provera da li sertifikat je tipa END_ENTITY
            if (eeCertificate.getType() != CertificateType.END_ENTITY) {
                // AUDIT LOG: Pokušaj download-a non-EE sertifikata kao EE
                auditLogService.logSecurityEvent(AuditLogService.EVENT_CERTIFICATE_VIEWED,
                        "Invalid certificate type for EE download", false,
                        "serialNumber=" + serialNumber + ", type=" + eeCertificate.getType(), httpRequest);

                return ResponseEntity.status(403).body(null);
            }

            // Provera da li korisnik ima pravo da preuzme:
            // 1. ADMIN može da preuzme bilo koji EE sertifikat
            // 2. CA može da preuzme bilo koji EE sertifikat
            // 3. BASIC korisnici mogu samo svoje EE sertifikate
            if ((user.getRole() != UserRole.ADMIN && user.getRole() != UserRole.CA) && 
                !eeCertificate.getOwner().getId().equals(user.getId())) {
                // AUDIT LOG: Neovlašćen download EE sertifikata
                auditLogService.logSecurityEvent(AuditLogService.EVENT_CERTIFICATE_VIEWED,
                        "Unauthorized EE certificate download attempt", false,
                        "serialNumber=" + serialNumber + ", userId=" + user.getId(), httpRequest);

                return ResponseEntity.status(403).body(null);
            }

            // Učitavanje i vraćanje X.509 sertifikata
            byte[] certBytes = keystoreService.getCertificateBytes("EE_" + serialNumber);

            // AUDIT LOG: Uspešan download EE sertifikata
            auditLogService.logSecurityEvent(AuditLogService.EVENT_CERTIFICATE_VIEWED,
                    "End-Entity certificate downloaded", true,
                    "serialNumber=" + serialNumber + ", subject=" + eeCertificate.getSubject(),
                    httpRequest);

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("application/x-x509-ca-cert"))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + serialNumber + ".cer")
                    .body(certBytes);

        } catch (Exception e) {
            // AUDIT LOG: Greška pri downloadu EE sertifikata
            auditLogService.logSecurityEvent(AuditLogService.EVENT_CERTIFICATE_VIEWED,
                    "End-Entity certificate download failed", false,
                    "serialNumber=" + serialNumber + ", error=" + e.getMessage(), httpRequest);

            logger.error("Error message", e);
            return ResponseEntity.status(500).build();
        }
    }
}
package com.bsep.pki_system.controller;

import com.bsep.pki_system.model.Certificate;
import com.bsep.pki_system.model.CertificateType;
import com.bsep.pki_system.service.CRLService;
import com.bsep.pki_system.service.CertificateService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.bsep.pki_system.audit.AuditLogService;
import com.bsep.pki_system.jwt.UserPrincipal;
import com.bsep.pki_system.model.User;
import com.bsep.pki_system.model.UserRole;
import com.bsep.pki_system.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api/crl")
public class CRLController {

    private static final Logger logger = LoggerFactory.getLogger(CRLController.class);
    
    private final CRLService crlService;
    private final CertificateService certificateService;
    private final AuditLogService auditLogService;
    private final UserService userService;

    public CRLController(CRLService crlService, CertificateService certificateService,
                         AuditLogService auditLogService, UserService userService) {
        this.crlService = crlService;
        this.certificateService = certificateService;
        this.auditLogService = auditLogService;
        this.userService = userService;
    }

    //Preuzimanje CRL liste za dati CA sertifikat
    // URL format: /api/crl/{serialNumber}.crl
    @PreAuthorize("hasAnyRole('ADMIN', 'CA')")
    @GetMapping("/{serialNumber}.crl")
    public ResponseEntity<byte[]> downloadCRL(@PathVariable String serialNumber, 
                                              @AuthenticationPrincipal UserPrincipal userPrincipal,
                                              HttpServletRequest httpRequest) {
        try {
            User user = userService.findByEmail(userPrincipal.getEmail())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            // Pronađi CA sertifikat
            Certificate caCertificate = certificateService.findBySerialNumber(serialNumber)
                    .orElseThrow(() -> new RuntimeException("CA certificate not found"));

            // Provera da li je sertifikat zaista CA
            if (caCertificate.getIsCA() == null || !caCertificate.getIsCA()) {
                auditLogService.logSecurityEvent(AuditLogService.EVENT_CERTIFICATE_VIEWED,
                        "Attempted to download CRL for non-CA certificate", false,
                        "serialNumber=" + serialNumber, httpRequest);
                return ResponseEntity.status(403).build();
            }

            // CA korisnici mogu da preuzmu CRL samo za sertifikate iz svoje organizacije
            // ADMIN može da preuzme bilo koji CRL
            if (user.getRole() == UserRole.CA && caCertificate.getType() != CertificateType.ROOT) {
                // Za non-ROOT sertifikate, proveri organizaciju
                if (!certificateService.isCertificateInUserOrganizationChain(caCertificate, user.getOrganization())) {
                    auditLogService.logSecurityEvent(AuditLogService.EVENT_CERTIFICATE_VIEWED,
                            "Unauthorized CRL download attempt", false,
                            "serialNumber=" + serialNumber + ", userId=" + user.getId(), httpRequest);
                    return ResponseEntity.status(403).build();
                }
            }

            // Generiši CRL
            byte[] crlBytes = crlService.getOrGenerateCRL(caCertificate);

            // AUDIT LOG: Uspešno preuzimanje CRL liste
            auditLogService.logSecurityEvent(AuditLogService.EVENT_CERTIFICATE_VIEWED,
                    "CRL list downloaded successfully", true,
                    "caSerialNumber=" + serialNumber + ", caSubject=" + caCertificate.getSubject() +
                            ", crlSize=" + crlBytes.length + " bytes", httpRequest);

            // Vrati kao .crl fajl
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("application/pkix-crl"))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=" + serialNumber + ".crl")
                    .body(crlBytes);

        } catch (RuntimeException e) {
            // AUDIT LOG: Greška pri generisanju CRL liste
            auditLogService.logSecurityEvent(AuditLogService.EVENT_CERTIFICATE_VIEWED,
                    "CRL generation/download failed", false,
                    "serialNumber=" + serialNumber + ", error=" + e.getMessage(), httpRequest);
            
            logger.error("CRL download error", e);
            return ResponseEntity.status(500).body(("Error: " + e.getMessage()).getBytes());
        } catch (Exception e) {
            // AUDIT LOG: Greška pri generisanju CRL liste
            auditLogService.logSecurityEvent(AuditLogService.EVENT_CERTIFICATE_VIEWED,
                    "CRL generation/download failed", false,
                    "serialNumber=" + serialNumber + ", error=" + e.getMessage(), httpRequest);

            logger.error("CRL generation failed for serial number: " + serialNumber, e);
            return ResponseEntity.status(500).body(("Error generating CRL: " + e.getMessage()).getBytes());
        }
    }
}
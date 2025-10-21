package com.bsep.pki_system.controller;

import com.bsep.pki_system.model.Certificate;
import com.bsep.pki_system.service.CRLService;
import com.bsep.pki_system.service.CertificateService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.bsep.pki_system.audit.AuditLogService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;

@RestController
@RequestMapping("/api/crl")
public class CRLController {

    private final CRLService crlService;
    private final CertificateService certificateService;
    private final AuditLogService auditLogService;

    public CRLController(CRLService crlService, CertificateService certificateService,
                         AuditLogService auditLogService) {
        this.crlService = crlService;
        this.certificateService = certificateService;
        this.auditLogService = auditLogService;
    }

    //Preuzimanje CRL liste za dati CA sertifikat
    // URL format: /api/crl/{serialNumber}.crl
    @GetMapping("/{serialNumber}.crl")
    public ResponseEntity<byte[]> downloadCRL(@PathVariable String serialNumber, HttpServletRequest httpRequest) {
        try {
            // Pronađi CA sertifikat
            Certificate caCertificate = certificateService.findBySerialNumber(serialNumber)
                    .orElseThrow(() -> new RuntimeException("CA certificate not found"));

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

        } catch (Exception e) {
            // AUDIT LOG: Greška pri generisanju CRL liste
            auditLogService.logSecurityEvent(AuditLogService.EVENT_CERTIFICATE_VIEWED,
                    "CRL generation/download failed", false,
                    "serialNumber=" + serialNumber + ", error=" + e.getMessage(), httpRequest);

            return ResponseEntity.status(500).build();
        }
    }
}
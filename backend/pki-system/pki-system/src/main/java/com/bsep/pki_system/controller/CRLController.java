package com.bsep.pki_system.controller;

import com.bsep.pki_system.model.Certificate;
import com.bsep.pki_system.service.CRLService;
import com.bsep.pki_system.service.CertificateService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/crl")
public class CRLController {

    private final CRLService crlService;
    private final CertificateService certificateService;

    public CRLController(CRLService crlService, CertificateService certificateService) {
        this.crlService = crlService;
        this.certificateService = certificateService;
    }

    //Preuzimanje CRL liste za dati CA sertifikat
    // URL format: /api/crl/{serialNumber}.crl
    @GetMapping("/{serialNumber}.crl")
    public ResponseEntity<byte[]> downloadCRL(@PathVariable String serialNumber) {
        try {
            // Pronađi CA sertifikat
            Certificate caCertificate = certificateService.findBySerialNumber(serialNumber)
                    .orElseThrow(() -> new RuntimeException("CA certificate not found"));

            // Generiši CRL
            byte[] crlBytes = crlService.generateCRL(caCertificate);

            // Vrati kao .crl fajl
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("application/pkix-crl"))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=" + serialNumber + ".crl")
                    .body(crlBytes);

        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }
}
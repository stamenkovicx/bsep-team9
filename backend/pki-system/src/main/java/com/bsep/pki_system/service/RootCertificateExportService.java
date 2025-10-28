package com.bsep.pki_system.service;

import com.bsep.pki_system.model.Certificate;
import com.bsep.pki_system.model.CertificateType;
import com.bsep.pki_system.model.CertificateStatus;
import com.bsep.pki_system.repository.CertificateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.FileOutputStream;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Date;

@Service
public class RootCertificateExportService {

    private static final Logger logger = LoggerFactory.getLogger(RootCertificateExportService.class);
    private final CertificateRepository certificateRepository;
    private final KeystoreService keystoreService;

    public RootCertificateExportService(CertificateRepository certificateRepository,
                                       KeystoreService keystoreService) {
        this.certificateRepository = certificateRepository;
        this.keystoreService = keystoreService;
    }

    /**
     * Exports the first valid Root certificate to a .crt file
     * This file can be installed in the browser's trust store
     */
    public void exportRootCertificate() {
        try {
            // Find the first valid Root certificate
            Certificate rootCert = certificateRepository.findAll().stream()
                    .filter(cert -> cert.getType() == CertificateType.ROOT)
                    .filter(cert -> cert.getStatus() == CertificateStatus.VALID)
                    .filter(cert -> {
                        Date now = new Date();
                        return cert.getValidFrom().before(now) && cert.getValidTo().after(now);
                    })
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("No valid Root certificate found"));

            // Load the certificate from keystore
            X509Certificate x509Cert = (X509Certificate) keystoreService.getCertificate("CA_" + rootCert.getSerialNumber());
            
            if (x509Cert == null) {
                throw new RuntimeException("Could not find Root certificate in keystore");
            }

            // Convert to PEM format
            String pemContent = convertToPEM(x509Cert);

            // Write to file
            String filename = "root-ca.crt";
            try (FileOutputStream fos = new FileOutputStream(filename)) {
                fos.write(pemContent.getBytes());
            }

            logger.info("Root CA certificate exported to: {}", filename);
            logger.info("To install in Windows:");
            logger.info("  1. Double-click root-ca.crt");
            logger.info("  2. Click 'Install Certificate...'");
            logger.info("  3. Select 'Local Machine' → 'Place all certificates in the following store'");
            logger.info("  4. Click 'Browse' → Select 'Trusted Root Certification Authorities'");
            logger.info("  5. Click 'Next' → 'Finish' → 'Yes'");

        } catch (Exception e) {
            logger.error("Error exporting Root certificate: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to export Root certificate", e);
        }
    }

    private String convertToPEM(X509Certificate cert) throws Exception {
        // Get DER encoded bytes
        byte[] derCert = cert.getEncoded();

        // Base64 encode
        String base64Cert = Base64.getEncoder().encodeToString(derCert);

        // Format as PEM
        StringBuilder pem = new StringBuilder();
        pem.append("-----BEGIN CERTIFICATE-----\n");

        // Add Base64 content with line breaks (64 chars per line)
        int lineLength = 64;
        for (int i = 0; i < base64Cert.length(); i += lineLength) {
            int end = Math.min(i + lineLength, base64Cert.length());
            pem.append(base64Cert, i, end).append("\n");
        }

        pem.append("-----END CERTIFICATE-----\n");

        return pem.toString();
    }
}


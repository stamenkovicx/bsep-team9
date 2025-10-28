package com.bsep.pki_system.service;

import com.bsep.pki_system.model.Certificate;
import com.bsep.pki_system.model.CertificateType;
import com.bsep.pki_system.model.CertificateStatus;
import com.bsep.pki_system.repository.CertificateRepository;
import com.bsep.pki_system.repository.KeystorePasswordRepository;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.FileOutputStream;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.Optional;

@Service
public class HttpsCertificateService {

    private static final Logger logger = LoggerFactory.getLogger(HttpsCertificateService.class);
    private final CertificateRepository certificateRepository;
    private final KeystoreService keystoreService;
    private final CertificateService certificateService;
    private final KeystorePasswordRepository keystorePasswordRepository;

    static {
        java.security.Security.addProvider(new BouncyCastleProvider());
    }

    public HttpsCertificateService(CertificateRepository certificateRepository,
                                   KeystoreService keystoreService,
                                   CertificateService certificateService,
                                   KeystorePasswordRepository keystorePasswordRepository) {
        this.certificateRepository = certificateRepository;
        this.keystoreService = keystoreService;
        this.certificateService = certificateService;
        this.keystorePasswordRepository = keystorePasswordRepository;
    }

    /**
     * Generates or retrieves an HTTPS certificate for the server
     * Uses the PKI system's Root certificate to sign a server certificate
     */
    @Transactional
    public void generateHttpsCertificate() {
        try {
            // Check if both the database entry AND the file exist
            File certFile = new File("server.p12");
            Optional<Certificate> existingCert = certificateRepository.findBySerialNumber("HTTPS_SERVER");
            
            if (existingCert.isPresent() && certFile.exists()) {
                logger.info("HTTPS certificate already exists (both in DB and file), skipping generation");
                return;
            }
            
            if (existingCert.isPresent() && !certFile.exists()) {
                logger.warn("HTTPS certificate exists in database but server.p12 file is missing. Regenerating...");
                // Delete the keystore password first (to satisfy foreign key constraint)
                keystorePasswordRepository.deleteByCertificateId(existingCert.get().getId());
                // Now delete the certificate
                certificateRepository.delete(existingCert.get());
            }

            // Find the first valid Root certificate
            Certificate rootCert = certificateRepository.findAll().stream()
                    .filter(cert -> cert.getType() == CertificateType.ROOT)
                    .filter(cert -> cert.getStatus() == CertificateStatus.VALID)
                    .filter(cert -> {
                        Date now = new Date();
                        return cert.getValidFrom().before(now) && cert.getValidTo().after(now);
                    })
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("No valid Root certificate found. Please create a Root certificate first."));

            logger.info("Found valid Root certificate: {}", rootCert.getSerialNumber());

            // Generate key pair for HTTPS server
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
            keyGen.initialize(2048, new SecureRandom());
            KeyPair serverKeyPair = keyGen.generateKeyPair();

            // Create X500Name for server certificate
            X500Name subjectName = new X500Name("CN=localhost, O=PKI Server, C=RS");

            // Get Root certificate from keystore
            X509Certificate rootX509Cert = (X509Certificate) keystoreService.getCertificate("CA_" + rootCert.getSerialNumber());
            
            // Create X500Name for issuer (Root certificate's subject)
            X500Name issuerName = new X500Name(rootX509Cert.getSubjectX500Principal().getName());

            // Generate serial number for HTTPS certificate
            BigInteger serialNumber = new BigInteger(128, new SecureRandom());
            
            // Calculate validity dates (1 year from now)
            Date validFrom = new Date();
            Date validTo = new Date(System.currentTimeMillis() + 365L * 24 * 60 * 60 * 1000);

            // Create certificate builder
            JcaX509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                    issuerName,
                    serialNumber,
                    validFrom,
                    validTo,
                    subjectName,
                    serverKeyPair.getPublic()
            );

            // Add Subject Key Identifier extension
            JcaX509ExtensionUtils extUtils = new JcaX509ExtensionUtils();
            org.bouncycastle.asn1.x509.SubjectKeyIdentifier ski = extUtils.createSubjectKeyIdentifier(serverKeyPair.getPublic());
            certBuilder.addExtension(org.bouncycastle.asn1.x509.Extension.subjectKeyIdentifier, false, ski);

            // Add Authority Key Identifier extension (from root cert)
            org.bouncycastle.asn1.x509.AuthorityKeyIdentifier aki = extUtils.createAuthorityKeyIdentifier(rootX509Cert);
            certBuilder.addExtension(org.bouncycastle.asn1.x509.Extension.authorityKeyIdentifier, false, aki);

            // Add Key Usage extension (server certificate usage)
            org.bouncycastle.asn1.x509.KeyUsage keyUsage = new org.bouncycastle.asn1.x509.KeyUsage(
                    org.bouncycastle.asn1.x509.KeyUsage.digitalSignature |
                    org.bouncycastle.asn1.x509.KeyUsage.keyEncipherment |
                    org.bouncycastle.asn1.x509.KeyUsage.dataEncipherment);
            certBuilder.addExtension(org.bouncycastle.asn1.x509.Extension.keyUsage, true, keyUsage);

            // Add Extended Key Usage for server authentication
            org.bouncycastle.asn1.x509.ExtendedKeyUsage extendedKeyUsage = new org.bouncycastle.asn1.x509.ExtendedKeyUsage(
                    org.bouncycastle.asn1.x509.KeyPurposeId.id_kp_serverAuth);
            certBuilder.addExtension(org.bouncycastle.asn1.x509.Extension.extendedKeyUsage, false, extendedKeyUsage);

            // Add Basic Constraints (not a CA)
            org.bouncycastle.asn1.x509.BasicConstraints basicConstraints = new org.bouncycastle.asn1.x509.BasicConstraints(false);
            certBuilder.addExtension(org.bouncycastle.asn1.x509.Extension.basicConstraints, true, basicConstraints);

            // Sign the certificate with Root's private key
            java.security.PrivateKey rootPrivateKey = keystoreService.getPrivateKey("CA_" + rootCert.getSerialNumber(), rootCert.getSerialNumber());
            ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSAEncryption")
                    .build(rootPrivateKey);
            
            X509CertificateHolder certHolder = certBuilder.build(signer);
            X509Certificate httpsX509Cert = new JcaX509CertificateConverter().getCertificate(certHolder);

            // Save certificate to our model
            Certificate httpsCertificate = new Certificate();
            httpsCertificate.setSerialNumber("HTTPS_SERVER");
            httpsCertificate.setSubject(subjectName.toString());
            httpsCertificate.setIssuer(issuerName.toString());
            httpsCertificate.setValidFrom(validFrom);
            httpsCertificate.setValidTo(validTo);
            httpsCertificate.setType(CertificateType.END_ENTITY);
            httpsCertificate.setPublicKey(java.util.Base64.getEncoder().encodeToString(serverKeyPair.getPublic().getEncoded()));
            httpsCertificate.setIsCA(false);
            httpsCertificate.setStatus(CertificateStatus.VALID);
            httpsCertificate.setIssuerCertificate(rootCert);
            httpsCertificate.setBasicConstraints("CA:FALSE");
            httpsCertificate.setKeyUsage("digitalSignature, keyEncipherment, dataEncipherment");

            // Save to database
            Certificate savedCert = certificateService.saveCertificate(httpsCertificate);

            // Store in keystore as a KeyEntry with private key
            keystoreService.savePrivateKey("HTTPS_SERVER", serverKeyPair.getPrivate(), httpsX509Cert, "HTTPS_SERVER");

            // Export to PKCS12 for Spring Boot
            exportToPkcs12(serverKeyPair.getPrivate(), httpsX509Cert, rootX509Cert);

            logger.info("HTTPS certificate generated and saved successfully");
        } catch (Exception e) {
            logger.error("Error generating HTTPS certificate: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate HTTPS certificate", e);
        }
    }

    private void exportToPkcs12(java.security.PrivateKey privateKey, X509Certificate serverCert, X509Certificate rootCert) throws Exception {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, null);

        // Create certificate chain: server certificate only
        // The root certificate doesn't need to be in the chain for HTTPS
        X509Certificate[] chain = {serverCert};
        keyStore.setKeyEntry("server", privateKey, "changeit".toCharArray(), chain);

        // Optionally, add the root certificate as a trusted certificate (not in the chain)
        // This can help with trust in some scenarios
        try {
            keyStore.setCertificateEntry("root", rootCert);
        } catch (Exception e) {
            logger.warn("Could not add root certificate to keystore: {}", e.getMessage());
        }

        // Save to PKCS12 file for Spring Boot
        try (FileOutputStream fos = new FileOutputStream("server.p12")) {
            keyStore.store(fos, "changeit".toCharArray());
        }

        logger.info("HTTPS certificate exported to server.p12");
    }


    /**
     * Returns the private key alias for the HTTPS certificate
     */
    public String getHttpsKeyAlias() {
        return "HTTPS_SERVER";
    }

    /**
     * Returns the keystore password for the HTTPS certificate
     */
    public String getHttpsKeyPassword() {
        return "changeit";
    }
}


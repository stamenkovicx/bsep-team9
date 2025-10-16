package com.bsep.pki_system.service;

import com.bsep.pki_system.dto.CreateCertificateDTO;
import com.bsep.pki_system.model.Certificate;
import com.bsep.pki_system.model.CertificateType;
import com.bsep.pki_system.model.User;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x509.*;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.springframework.stereotype.Service;
import org.bouncycastle.asn1.x509.AuthorityKeyIdentifier;
import org.bouncycastle.asn1.x509.SubjectKeyIdentifier;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;

import java.math.BigInteger;
import java.security.*;
import java.security.cert.X509Certificate;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Service
public class CertificateGeneratorService {

    private final KeystoreService keystoreService;

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    public CertificateGeneratorService(KeystoreService keystoreService) {
        this.keystoreService = keystoreService;
    }

    public KeystoreService getKeystoreService() {
        return keystoreService;
    }
    public Certificate generateRootCertificate(CreateCertificateDTO request, User owner) throws Exception {
        // 1. Generisanje para ključeva
        KeyPair keyPair = generateKeyPair();

        // 2. Kreiranje X500Name za subject i issuer (isti za root)
        X500Name subject = createX500Name(request);
        X500Name issuer = subject; // Root je samopotpisani

        // 3. Generisanje serijskog broja
        BigInteger serialNumber = generateSerialNumber();

        // 4. Kreiranje builder-a za sertifikat
        X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                issuer,
                serialNumber,
                request.getValidFrom(),
                request.getValidTo(),
                subject,
                keyPair.getPublic()
        );

        // Kreiranje SKI i AKI ekstenzija - za root je uvek isti ski i aki
        JcaX509ExtensionUtils extensionUtils = new JcaX509ExtensionUtils();
        SubjectKeyIdentifier ski = extensionUtils.createSubjectKeyIdentifier(keyPair.getPublic());
        AuthorityKeyIdentifier aki = extensionUtils.createAuthorityKeyIdentifier(keyPair.getPublic());

        // 5. Dodavanje ekstenzija
        addBasicConstraints(certBuilder, true, -1); // CA:TRUE, bez pathlen ograničenja
        addKeyUsage(certBuilder);
        certBuilder.addExtension(Extension.subjectKeyIdentifier, false, ski);
        certBuilder.addExtension(Extension.authorityKeyIdentifier, false, aki);

        // 6. Potpisivanje sertifikata
        ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSAEncryption").build(keyPair.getPrivate());
        X509CertificateHolder certHolder = certBuilder.build(signer);
        X509Certificate x509Cert = new JcaX509CertificateConverter().getCertificate(certHolder);

        // 7. Čuvanje privatnog ključa u keystore
        //String alias = "ROOT_" + serialNumber.toString();
        String alias = "CA_" + serialNumber.toString(); // isti prefiks za sve CA sertifikate (i root i intermediate)
        keystoreService.savePrivateKey(alias, keyPair.getPrivate(), x509Cert);

        // 8. Čuvanje u našem Certificate modelu
        Certificate certificate = new Certificate();
        certificate.setSerialNumber(serialNumber.toString());
        certificate.setSubject(subject.toString());
        certificate.setIssuer(issuer.toString());
        certificate.setValidFrom(request.getValidFrom());
        certificate.setValidTo(request.getValidTo());
        certificate.setType(CertificateType.ROOT);
        certificate.setPublicKey(java.util.Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()));
        certificate.setIsCA(true);
        certificate.setBasicConstraints("CA:TRUE");
        certificate.setOwner(owner);
        certificate.setKeyUsage("keyCertSign, cRLSign");

        return certificate;
    }

    private KeyPair generateKeyPair() throws NoSuchAlgorithmException {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        return keyGen.generateKeyPair();
    }

    private X500Name createX500Name(CreateCertificateDTO request) {
        X500NameBuilder builder = new X500NameBuilder();

        if (request.getSubjectCommonName() != null)
            builder.addRDN(BCStyle.CN, request.getSubjectCommonName());
        if (request.getSubjectOrganization() != null)
            builder.addRDN(BCStyle.O, request.getSubjectOrganization());
        if (request.getSubjectOrganizationalUnit() != null)
            builder.addRDN(BCStyle.OU, request.getSubjectOrganizationalUnit());
        if (request.getSubjectCountry() != null)
            builder.addRDN(BCStyle.C, request.getSubjectCountry());
        if (request.getSubjectState() != null)
            builder.addRDN(BCStyle.ST, request.getSubjectState());
        if (request.getSubjectLocality() != null)
            builder.addRDN(BCStyle.L, request.getSubjectLocality());
        if (request.getSubjectEmail() != null)
            builder.addRDN(BCStyle.EmailAddress, request.getSubjectEmail());

        return builder.build();
    }

    private BigInteger generateSerialNumber() {
        //return BigInteger.valueOf(System.currentTimeMillis());
        return new BigInteger(128, new SecureRandom());
    }

    private void addBasicConstraints(X509v3CertificateBuilder builder, boolean isCA, int pathLen) throws Exception {
        BasicConstraints basicConstraints = new BasicConstraints(isCA);
        builder.addExtension(Extension.basicConstraints, true, basicConstraints);
    }

    private void addKeyUsage(X509v3CertificateBuilder builder) throws Exception {
        KeyUsage ku = new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign);
        builder.addExtension(Extension.keyUsage, true, ku);
    }

    public Certificate generateIntermediateCertificate(CreateCertificateDTO request, User owner, Certificate issuerCertificate) throws Exception {
        // 1. Generisanje para ključeva za novi, intermediate sertifikat
        KeyPair keyPair = generateKeyPair();

        // 2. Kreiranje X500Name za subject
        X500Name subject = createX500Name(request);

        // 3. Uzimanje podataka o izdavaocu (issueru)
        PrivateKey issuerPrivateKey = keystoreService.getPrivateKey("CA_" + issuerCertificate.getSerialNumber());
        X500Name issuer = new X500Name(issuerCertificate.getSubject());

        // Priprema za SKI i AKI
        JcaX509ExtensionUtils extensionUtils = new JcaX509ExtensionUtils();

        // Kreiraj SKI od javnog ključa NOVOG sertifikata
        SubjectKeyIdentifier ski = extensionUtils.createSubjectKeyIdentifier(keyPair.getPublic());

        // Kreiraj AKI od javnog ključa IZDAVAOCA (issuer-a)
        byte[] issuerPublicKeyBytes = java.util.Base64.getDecoder().decode(issuerCertificate.getPublicKey());
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        X509EncodedKeySpec issuerPublicKeySpec = new X509EncodedKeySpec(issuerPublicKeyBytes);
        PublicKey issuerPublicKey = keyFactory.generatePublic(issuerPublicKeySpec);
        AuthorityKeyIdentifier aki = extensionUtils.createAuthorityKeyIdentifier(issuerPublicKey);
        // ====================================================================

        // 4. Generisanje serijskog broja
        BigInteger serialNumber = generateSerialNumber();

        // 5. Kreiranje builder-a za sertifikat
        X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                issuer,
                serialNumber,
                request.getValidFrom(),
                request.getValidTo(),
                subject,
                keyPair.getPublic()
        );

        // 6. Dodavanje ekstenzija
        addBasicConstraints(certBuilder, true, -1);
        addKeyUsage(certBuilder);

        // DODATO: Postavljanje SKI i AKI ekstenzija
        certBuilder.addExtension(Extension.subjectKeyIdentifier, false, ski);
        certBuilder.addExtension(Extension.authorityKeyIdentifier, false, aki);

        // 7. POTPISIVANJE SERTIFIKATA
        ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSAEncryption").build(issuerPrivateKey);
        X509CertificateHolder certHolder = certBuilder.build(signer);
        X509Certificate x509Cert = new JcaX509CertificateConverter().getCertificate(certHolder);

        // 8. Čuvanje privatnog ključa sa lancem u keystore
        String alias = "CA_" + serialNumber.toString();
        java.security.cert.Certificate[] chain = buildCertificateChain(issuerCertificate, x509Cert);
        keystoreService.savePrivateKeyWithChain(alias, keyPair.getPrivate(), chain);

        // 9. Kreiranje našeg modela za čuvanje u bazi
        Certificate certificate = new Certificate();
        certificate.setSerialNumber(serialNumber.toString());
        certificate.setSubject(subject.toString());
        certificate.setIssuer(issuer.toString());
        certificate.setValidFrom(request.getValidFrom());
        certificate.setValidTo(request.getValidTo());
        certificate.setType(CertificateType.INTERMEDIATE);
        certificate.setPublicKey(java.util.Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()));
        certificate.setIsCA(true);
        certificate.setBasicConstraints("CA:TRUE");
        certificate.setKeyUsage("keyCertSign, cRLSign");
        certificate.setOwner(owner);
        certificate.setIssuerCertificate(issuerCertificate);

        return certificate;
    }

    private java.security.cert.Certificate[] buildCertificateChain(Certificate issuerCert, X509Certificate newCert) throws Exception {
        List<java.security.cert.Certificate> chain = new ArrayList<>();
        chain.add(newCert); // Novi sertifikat ide prvi

        // Rekurzivno dodaj sve roditelje do root-a
        Certificate current = issuerCert;
        while (current != null) {
            X509Certificate x509 = (X509Certificate) keystoreService.getCertificate("CA_" + current.getSerialNumber());            chain.add(x509);
            current = current.getIssuerCertificate(); // Idemo gore po lancu
        }

        return chain.toArray(new java.security.cert.Certificate[0]);
    }

}
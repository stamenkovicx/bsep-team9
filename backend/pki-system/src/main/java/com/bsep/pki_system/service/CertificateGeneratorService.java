package com.bsep.pki_system.service;

import com.bsep.pki_system.dto.CreateCertificateDTO;
import com.bsep.pki_system.model.Certificate;
import com.bsep.pki_system.model.CertificateType;
import com.bsep.pki_system.model.User;
import com.bsep.pki_system.service.CertificateService;
import org.apache.commons.codec.DecoderException;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
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
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequest;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemReader;
import org.springframework.stereotype.Service;
import org.bouncycastle.asn1.x509.AuthorityKeyIdentifier;
import org.bouncycastle.asn1.x509.SubjectKeyIdentifier;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.StringReader;
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
    private final CertificateService certificateService;

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    public CertificateGeneratorService(KeystoreService keystoreService,
                                       CertificateService certificateService) {
        this.keystoreService = keystoreService;
        this.certificateService = certificateService;
    }

    public KeystoreService getKeystoreService() {
        return keystoreService;
    }

    @Transactional(rollbackFor = Exception.class)
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
        //omogući izdavanje proizvoljnog broja intermediate sertifikata u jednom lancu -1
        addBasicConstraints(certBuilder, true, -1); // CA:TRUE, bez pathlen ograničenja
        addKeyUsage(certBuilder);
        certBuilder.addExtension(Extension.subjectKeyIdentifier, false, ski);
        certBuilder.addExtension(Extension.authorityKeyIdentifier, false, aki);

        // 6. Potpisivanje sertifikata
        ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSAEncryption").build(keyPair.getPrivate());
        X509CertificateHolder certHolder = certBuilder.build(signer);
        X509Certificate x509Cert = new JcaX509CertificateConverter().getCertificate(certHolder);

        // 7. Čuvanje u našem Certificate modelu
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

        // 7a. PRVO SAČUVAJ CERTIFICATE U BAZU
        Certificate savedCertificate = certificateService.saveCertificate(certificate);

        // 8. Čuvanje privatnog ključa u keystore
        String alias = "CA_" + serialNumber.toString(); // isti prefiks za sve CA sertifikate (i root i intermediate)
        keystoreService.savePrivateKey(alias, keyPair.getPrivate(), x509Cert, serialNumber.toString());


        return savedCertificate;
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

    @Transactional(rollbackFor = Exception.class)
    public Certificate generateIntermediateCertificate(CreateCertificateDTO request, User owner, Certificate issuerCertificate) throws Exception {
        // 1. Generisanje para ključeva za novi, intermediate sertifikat
        KeyPair keyPair = generateKeyPair();

        // 2. Kreiranje X500Name za subject
        X500Name subject = createX500Name(request);

        // 3. Uzimanje podataka o izdavaocu (issueru)
        PrivateKey issuerPrivateKey = keystoreService.getPrivateKey("CA_" + issuerCertificate.getSerialNumber(), issuerCertificate.getSerialNumber());
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
        addBasicConstraints(certBuilder, true, -1);//omogucen prozivoljan lanac sertifikata
        addKeyUsage(certBuilder);

        // DODATO: Postavljanje SKI i AKI ekstenzija
        certBuilder.addExtension(Extension.subjectKeyIdentifier, false, ski);
        certBuilder.addExtension(Extension.authorityKeyIdentifier, false, aki);
        //CRL Distribution Point
        addCRLDistributionPoint(certBuilder, issuerCertificate.getSerialNumber());

        // 7. POTPISIVANJE SERTIFIKATA
        ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSAEncryption").build(issuerPrivateKey);
        X509CertificateHolder certHolder = certBuilder.build(signer);
        X509Certificate x509Cert = new JcaX509CertificateConverter().getCertificate(certHolder);

        // 8. Kreiranje našeg modela za čuvanje u bazi
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

        // 8a. PRVO SAČUVAJ CERTIFICATE U BAZU
        Certificate savedCertificate = certificateService.saveCertificate(certificate);

        // 9. Čuvanje privatnog ključa sa lancem u keystore
        String alias = "CA_" + serialNumber.toString();
        java.security.cert.Certificate[] chain = buildCertificateChain(issuerCertificate, x509Cert);
        keystoreService.savePrivateKeyWithChain(alias, keyPair.getPrivate(), chain, serialNumber.toString());

        return savedCertificate;
    }
    private void addCRLDistributionPoint(X509v3CertificateBuilder builder, String issuerSerialNumber) throws Exception {
        // URL gde će biti dostupna CRL lista
        String crlUrl = "http://localhost:8080/api/crl/" + issuerSerialNumber + ".crl";

        GeneralName generalName = new GeneralName(GeneralName.uniformResourceIdentifier, crlUrl);
        GeneralNames generalNames = new GeneralNames(generalName);
        DistributionPointName dpn = new DistributionPointName(generalNames);
        DistributionPoint dp = new DistributionPoint(dpn, null, null);

        builder.addExtension(
                Extension.cRLDistributionPoints,
                false,
                new CRLDistPoint(new DistributionPoint[]{dp})
        );
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

    @Transactional(rollbackFor = Exception.class)
    public Certificate generateEECertificateFromCsr(String csrPem, Date validFrom, Date validTo,
                                                    Certificate issuerCertificate, User owner) throws Exception {

        // 1. Parsiranje CSR-a
        PKCS10CertificationRequest csr = parseCsr(csrPem);

        // 2. Izdavalac (Issuer) i privatni ključ izdavaoca
        PrivateKey issuerPrivateKey = keystoreService.getPrivateKey("CA_" + issuerCertificate.getSerialNumber(), issuerCertificate.getSerialNumber());
        X500Name issuer = new X500Name(issuerCertificate.getSubject());
        PublicKey subjectPublicKey = new JcaPKCS10CertificationRequest(csr).getPublicKey();
        X500Name subject = csr.getSubject();

        // 3. Generisanje serijskog broja
        BigInteger serialNumber = generateSerialNumber();

        // 4. Kreiranje builder-a
        X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                issuer,
                serialNumber,
                validFrom,
                validTo,
                subject,
                subjectPublicKey
        );

        // 5. DODAVANJE EKSTENZIJA
        JcaX509ExtensionUtils extensionUtils = new JcaX509ExtensionUtils();

        // Subject Key Identifier (SKI) za EE sertifikat
        SubjectKeyIdentifier ski = extensionUtils.createSubjectKeyIdentifier(subjectPublicKey);
        certBuilder.addExtension(Extension.subjectKeyIdentifier, false, ski);

        // Authority Key Identifier (AKI) - javni ključ IZDAVAOCA (issuer-a)
        byte[] issuerPublicKeyBytes = java.util.Base64.getDecoder().decode(issuerCertificate.getPublicKey());
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        X509EncodedKeySpec issuerPublicKeySpec = new X509EncodedKeySpec(issuerPublicKeyBytes);
        PublicKey issuerPublicKey = keyFactory.generatePublic(issuerPublicKeySpec);
        AuthorityKeyIdentifier aki = extensionUtils.createAuthorityKeyIdentifier(issuerPublicKey);
        certBuilder.addExtension(Extension.authorityKeyIdentifier, false, aki);

        // Basic Constraints: CA:FALSE (EE sertifikat)
        certBuilder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
        org.bouncycastle.asn1.pkcs.Attribute[] pkcsAttributes = csr.getAttributes();

        // Key Usage i SANs
        addKeyUsageFromCsrAttributes(certBuilder, pkcsAttributes);
        addSansFromCsrAttributes(certBuilder, pkcsAttributes);

        // CRL Distribution Point
        addCRLDistributionPoint(certBuilder, issuerCertificate.getSerialNumber());


        // 6. Potpisivanje sertifikata
        ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSAEncryption").build(issuerPrivateKey);
        X509CertificateHolder certHolder = certBuilder.build(signer);
        X509Certificate x509Cert = new JcaX509CertificateConverter().getCertificate(certHolder);

        // 7. Kreiranje modela za bazu (Bez PEM podataka)
        Certificate certificate = new Certificate();
        certificate.setSerialNumber(serialNumber.toString());
        certificate.setSubject(subject.toString());
        certificate.setIssuer(issuer.toString());
        certificate.setValidFrom(validFrom);
        certificate.setValidTo(validTo);
        certificate.setType(CertificateType.END_ENTITY);
        certificate.setPublicKey(java.util.Base64.getEncoder().encodeToString(subjectPublicKey.getEncoded()));
        certificate.setIsCA(false);
        certificate.setBasicConstraints("CA:FALSE");
        certificate.setOwner(owner);
        certificate.setIssuerCertificate(issuerCertificate);
        certificate.setKeyUsage("digitalSignature, keyEncipherment"); // Default EE KeyUsage

        // 8. Čuvanje EE sertifikata u keystore-u kao TrustedCertificateEntry
        String alias = "EE_" + serialNumber.toString();
        keystoreService.saveTrustedCertificate(alias, x509Cert);

        // 9. Čuvanje modela sertifikata u bazi SA PEM sadržajem.
        // OVA METODA ĆE DODATI PEM PODATKE U 'certificate' OBJEKAT I SAČUVATI GA.
        Certificate savedCertificate = certificateService.saveEndEntityCertificate(certificate, x509Cert);

        return savedCertificate;
    }

    // U CertificateGeneratorService.java, unutar generateEECertificateFromCsr ili parseCsr metode

    // U CertificateGeneratorService.java

    public PKCS10CertificationRequest parseCsr(String csrPem) throws Exception, DecoderException {

        if (csrPem == null || csrPem.trim().isEmpty()) {
            throw new IllegalArgumentException("CSR content cannot be empty.");
        }

        // 1. Agresivno čišćenje stringa
        csrPem = csrPem.trim();
        // Normalizacija novih linija, što pomaže PemReader-u da pravilno segmentira Base64 blok
        csrPem = csrPem.replaceAll("\r\n", "\n").replaceAll("\r", "\n");


        try (PemReader pemReader = new PemReader(new StringReader(csrPem))) {
            PemObject pemObject = pemReader.readPemObject();

            if (pemObject == null) {
                // Ako PemReader ne pronađe BEGIN/END, to je problem formata
                throw new IllegalArgumentException("Invalid PEM format: Could not read PEM object. Is the CSR wrapped with BEGIN/END tags?");
            }

            String type = pemObject.getType();
            if (!"CERTIFICATE REQUEST".equals(type) && !"NEW CERTIFICATE REQUEST".equals(type)) {
                throw new IllegalArgumentException("Invalid PEM type: Expected 'CERTIFICATE REQUEST' but found '" + type + "'.");
            }

            // 2. Kreiranje CSR objekta iz dekodiranog DER sadržaja
            byte[] content = pemObject.getContent();

            // Pokušaj parsiranja DER bajtova. Ovdje se dešava greška 'corrupted stream'.
            return new PKCS10CertificationRequest(content);

        } catch (IOException e) {
            // Rukovodi IOException, što je roditelj za 'corrupted stream' grešku Bouncy Castle-a

            // Opcionalno: Izdvajanje specifične poruke za bolje logovanje
            String errorMessage = e.getMessage() != null ? e.getMessage() : "Unknown I/O Error";
            System.err.println("--- ASN.1 PARSING ERROR ---");
            System.err.println("Bouncy Castle failed to parse the DER content (ASN.1 structure).");
            System.err.println("Caused by: " + errorMessage);
            System.err.println("-------------------------");

            // Baca se jasna poruka koju će frontend prikazati
            throw new Exception("Error creating End-Entity certificate: Failed to parse the CSR's internal structure. Please ensure the CSR is correctly generated and complete.", e);
        }
    }


    private void addKeyUsageFromCsrAttributes(X509v3CertificateBuilder builder, org.bouncycastle.asn1.pkcs.Attribute[] attributes) throws Exception {

        KeyUsage requestedKeyUsage = null;

        // 1. Prođi kroz sve atribute poslate u CSR-u
        for (org.bouncycastle.asn1.pkcs.Attribute attr : attributes) {
            // Traži OID za 'ExtensionRequest' (gde se čuvaju ekstenzije u CSR-u)
            if (attr.getAttrType().equals(PKCSObjectIdentifiers.pkcs_9_at_extensionRequest)) {
                Extensions extensions = Extensions.getInstance(attr.getAttrValues().getObjectAt(0));

                // 2. Unutar ekstenzija, traži 'Key Usage'
                org.bouncycastle.asn1.x509.Extension kuExt = extensions.getExtension(Extension.keyUsage);

                if (kuExt != null) {
                    // Ako je Key Usage pronađen, parsiraj ga
                    requestedKeyUsage = KeyUsage.getInstance(kuExt.getParsedValue());
                    break; // Pronašli smo, možemo da prekinemo petlju
                }
            }
        }

        if (requestedKeyUsage != null) {
            // 3. Koristi zatraženi Key Usage
            builder.addExtension(Extension.keyUsage, true, requestedKeyUsage);
        } else {
            // 4. Ako Key Usage nije zatražen u CSR-u, koristi podrazumevane EE vrednosti
            KeyUsage defaultKu = new KeyUsage(KeyUsage.digitalSignature | KeyUsage.keyEncipherment);
            builder.addExtension(Extension.keyUsage, true, defaultKu);
        }
    }

    private void addSansFromCsrAttributes(X509v3CertificateBuilder builder, org.bouncycastle.asn1.pkcs.Attribute[] attributes) throws Exception {
        // Logika za pronalaženje Subject Alternative Name (SAN) u CSR atributima
        // Standardno se nalazi pod OID-om ExtensionRequest (1.2.840.113549.1.9.14)
        for (org.bouncycastle.asn1.pkcs.Attribute attr : attributes) {
            if (attr.getAttrType().equals(PKCSObjectIdentifiers.pkcs_9_at_extensionRequest)) {
                Extensions extensions = Extensions.getInstance(attr.getAttrValues().getObjectAt(0));
                org.bouncycastle.asn1.x509.Extension sanExt = extensions.getExtension(Extension.subjectAlternativeName);
                if (sanExt != null) {
                    builder.addExtension(Extension.subjectAlternativeName, sanExt.isCritical(), sanExt.getParsedValue());
                }
            }
        }
    }
}
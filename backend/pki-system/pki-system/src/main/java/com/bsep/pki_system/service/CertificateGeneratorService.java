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

import java.math.BigInteger;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.Date;

@Service
public class CertificateGeneratorService {

    private final KeystoreService keystoreService;

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    public CertificateGeneratorService(KeystoreService keystoreService) {
        this.keystoreService = keystoreService;
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

        // 5. Dodavanje ekstenzija
        addBasicConstraints(certBuilder, true, -1); // CA:TRUE, bez pathlen ograničenja
        addKeyUsage(certBuilder);

        // 6. Potpisivanje sertifikata
        ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSAEncryption").build(keyPair.getPrivate());
        X509CertificateHolder certHolder = certBuilder.build(signer);
        X509Certificate x509Cert = new JcaX509CertificateConverter().getCertificate(certHolder);

        // 7. Čuvanje privatnog ključa u keystore
        String alias = "ROOT_" + serialNumber.toString();
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
        return BigInteger.valueOf(System.currentTimeMillis());
    }

    private void addBasicConstraints(X509v3CertificateBuilder builder, boolean isCA, int pathLen) throws Exception {
        BasicConstraints basicConstraints = new BasicConstraints(isCA);
        builder.addExtension(Extension.basicConstraints, true, basicConstraints);
    }

    private void addKeyUsage(X509v3CertificateBuilder builder) throws Exception {
        KeyUsage ku = new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign);
        builder.addExtension(Extension.keyUsage, true, ku);
    }
}
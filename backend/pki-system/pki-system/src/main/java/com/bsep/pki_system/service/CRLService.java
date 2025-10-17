package com.bsep.pki_system.service;

import com.bsep.pki_system.model.Certificate;
import com.bsep.pki_system.model.CertificateStatus;
import com.bsep.pki_system.repository.CertificateRepository;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.CRLReason;
import org.bouncycastle.cert.X509CRLHolder;
import org.bouncycastle.cert.X509v2CRLBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.security.PrivateKey;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

@Service
public class CRLService {

    private final CertificateRepository certificateRepository;
    private final KeystoreService keystoreService;

    public CRLService(CertificateRepository certificateRepository, KeystoreService keystoreService) {
        this.certificateRepository = certificateRepository;
        this.keystoreService = keystoreService;
    }

    //Generiše CRL listu za dati CA sertifikat
    public byte[] generateCRL(Certificate caCertificate) throws Exception {
        // 1. Pronađi sve povučene sertifikate koje je izdao ovaj CA
        List<Certificate> revokedCerts = certificateRepository.findByIssuerCertificateId(caCertificate.getId())
                .stream()
                .filter(cert -> cert.getStatus() == CertificateStatus.REVOKED)
                .toList();

        // 2. Učitaj privatni ključ CA-a
        String alias = "CA_" + caCertificate.getSerialNumber();
        PrivateKey caPrivateKey = keystoreService.getPrivateKey(alias, caCertificate.getSerialNumber());

        // 3. Kreiraj CRL builder
        X500Name issuer = new X500Name(caCertificate.getSubject());
        Date now = new Date();
        Date nextUpdate = Date.from(LocalDateTime.now().plusDays(7).atZone(ZoneId.systemDefault()).toInstant());

        BigInteger crlNumber = BigInteger.valueOf(
                caCertificate.getCrlNumber() != null ? caCertificate.getCrlNumber() + 1 : 1
        );

        X509v2CRLBuilder crlBuilder = new X509v2CRLBuilder(issuer, now);
        crlBuilder.setNextUpdate(nextUpdate);

        // 4. Dodaj sve povučene sertifikate
        for (Certificate revokedCert : revokedCerts) {
            BigInteger serialNumber = new BigInteger(revokedCert.getSerialNumber());
            Date revocationDate = Date.from(
                    revokedCert.getRevokedAt().atZone(ZoneId.systemDefault()).toInstant()
            );

            // Mapiranje razloga iz stringa u CRLReason
            int reasonCode = mapRevocationReason(revokedCert.getRevocationReason());

            crlBuilder.addCRLEntry(serialNumber, revocationDate, reasonCode);
        }

        // 5. Potpiši CRL
        ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSAEncryption").build(caPrivateKey);
        X509CRLHolder crlHolder = crlBuilder.build(signer);

        // 6. Ažuriraj CRL broj u bazi
        caCertificate.setCrlNumber(crlNumber.longValue());
        caCertificate.setLastCRLUpdate(LocalDateTime.now());
        certificateRepository.save(caCertificate);

        return crlHolder.getEncoded();
    }

    private int mapRevocationReason(String reason) {
        if (reason == null) return CRLReason.unspecified;

        return switch (reason.toLowerCase()) {
            case "keycompromise" -> CRLReason.keyCompromise;
            case "cacompromise" -> CRLReason.cACompromise;
            case "affiliationchanged" -> CRLReason.affiliationChanged;
            case "superseded" -> CRLReason.superseded;
            case "cessationofoperation" -> CRLReason.cessationOfOperation;
            case "certificatehold" -> CRLReason.certificateHold;
            case "removefromcrl" -> CRLReason.removeFromCRL;
            case "privilegewithdrawn" -> CRLReason.privilegeWithdrawn;
            case "aacompromise" -> CRLReason.aACompromise;
            default -> CRLReason.unspecified;
        };
    }
}
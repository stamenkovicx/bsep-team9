package com.bsep.pki_system.service;

import com.bsep.pki_system.controller.AuthController;
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
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509CRL;
import java.security.spec.X509EncodedKeySpec;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@Service
public class CRLService {

    private final CertificateRepository certificateRepository;
    private final KeystoreService keystoreService;
    private final Map<String, byte[]> crlCache = new ConcurrentHashMap<>();

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    public CRLService(CertificateRepository certificateRepository, KeystoreService keystoreService) {
        this.certificateRepository = certificateRepository;
        this.keystoreService = keystoreService;
    }

    //Generiše CRL listu za dati CA sertifikat

    @Transactional
    protected byte[] generateCRL(Certificate caCertificate) throws Exception {
        // 1. Pronađi sve povučene sertifikate koje je izdao ovaj CA
        List<Certificate> revokedCerts = certificateRepository.findByIssuerCertificateId(caCertificate.getId())
                .stream()
                .filter(cert -> cert.getStatus() == CertificateStatus.REVOKED)
                .toList();

        // 2. Učitaj privatni ključ CA-a
        String alias = "CA_" + caCertificate.getSerialNumber();
        PrivateKey caPrivateKey = keystoreService.getPrivateKey(alias, caCertificate.getSerialNumber());

        if (caPrivateKey == null) {
            throw new RuntimeException("Private key not found for CA: " + caCertificate.getSerialNumber());
        }

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
            
            // Koristi revoked date ili current date ako null
            Date revocationDate;
            if (revokedCert.getRevokedAt() != null) {
                revocationDate = Date.from(
                        revokedCert.getRevokedAt().atZone(ZoneId.systemDefault()).toInstant()
                );
            } else {
                // Ako revokedAt nije set-ovan, koristi trenutni datum
                revocationDate = new Date();
            }

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
    @Transactional(readOnly = true)
    public boolean isCertificateRevoked(Certificate certificateToCheck, Certificate issuer) {
        try {
            // 1. Generiši (ili dohvati) najnoviju CRL listu za izdavaoca
            byte[] crlBytes = getOrGenerateCRL(issuer);

            // 2. Parsiraj CRL bajtove u X509CRL objekat
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            X509CRL crl = (X509CRL) cf.generateCRL(new ByteArrayInputStream(crlBytes));

            // 3. Verifikuj potpis CRL liste (da li ju je zaista potpisao izdavalac)
            byte[] issuerPublicKeyBytes = java.util.Base64.getDecoder().decode(issuer.getPublicKey());
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            PublicKey issuerPublicKey = keyFactory.generatePublic(new X509EncodedKeySpec(issuerPublicKeyBytes));

            crl.verify(issuerPublicKey); // Baciće izuzetak ako potpis nije validan

            // 4. proveri da li je serijski broj sertifikata na listi
            BigInteger serialToCheck = new BigInteger(certificateToCheck.getSerialNumber());

            // crl.getRevokedCertificate() vraća non-null ako je serijski broj na listi
            return crl.getRevokedCertificate(serialToCheck) != null;

        } catch (Exception e) {
            logger.error("Error message", e);
            return true;
        }
    }
     //proverava kes pre generisanja
    public byte[] getOrGenerateCRL(Certificate caCertificate) throws Exception {
        String issuerSerial = caCertificate.getSerialNumber();

        //Da li CRL uopste postoji u kesu?
        if (!crlCache.containsKey(issuerSerial)) {
            // Ne postoji, generisi ga, stavi u kes i vrati
            byte[] newCrl = generateCRL(caCertificate);
            crlCache.put(issuerSerial, newCrl);
            return newCrl;
        }
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        X509CRL crl = (X509CRL) cf.generateCRL(new ByteArrayInputStream(crlCache.get(issuerSerial)));

        if (crl.getNextUpdate().before(new Date())) {
            // Kesirana lista je istekla. Generisi novu.
            byte[] newCrl = generateCRL(caCertificate);
            crlCache.put(issuerSerial, newCrl);
            return newCrl;
        }
        // Kesirana lista je validna i sveza. Vracam je.
        return crlCache.get(issuerSerial);
    }
    public void clearCache(String issuerSerialNumber) {
        if (issuerSerialNumber != null) {
            crlCache.remove(issuerSerialNumber);
        }
    }
}
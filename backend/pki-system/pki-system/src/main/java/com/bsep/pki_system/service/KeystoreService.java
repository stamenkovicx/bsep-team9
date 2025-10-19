package com.bsep.pki_system.service;

import com.bsep.pki_system.dto.EncryptionResultDTO;
import com.bsep.pki_system.model.KeystorePassword;
import com.bsep.pki_system.repository.KeystorePasswordRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import com.bsep.pki_system.service.CertificateService;

import java.io.*;
import java.security.*;
import java.security.cert.Certificate;
import java.util.Base64;

@Service
public class KeystoreService {
    private final KeystorePasswordRepository keystorePasswordRepository;
    private final PasswordEncryptionService encryptionService;
    private final CertificateService certificateService;

    // Master ključ će se čuvati u konfiguraciji
    @Value("${keystore.master.key:default-master-key-change-in-production}")
    private String masterKey;

    //private static final String KEYSTORE_PASSWORD = "keystore-pass-123"; // Privremeno, kasnije enkriptovati
    private static final String KEYSTORE_PATH = "keystore.p12";
    private static final String KEYSTORE_TYPE = "PKCS12";

    public KeystoreService(KeystorePasswordRepository keystorePasswordRepository,
                           PasswordEncryptionService encryptionService,
                           @Lazy CertificateService certificateService) {
        this.keystorePasswordRepository = keystorePasswordRepository;
        this.encryptionService = encryptionService;
        this.certificateService = certificateService;
    }

    public void savePrivateKey(String alias, PrivateKey privateKey, Certificate certificate, String serialNumber) throws Exception {
        // Generisanje nasumične lozinke za ovaj keystore entry
        String randomPassword = encryptionService.generateRandomPassword();

        KeyStore keyStore = loadOrCreateKeystore();

        Certificate[] chain = {certificate};
        keyStore.setKeyEntry(alias, privateKey, randomPassword.toCharArray(), chain);

        saveKeystore(keyStore);

        // Čuvanje enkriptovane lozinke u bazi
        saveEncryptedPassword(serialNumber, randomPassword);
    }

    public PrivateKey getPrivateKey(String alias, String serialNumber) throws Exception {
        // Dobijanje lozinke iz baze i dekriptovanje
        String password = getDecryptedPassword(serialNumber);

        KeyStore keyStore = loadKeystore();
        return (PrivateKey) keyStore.getKey(alias, password.toCharArray());
    }

    public Certificate getCertificate(String alias) throws Exception {
        KeyStore keyStore = loadKeystore();
        return keyStore.getCertificate(alias);
    }

    private KeyStore loadOrCreateKeystore() throws Exception {
        File keystoreFile = new File(KEYSTORE_PATH);
        if (keystoreFile.exists()) {
            return loadKeystore();
        } else {
            KeyStore keyStore = KeyStore.getInstance(KEYSTORE_TYPE);
            keyStore.load(null, null);
            return keyStore;
        }
    }

    private KeyStore loadKeystore() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(KEYSTORE_TYPE);
        // Keystore fajl sam po sebi nije zaštićen lozinkom
        try (FileInputStream fis = new FileInputStream(KEYSTORE_PATH)) {
            keyStore.load(fis, null);
        }
        return keyStore;
    }

    private void saveKeystore(KeyStore keyStore) throws Exception {
        try (FileOutputStream fos = new FileOutputStream(KEYSTORE_PATH)) {
            keyStore.store(fos, null); // Keystore fajl nije zaštićen lozinkom
        }
    }

    //čuva privatni ključ sa celim lancem sertifikata (za intermediate)
    public void savePrivateKeyWithChain(String alias, PrivateKey privateKey, Certificate[] chain, String serialNumber) throws Exception {
        String randomPassword = encryptionService.generateRandomPassword();

        KeyStore keyStore = loadOrCreateKeystore();
        keyStore.setKeyEntry(alias, privateKey, randomPassword.toCharArray(), chain);
        saveKeystore(keyStore);

        // Čuvanje enkriptovane lozinke u bazi
        saveEncryptedPassword(serialNumber, randomPassword);
    }
    //vraća ceo lanac (opciono, ali korisno)
    public Certificate[] getCertificateChain(String alias) throws Exception {
        KeyStore keyStore = loadKeystore();
        return keyStore.getCertificateChain(alias);
    }

    // Nove metode za upravljanje enkriptovanim lozinkama
    private void saveEncryptedPassword(String serialNumber, String password) throws Exception {
        EncryptionResultDTO encryptionResult = encryptionService.encryptPassword(password, masterKey);

        // Pronađi Certificate iz baze po serialNumber-u
        com.bsep.pki_system.model.Certificate certificate = certificateService.findBySerialNumber(serialNumber)
                .orElseThrow(() -> new RuntimeException("Certificate not found with serial number: " + serialNumber));

        KeystorePassword keystorePassword = new KeystorePassword();
        keystorePassword.setCertificate(certificate);
        keystorePassword.setEncryptedPassword(encryptionResult.getEncryptedData());
        keystorePassword.setIv(encryptionResult.getIv());
        keystorePassword.setSalt(encryptionResult.getSalt());
        keystorePassword.setEncryptionAlgorithm(encryptionResult.getAlgorithm());

        keystorePasswordRepository.save(keystorePassword);
    }

    private String getDecryptedPassword(String serialNumber) throws Exception {
        KeystorePassword keystorePassword = keystorePasswordRepository
                .findByCertificateSerialNumber(serialNumber)
                .orElseThrow(() -> new RuntimeException("Keystore password not found for certificate: " + serialNumber));

        return encryptionService.decryptPassword(
                keystorePassword.getEncryptedPassword(),
                keystorePassword.getIv(),
                keystorePassword.getSalt(),
                masterKey
        );
    }

    public void saveTrustedCertificate(String alias, Certificate certificate) throws Exception {
        KeyStore keyStore = loadOrCreateKeystore();

        // Provera da li već postoji entry sa tim aliasom (za svaki slučaj)
        if (keyStore.containsAlias(alias)) {
            keyStore.deleteEntry(alias);
        }

        // setCertificateEntry se koristi za Trusted Certificate (nema privatnog ključa)
        keyStore.setCertificateEntry(alias, certificate);

        saveKeystore(keyStore);
    }

    public byte[] getCertificateBytes(String alias) throws Exception {
        Certificate cert = getCertificate(alias);
        if (cert == null) {
            throw new KeyStoreException("Certificate not found for alias: " + alias);
        }
        return cert.getEncoded();
    }
}
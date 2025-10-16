package com.bsep.pki_system.service;

import org.springframework.stereotype.Service;

import java.io.*;
import java.security.*;
import java.security.cert.Certificate;
import java.util.Base64;

@Service
public class KeystoreService {

    private static final String KEYSTORE_PASSWORD = "keystore-pass-123"; // Privremeno, kasnije enkriptovati
    private static final String KEYSTORE_PATH = "keystore.p12";
    private static final String KEYSTORE_TYPE = "PKCS12";

    public void savePrivateKey(String alias, PrivateKey privateKey, Certificate certificate) throws Exception {
        KeyStore keyStore = loadOrCreateKeystore();

        Certificate[] chain = {certificate};
        keyStore.setKeyEntry(alias, privateKey, KEYSTORE_PASSWORD.toCharArray(), chain);

        saveKeystore(keyStore);
    }

    public PrivateKey getPrivateKey(String alias) throws Exception {
        KeyStore keyStore = loadKeystore();
        return (PrivateKey) keyStore.getKey(alias, KEYSTORE_PASSWORD.toCharArray());
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
        try (FileInputStream fis = new FileInputStream(KEYSTORE_PATH)) {
            keyStore.load(fis, KEYSTORE_PASSWORD.toCharArray());
        }
        return keyStore;
    }

    private void saveKeystore(KeyStore keyStore) throws Exception {
        try (FileOutputStream fos = new FileOutputStream(KEYSTORE_PATH)) {
            keyStore.store(fos, KEYSTORE_PASSWORD.toCharArray());
        }
    }

    //čuva privatni ključ sa celim lancem sertifikata (za intermediate)
    public void savePrivateKeyWithChain(String alias, PrivateKey privateKey, Certificate[] chain) throws Exception {
        KeyStore keyStore = loadOrCreateKeystore();
        keyStore.setKeyEntry(alias, privateKey, KEYSTORE_PASSWORD.toCharArray(), chain);
        saveKeystore(keyStore);
    }
    //vraća ceo lanac (opciono, ali korisno)
    public Certificate[] getCertificateChain(String alias) throws Exception {
        KeyStore keyStore = loadKeystore();
        return keyStore.getCertificateChain(alias);
    }
}
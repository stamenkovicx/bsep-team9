package com.bsep.pki_system.service;

import org.springframework.stereotype.Service;

import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import javax.crypto.Cipher;

@Service
public class EncryptionService {

    private static final String RSA_ALGORITHM = "RSA";
    private static final String RSA_TRANSFORMATION = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";

    // Enkriptuj podatke koristeći javni ključ (PEM format)
    public String encryptWithPublicKey(String data, String publicKeyPem) {
        try {
            // Ukloni PEM header i footer
            String publicKeyContent = publicKeyPem
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s", "");

            // Dekoduj Base64
            byte[] keyBytes = Base64.getDecoder().decode(publicKeyContent);

            // Kreiraj PublicKey objekat
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(keyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance(RSA_ALGORITHM);
            PublicKey publicKey = keyFactory.generatePublic(keySpec);

            // Enkriptuj podatke
            Cipher cipher = Cipher.getInstance(RSA_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);
            byte[] encryptedBytes = cipher.doFinal(data.getBytes());

            return Base64.getEncoder().encodeToString(encryptedBytes);

        } catch (Exception e) {
            throw new RuntimeException("Encryption failed", e);
        }
    }

    // Dekriptuj podatke koristeći privatni ključ (ova metoda će se koristiti na frontendu)
    public String decryptWithPrivateKey(String encryptedData, PrivateKey privateKey) {
        try {
            byte[] encryptedBytes = Base64.getDecoder().decode(encryptedData);

            Cipher cipher = Cipher.getInstance(RSA_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, privateKey);
            byte[] decryptedBytes = cipher.doFinal(encryptedBytes);

            return new String(decryptedBytes);
        } catch (Exception e) {
            throw new RuntimeException("Decryption failed", e);
        }
    }

    // Pomoćna metoda za ekstrakciju javnog ključa iz sertifikata
    public PublicKey getPublicKeyFromCertificate(String certificatePem) {
        try {
            // Ovo je pojednostavljena verzija - u praksi bi koristili CertificateFactory
            String publicKeyContent = certificatePem
                    .replace("-----BEGIN CERTIFICATE-----", "")
                    .replace("-----END CERTIFICATE-----", "")
                    .replaceAll("\\s", "");

            byte[] certBytes = Base64.getDecoder().decode(publicKeyContent);

            // Ovdje bi trebalo parsirati sertifikat i ekstraktovati javni ključ
            // Za sada vraćamo null jer će se javni ključ čuvati u Certificate entitetu
            return null;
        } catch (Exception e) {
            throw new RuntimeException("Failed to extract public key from certificate", e);
        }
    }

    // Generiši par ključeva (korisno za testiranje)
    public KeyPair generateKeyPair() {
        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance(RSA_ALGORITHM);
            keyGen.initialize(2048);
            return keyGen.generateKeyPair();
        } catch (Exception e) {
            throw new RuntimeException("Key pair generation failed", e);
        }
    }
}
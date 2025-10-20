package com.bsep.pki_system.service;

import com.bsep.pki_system.dto.EncryptionResultDTO;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.Base64;

@Service
public class PasswordEncryptionService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int IV_LENGTH = 12;
    private static final int SALT_LENGTH = 16;
    private static final int ITERATIONS = 65536;
    private static final int KEY_LENGTH = 256;

    private final SecureRandom secureRandom = new SecureRandom();

    public EncryptionResultDTO encryptPassword(String password, String masterKey) throws Exception {
        // Generisanje soli i IV
        byte[] salt = generateRandomBytes(SALT_LENGTH);
        byte[] iv = generateRandomBytes(IV_LENGTH);

        // Generisanje AES ključa iz master key pomoću PBKDF2
        SecretKey secretKey = deriveKey(masterKey, salt);

        // Enkripcija
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);

        byte[] encryptedData = cipher.doFinal(password.getBytes());

        return new EncryptionResultDTO(
                Base64.getEncoder().encodeToString(encryptedData),
                Base64.getEncoder().encodeToString(iv),
                Base64.getEncoder().encodeToString(salt),
                ALGORITHM
        );
    }

    public String decryptPassword(String encryptedPassword, String iv, String salt, String masterKey) throws Exception {
        byte[] encryptedBytes = Base64.getDecoder().decode(encryptedPassword);
        byte[] ivBytes = Base64.getDecoder().decode(iv);
        byte[] saltBytes = Base64.getDecoder().decode(salt);

        SecretKey secretKey = deriveKey(masterKey, saltBytes);

        Cipher cipher = Cipher.getInstance(ALGORITHM);
        GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, ivBytes);
        cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);

        byte[] decryptedData = cipher.doFinal(encryptedBytes);
        return new String(decryptedData);
    }

    private SecretKey deriveKey(String masterKey, byte[] salt) throws NoSuchAlgorithmException, InvalidKeySpecException {
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        KeySpec spec = new PBEKeySpec(masterKey.toCharArray(), salt, ITERATIONS, KEY_LENGTH);
        SecretKey tmp = factory.generateSecret(spec);
        return new SecretKeySpec(tmp.getEncoded(), "AES");
    }

    private byte[] generateRandomBytes(int length) {
        byte[] bytes = new byte[length];
        secureRandom.nextBytes(bytes);
        return bytes;
    }

    public String generateRandomPassword() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()";
        StringBuilder password = new StringBuilder(16);
        for (int i = 0; i < 16; i++) {
            password.append(chars.charAt(secureRandom.nextInt(chars.length())));
        }
        return password.toString();
    }
}
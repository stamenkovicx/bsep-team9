package com.bsep.pki_system.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class EncryptionServiceTest {

    @InjectMocks
    private EncryptionService encryptionService;

    private KeyPair testKeyPair;
    private String testPublicKeyPem;
    private String testData;

    @BeforeEach
    void setUp() {
        // Generate test key pair
        testKeyPair = encryptionService.generateKeyPair();

        // Convert public key to PEM format
        PublicKey publicKey = testKeyPair.getPublic();
        String publicKeyBase64 = Base64.getEncoder().encodeToString(publicKey.getEncoded());
        testPublicKeyPem = "-----BEGIN PUBLIC KEY-----\n" +
                publicKeyBase64 +
                "\n-----END PUBLIC KEY-----";

        testData = "Hello, this is a secret message!";
    }

    // ===== ENCRYPTION TESTS =====

    @Test
    void encryptWithPublicKey_WithValidData_ShouldReturnEncryptedString() {
        String encrypted = encryptionService.encryptWithPublicKey(testData, testPublicKeyPem);

        assertNotNull(encrypted);
        assertFalse(encrypted.isEmpty());
        // Encrypted data should be base64 encoded
        assertDoesNotThrow(() -> Base64.getDecoder().decode(encrypted));
    }

    @Test
    void encryptWithPublicKey_WithEmptyData_ShouldEncrypt() {
        String emptyData = "";

        String encrypted = encryptionService.encryptWithPublicKey(emptyData, testPublicKeyPem);

        assertNotNull(encrypted);
        assertFalse(encrypted.isEmpty());
    }

    @Test
    void encryptWithPublicKey_WithNullData_ShouldThrowException() {
        assertThrows(RuntimeException.class, () -> {
            encryptionService.encryptWithPublicKey(null, testPublicKeyPem);
        });
    }

    @Test
    void encryptWithPublicKey_WithInvalidPublicKey_ShouldThrowException() {
        String invalidPublicKey = "invalid-public-key";

        assertThrows(RuntimeException.class, () -> {
            encryptionService.encryptWithPublicKey(testData, invalidPublicKey);
        });
    }

    @Test
    void encryptWithPublicKey_WithNullPublicKey_ShouldThrowException() {
        assertThrows(RuntimeException.class, () -> {
            encryptionService.encryptWithPublicKey(testData, null);
        });
    }

    @Test
    void encryptWithPublicKey_WithPublicKeyWithoutHeaders_ShouldWork() {
        // Test without PEM headers
        String publicKeyBase64 = Base64.getEncoder().encodeToString(testKeyPair.getPublic().getEncoded());

        String encrypted = encryptionService.encryptWithPublicKey(testData, publicKeyBase64);

        assertNotNull(encrypted);
        assertFalse(encrypted.isEmpty());
    }

    @Test
    void encryptWithPublicKey_WithPublicKeyWithExtraSpaces_ShouldWork() {
        // Test with extra spaces and newlines
        String publicKeyBase64 = Base64.getEncoder().encodeToString(testKeyPair.getPublic().getEncoded());
        String publicKeyWithSpaces = "-----BEGIN PUBLIC KEY-----\n" +
                publicKeyBase64 + "\n\n" +
                "-----END PUBLIC KEY-----\n";

        String encrypted = encryptionService.encryptWithPublicKey(testData, publicKeyWithSpaces);

        assertNotNull(encrypted);
        assertFalse(encrypted.isEmpty());
    }

    // ===== DECRYPTION TESTS =====

    @Test
    void decryptWithPrivateKey_WithValidData_ShouldReturnOriginalString() {
        // First encrypt
        String encrypted = encryptionService.encryptWithPublicKey(testData, testPublicKeyPem);

        // Then decrypt
        String decrypted = encryptionService.decryptWithPrivateKey(encrypted, testKeyPair.getPrivate());

        assertNotNull(decrypted);
        assertEquals(testData, decrypted);
    }

    @Test
    void decryptWithPrivateKey_WithEmptyData_ShouldDecrypt() {
        String emptyData = "";
        String encrypted = encryptionService.encryptWithPublicKey(emptyData, testPublicKeyPem);

        String decrypted = encryptionService.decryptWithPrivateKey(encrypted, testKeyPair.getPrivate());

        assertNotNull(decrypted);
        assertEquals(emptyData, decrypted);
    }

    @Test
    void decryptWithPrivateKey_WithInvalidEncryptedData_ShouldThrowException() {
        String invalidEncryptedData = "invalid-base64-data";

        assertThrows(RuntimeException.class, () -> {
            encryptionService.decryptWithPrivateKey(invalidEncryptedData, testKeyPair.getPrivate());
        });
    }

    @Test
    void decryptWithPrivateKey_WithNullEncryptedData_ShouldThrowException() {
        assertThrows(RuntimeException.class, () -> {
            encryptionService.decryptWithPrivateKey(null, testKeyPair.getPrivate());
        });
    }

    @Test
    void decryptWithPrivateKey_WithNullPrivateKey_ShouldThrowException() {
        String encrypted = encryptionService.encryptWithPublicKey(testData, testPublicKeyPem);

        assertThrows(RuntimeException.class, () -> {
            encryptionService.decryptWithPrivateKey(encrypted, null);
        });
    }

    @Test
    void decryptWithPrivateKey_WithWrongPrivateKey_ShouldThrowException() {
        // Generate a different key pair
        KeyPair differentKeyPair = encryptionService.generateKeyPair();
        String encrypted = encryptionService.encryptWithPublicKey(testData, testPublicKeyPem);

        assertThrows(RuntimeException.class, () -> {
            encryptionService.decryptWithPrivateKey(encrypted, differentKeyPair.getPrivate());
        });
    }

    // ===== KEY PAIR GENERATION TESTS =====

    @Test
    void generateKeyPair_ShouldReturnValidKeyPair() {
        KeyPair keyPair = encryptionService.generateKeyPair();

        assertNotNull(keyPair);
        assertNotNull(keyPair.getPublic());
        assertNotNull(keyPair.getPrivate());
        assertEquals("RSA", keyPair.getPublic().getAlgorithm());
        assertEquals("RSA", keyPair.getPrivate().getAlgorithm());
    }

    @Test
    void generateKeyPair_ShouldGenerateDifferentKeys() {
        KeyPair keyPair1 = encryptionService.generateKeyPair();
        KeyPair keyPair2 = encryptionService.generateKeyPair();

        assertNotNull(keyPair1);
        assertNotNull(keyPair2);
        // Public keys should be different (random generation)
        assertNotEquals(
                Base64.getEncoder().encodeToString(keyPair1.getPublic().getEncoded()),
                Base64.getEncoder().encodeToString(keyPair2.getPublic().getEncoded())
        );
    }

    // ===== PUBLIC KEY EXTRACTION TESTS =====

    /*@Test
    void getPublicKeyFromCertificate_WithValidCertificate_ShouldReturnNull() {
        // This method currently returns null as per the implementation
        String certificatePem = "-----BEGIN CERTIFICATE-----\nMII...TEST...AQAB\n-----END CERTIFICATE-----";

        PublicKey result = encryptionService.getPublicKeyFromCertificate(certificatePem);

        assertNull(result); // As per current implementation
    }*/

    @Test
    void getPublicKeyFromCertificate_WithInvalidCertificate_ShouldThrowException() {
        String invalidCertificate = "invalid-certificate";

        assertThrows(RuntimeException.class, () -> {
            encryptionService.getPublicKeyFromCertificate(invalidCertificate);
        });
    }

    @Test
    void getPublicKeyFromCertificate_WithNullCertificate_ShouldThrowException() {
        assertThrows(RuntimeException.class, () -> {
            encryptionService.getPublicKeyFromCertificate(null);
        });
    }

    // ===== INTEGRATION TESTS =====

    @Test
    void encryptAndDecrypt_ShouldBeReversible() {
        // Test the complete flow
        String originalData = "Sensitive password data: MySecret123!";

        // Encrypt
        String encrypted = encryptionService.encryptWithPublicKey(originalData, testPublicKeyPem);

        // Decrypt
        String decrypted = encryptionService.decryptWithPrivateKey(encrypted, testKeyPair.getPrivate());

        assertEquals(originalData, decrypted);
    }

    @Test
    void encryptAndDecrypt_WithSpecialCharacters_ShouldWork() {
        String dataWithSpecialChars = "Password!@#$%^&*()123";

        String encrypted = encryptionService.encryptWithPublicKey(dataWithSpecialChars, testPublicKeyPem);
        String decrypted = encryptionService.decryptWithPrivateKey(encrypted, testKeyPair.getPrivate());

        assertEquals(dataWithSpecialChars, decrypted);
    }

    @Test
    void encryptAndDecrypt_WithMediumData_ShouldWork() {
        String mediumData = "This is a medium length password that should work with RSA encryption without any problems.";

        // Proveri dužinu
        int dataLength = mediumData.getBytes().length;
        assertTrue(dataLength < 100, "Data should be short enough for RSA");

        String encrypted = encryptionService.encryptWithPublicKey(mediumData, testPublicKeyPem);
        String decrypted = encryptionService.decryptWithPrivateKey(encrypted, testKeyPair.getPrivate());

        assertEquals(mediumData, decrypted);
    }

    @Test
    void encryptWithPublicKey_WithDataExceedingRSALimit_ShouldThrowException() {
        // Kreiraj podatke koji premašuju RSA limit
        StringBuilder veryLongData = new StringBuilder();
        for (int i = 0; i < 500; i++) {
            veryLongData.append("This is a very long line of text that will exceed RSA encryption limits. ");
        }
        String dataExceedingLimit = veryLongData.toString();

        // Proveri da li premašuje limit
        assertTrue(dataExceedingLimit.getBytes().length > 245, "Data should exceed RSA limit");

        assertThrows(RuntimeException.class, () -> {
            encryptionService.encryptWithPublicKey(dataExceedingLimit, testPublicKeyPem);
        });
    }

    @Test
    void encryptWithPublicKey_WithDataAtRSALimit_ShouldWork() {
        // Kreiraj podatke tačno na RSA limitu (245 bajtova)
        String dataAtLimit = "A".repeat(240); // 240 ASCII karaktera = 240 bajtova

        assertTrue(dataAtLimit.getBytes().length <= 245, "Data should be at or below RSA limit");

        String encrypted = encryptionService.encryptWithPublicKey(dataAtLimit, testPublicKeyPem);
        String decrypted = encryptionService.decryptWithPrivateKey(encrypted, testKeyPair.getPrivate());

        assertEquals(dataAtLimit, decrypted);
    }


    // ===== EDGE CASE TESTS =====

    @Test
    void encryptWithPublicKey_WithVeryShortData_ShouldWork() {
        String shortData = "a";

        String encrypted = encryptionService.encryptWithPublicKey(shortData, testPublicKeyPem);
        String decrypted = encryptionService.decryptWithPrivateKey(encrypted, testKeyPair.getPrivate());

        assertEquals(shortData, decrypted);
    }

    @Test
    void encryptWithPublicKey_WithWhitespaceData_ShouldWork() {
        String whitespaceData = "   \t\n  ";

        String encrypted = encryptionService.encryptWithPublicKey(whitespaceData, testPublicKeyPem);
        String decrypted = encryptionService.decryptWithPrivateKey(encrypted, testKeyPair.getPrivate());

        assertEquals(whitespaceData, decrypted);
    }
}
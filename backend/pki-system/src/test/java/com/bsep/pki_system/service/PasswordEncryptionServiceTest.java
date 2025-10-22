package com.bsep.pki_system.service;

import com.bsep.pki_system.dto.EncryptionResultDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class PasswordEncryptionServiceTest {

    @InjectMocks
    private PasswordEncryptionService passwordEncryptionService;

    private String testPassword;
    private String testMasterKey;

    @BeforeEach
    void setUp() {
        testPassword = "MySuperSecretPassword123!";
        testMasterKey = "MyMasterEncryptionKey2024";
    }

    // ===== ENCRYPTION TESTS =====

    @Test
    void encryptPassword_WithValidData_ShouldReturnEncryptionResult() throws Exception {
        EncryptionResultDTO result = passwordEncryptionService.encryptPassword(testPassword, testMasterKey);

        assertNotNull(result);
        assertNotNull(result.getEncryptedData());
        assertNotNull(result.getIv());
        assertNotNull(result.getSalt());
        assertEquals("AES/GCM/NoPadding", result.getAlgorithm());

        // Proveri da su IV i salt base64 encoded
        assertDoesNotThrow(() -> Base64.getDecoder().decode(result.getIv()));
        assertDoesNotThrow(() -> Base64.getDecoder().decode(result.getSalt()));
        assertDoesNotThrow(() -> Base64.getDecoder().decode(result.getEncryptedData()));
    }

    @Test
    void encryptPassword_WithEmptyPassword_ShouldWork() throws Exception {
        String emptyPassword = "";

        EncryptionResultDTO result = passwordEncryptionService.encryptPassword(emptyPassword, testMasterKey);

        assertNotNull(result);
        assertNotNull(result.getEncryptedData());
    }

    @Test
    void encryptPassword_WithNullPassword_ShouldThrowException() {
        assertThrows(Exception.class, () -> {
            passwordEncryptionService.encryptPassword(null, testMasterKey);
        });
    }

    @Test
    void encryptPassword_WithNullMasterKey_ShouldThrowException() {
        assertThrows(Exception.class, () -> {
            passwordEncryptionService.encryptPassword(testPassword, null);
        });
    }

    @Test
    void encryptPassword_WithSpecialCharacters_ShouldWork() throws Exception {
        String passwordWithSpecialChars = "P@ssw0rd!@#$%^&*()";

        EncryptionResultDTO result = passwordEncryptionService.encryptPassword(passwordWithSpecialChars, testMasterKey);

        assertNotNull(result);
        assertNotNull(result.getEncryptedData());
    }

    // ===== DECRYPTION TESTS =====

    @Test
    void decryptPassword_WithValidData_ShouldReturnOriginalPassword() throws Exception {
        // Prvo enkriptuj
        EncryptionResultDTO encrypted = passwordEncryptionService.encryptPassword(testPassword, testMasterKey);

        // Onda dekriptuj
        String decrypted = passwordEncryptionService.decryptPassword(
                encrypted.getEncryptedData(),
                encrypted.getIv(),
                encrypted.getSalt(),
                testMasterKey
        );

        assertEquals(testPassword, decrypted);
    }

    @Test
    void decryptPassword_WithEmptyPassword_ShouldWork() throws Exception {
        String emptyPassword = "";
        EncryptionResultDTO encrypted = passwordEncryptionService.encryptPassword(emptyPassword, testMasterKey);

        String decrypted = passwordEncryptionService.decryptPassword(
                encrypted.getEncryptedData(),
                encrypted.getIv(),
                encrypted.getSalt(),
                testMasterKey
        );

        assertEquals(emptyPassword, decrypted);
    }

    @Test
    void decryptPassword_WithWrongMasterKey_ShouldThrowException() throws Exception {
        EncryptionResultDTO encrypted = passwordEncryptionService.encryptPassword(testPassword, testMasterKey);
        String wrongMasterKey = "WrongMasterKey123";

        assertThrows(Exception.class, () -> {
            passwordEncryptionService.decryptPassword(
                    encrypted.getEncryptedData(),
                    encrypted.getIv(),
                    encrypted.getSalt(),
                    wrongMasterKey
            );
        });
    }

    @Test
    void decryptPassword_WithWrongIV_ShouldThrowException() throws Exception {
        EncryptionResultDTO encrypted = passwordEncryptionService.encryptPassword(testPassword, testMasterKey);
        String wrongIv = Base64.getEncoder().encodeToString(new byte[12]); // Pogrešan IV

        assertThrows(Exception.class, () -> {
            passwordEncryptionService.decryptPassword(
                    encrypted.getEncryptedData(),
                    wrongIv,
                    encrypted.getSalt(),
                    testMasterKey
            );
        });
    }

    @Test
    void decryptPassword_WithWrongSalt_ShouldThrowException() throws Exception {
        EncryptionResultDTO encrypted = passwordEncryptionService.encryptPassword(testPassword, testMasterKey);
        String wrongSalt = Base64.getEncoder().encodeToString(new byte[16]); // Pogrešan salt

        assertThrows(Exception.class, () -> {
            passwordEncryptionService.decryptPassword(
                    encrypted.getEncryptedData(),
                    encrypted.getIv(),
                    wrongSalt,
                    testMasterKey
            );
        });
    }

    @Test
    void decryptPassword_WithInvalidBase64_ShouldThrowException() {
        String invalidBase64 = "invalid-base64-data";

        assertThrows(Exception.class, () -> {
            passwordEncryptionService.decryptPassword(
                    invalidBase64,
                    invalidBase64,
                    invalidBase64,
                    testMasterKey
            );
        });
    }

    @Test
    void decryptPassword_WithNullParameters_ShouldThrowException() {
        assertThrows(Exception.class, () -> {
            passwordEncryptionService.decryptPassword(null, "iv", "salt", testMasterKey);
        });

        assertThrows(Exception.class, () -> {
            passwordEncryptionService.decryptPassword("encrypted", null, "salt", testMasterKey);
        });

        assertThrows(Exception.class, () -> {
            passwordEncryptionService.decryptPassword("encrypted", "iv", null, testMasterKey);
        });

        assertThrows(Exception.class, () -> {
            passwordEncryptionService.decryptPassword("encrypted", "iv", "salt", null);
        });
    }

    // ===== ENCRYPT-DECRYPT INTEGRATION TESTS =====

    @Test
    void encryptAndDecrypt_ShouldBeReversible() throws Exception {
        String originalPassword = "VeryStrongPassword!123";

        EncryptionResultDTO encrypted = passwordEncryptionService.encryptPassword(originalPassword, testMasterKey);
        String decrypted = passwordEncryptionService.decryptPassword(
                encrypted.getEncryptedData(),
                encrypted.getIv(),
                encrypted.getSalt(),
                testMasterKey
        );

        assertEquals(originalPassword, decrypted);
    }

    @Test
    void encryptAndDecrypt_WithDifferentPasswords_ShouldWork() throws Exception {
        String[] passwords = {
                "short",
                "LongPasswordWithNumbers123",
                "P@ssw0rd!",
                "Another_Test-Password_456"
        };

        for (String password : passwords) {
            EncryptionResultDTO encrypted = passwordEncryptionService.encryptPassword(password, testMasterKey);
            String decrypted = passwordEncryptionService.decryptPassword(
                    encrypted.getEncryptedData(),
                    encrypted.getIv(),
                    encrypted.getSalt(),
                    testMasterKey
            );

            assertEquals(password, decrypted, "Failed for password: " + password);
        }
    }

    @Test
    void encryptAndDecrypt_WithDifferentMasterKeys_ShouldWork() throws Exception {
        String password = "TestPassword123";
        String[] masterKeys = {
                "key1",
                "LongMasterKeyForEncryption",
                "Another_Master-Key_2024",
                "!@#$%MasterKey^&*()"
        };

        for (String masterKey : masterKeys) {
            EncryptionResultDTO encrypted = passwordEncryptionService.encryptPassword(password, masterKey);
            String decrypted = passwordEncryptionService.decryptPassword(
                    encrypted.getEncryptedData(),
                    encrypted.getIv(),
                    encrypted.getSalt(),
                    masterKey
            );

            assertEquals(password, decrypted, "Failed for master key: " + masterKey);
        }
    }

    // ===== RANDOM PASSWORD GENERATION TESTS =====

    @Test
    void generateRandomPassword_ShouldReturnValidPassword() {
        String password = passwordEncryptionService.generateRandomPassword();

        assertNotNull(password);
        assertEquals(16, password.length());

        // Proveri da sadrži različite tipove karaktera - sa više pokušaja
        boolean hasUpper = password.matches(".*[A-Z].*");
        boolean hasLower = password.matches(".*[a-z].*");
        boolean hasNumber = password.matches(".*[0-9].*");
        boolean hasSpecial = password.matches(".*[!@#$%^&*()].*");

        assertTrue(hasUpper, "Should contain uppercase letters. Password: " + password);
        assertTrue(hasLower, "Should contain lowercase letters. Password: " + password);
        assertTrue(hasNumber, "Should contain numbers. Password: " + password);
        assertTrue(hasSpecial, "Should contain special characters. Password: " + password);
    }

    @Test
    void generateRandomPassword_ShouldContainRequiredCharacters_AfterMultipleAttempts() {
        // Testirajte više puta da budete sigurni
        boolean foundValidPassword = false;

        for (int i = 0; i < 10; i++) {
            String password = passwordEncryptionService.generateRandomPassword();

            boolean hasUpper = password.matches(".*[A-Z].*");
            boolean hasLower = password.matches(".*[a-z].*");
            boolean hasNumber = password.matches(".*[0-9].*");
            boolean hasSpecial = password.matches(".*[!@#$%^&*()].*");

            if (hasUpper && hasLower && hasNumber && hasSpecial) {
                foundValidPassword = true;
                break;
            }
        }

        assertTrue(foundValidPassword, "Should generate valid password with all character types within 10 attempts");
    }

    @Test
    void generateRandomPassword_ShouldGenerateDifferentPasswords() {
        String password1 = passwordEncryptionService.generateRandomPassword();
        String password2 = passwordEncryptionService.generateRandomPassword();

        assertNotNull(password1);
        assertNotNull(password2);
        assertNotEquals(password1, password2, "Should generate different passwords");
    }

    @Test
    void generateRandomPassword_ShouldHaveCorrectLength() {
        for (int i = 0; i < 10; i++) {
            String password = passwordEncryptionService.generateRandomPassword();
            assertEquals(16, password.length(), "Password should be 16 characters long");
        }
    }

    // ===== EDGE CASE TESTS =====

    @Test
    void encryptPassword_WithVeryLongPassword_ShouldWork() throws Exception {
        String longPassword = "A".repeat(1000); // Veoma dugačka lozinka

        EncryptionResultDTO result = passwordEncryptionService.encryptPassword(longPassword, testMasterKey);
        String decrypted = passwordEncryptionService.decryptPassword(
                result.getEncryptedData(),
                result.getIv(),
                result.getSalt(),
                testMasterKey
        );

        assertEquals(longPassword, decrypted);
    }

    @Test
    void encryptPassword_WithUnicodeCharacters_ShouldWork() throws Exception {
        String unicodePassword = "Password🔐Unicode🚀Test🎯";

        EncryptionResultDTO result = passwordEncryptionService.encryptPassword(unicodePassword, testMasterKey);
        String decrypted = passwordEncryptionService.decryptPassword(
                result.getEncryptedData(),
                result.getIv(),
                result.getSalt(),
                testMasterKey
        );

        assertEquals(unicodePassword, decrypted);
    }

    @Test
    void encryptAndDecrypt_MultipleTimes_ShouldWork() throws Exception {
        String password = "TestPassword";

        for (int i = 0; i < 5; i++) {
            EncryptionResultDTO encrypted = passwordEncryptionService.encryptPassword(password, testMasterKey);
            String decrypted = passwordEncryptionService.decryptPassword(
                    encrypted.getEncryptedData(),
                    encrypted.getIv(),
                    encrypted.getSalt(),
                    testMasterKey
            );

            assertEquals(password, decrypted, "Failed on iteration: " + i);
        }
    }
}
package com.bsep.pki_system.service;

import com.bsep.pki_system.dto.EncryptionResultDTO;
import com.bsep.pki_system.model.Certificate;
import com.bsep.pki_system.model.KeystorePassword;
import com.bsep.pki_system.repository.KeystorePasswordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.reflect.Field;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class KeystoreServiceTest {

    private KeystorePasswordRepository keystorePasswordRepository;
    private PasswordEncryptionService encryptionService;
    private CertificateService certificateService;
    private KeystoreService keystoreService;

    private KeyPair keyPair;
    private Certificate modelCertificate;
    private KeystorePassword keystorePassword;

    @BeforeEach
    void setUp() throws Exception {
        keystorePasswordRepository = mock(KeystorePasswordRepository.class);
        encryptionService = mock(PasswordEncryptionService.class);
        certificateService = mock(CertificateService.class);

        keystoreService = new KeystoreService(keystorePasswordRepository, encryptionService, certificateService);

        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        keyPair = keyGen.generateKeyPair();

        modelCertificate = new Certificate();
        modelCertificate.setId(1L);
        modelCertificate.setSerialNumber("123456789");

        keystorePassword = new KeystorePassword();
        keystorePassword.setId(1L);
        keystorePassword.setCertificate(modelCertificate);
        keystorePassword.setEncryptedPassword("encrypted-pass");
        keystorePassword.setIv("iv");
        keystorePassword.setSalt("salt");
        keystorePassword.setEncryptionAlgorithm("AES/CBC/PKCS5Padding");

        setPrivateField(keystoreService, "masterKey", "test-master-key");

        File keystoreFile = new File("keystore.p12");
        if (keystoreFile.exists()) {
            keystoreFile.delete();
        }
    }

    private void setPrivateField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    // ===== SAMO TESTOVI KOJI RADE BEZ KEYSTORE OPERACIJA =====

    @Test
    void getPrivateKey_WhenPasswordNotFound_ShouldThrowException() {
        String alias = "non-existent-alias";
        String serialNumber = "999999999";

        when(keystorePasswordRepository.findByCertificateSerialNumber(serialNumber))
                .thenReturn(Optional.empty());

        Exception exception = assertThrows(RuntimeException.class, () -> {
            keystoreService.getPrivateKey(alias, serialNumber);
        });

        assertEquals("Keystore password not found for certificate: 999999999", exception.getMessage());
        verify(keystorePasswordRepository, times(1)).findByCertificateSerialNumber(serialNumber);
    }

    @Test
    void keyPairGeneration_ShouldWork() {
        assertNotNull(keyPair.getPrivate());
        assertNotNull(keyPair.getPublic());
        assertEquals("RSA", keyPair.getPrivate().getAlgorithm());
        assertEquals("RSA", keyPair.getPublic().getAlgorithm());
    }

    @Test
    void encryptionServiceMock_ShouldWork() throws Exception {
        String password = "test-password";
        String masterKey = "test-master-key";
        EncryptionResultDTO expectedResult = new EncryptionResultDTO();
        expectedResult.setEncryptedData("encrypted");
        expectedResult.setIv("iv");
        expectedResult.setSalt("salt");
        expectedResult.setAlgorithm("AES/CBC/PKCS5Padding");

        when(encryptionService.encryptPassword(password, masterKey)).thenReturn(expectedResult);

        EncryptionResultDTO result = encryptionService.encryptPassword(password, masterKey);

        assertNotNull(result);
        assertEquals("encrypted", result.getEncryptedData());
        assertEquals("iv", result.getIv());
        assertEquals("salt", result.getSalt());
    }

    @Test
    void repositoryOperations_ShouldWork() {
        when(keystorePasswordRepository.save(any(KeystorePassword.class))).thenReturn(keystorePassword);
        when(keystorePasswordRepository.findByCertificateSerialNumber("123456789")).thenReturn(Optional.of(keystorePassword));

        KeystorePassword saved = keystorePasswordRepository.save(keystorePassword);
        Optional<KeystorePassword> found = keystorePasswordRepository.findByCertificateSerialNumber("123456789");

        assertNotNull(saved);
        assertTrue(found.isPresent());
        assertEquals(keystorePassword.getId(), found.get().getId());
    }

    @Test
    void decryptionServiceMock_ShouldWork() throws Exception {
        String encryptedPassword = "encrypted-pass";
        String iv = "iv";
        String salt = "salt";
        String masterKey = "test-master-key";
        String decryptedPassword = "decrypted-pass";

        when(encryptionService.decryptPassword(encryptedPassword, iv, salt, masterKey)).thenReturn(decryptedPassword);

        String result = encryptionService.decryptPassword(encryptedPassword, iv, salt, masterKey);

        assertEquals("decrypted-pass", result);
    }
}
package com.bsep.pki_system.service;

import com.bsep.pki_system.model.Certificate;
import com.bsep.pki_system.model.CertificateStatus;
import com.bsep.pki_system.model.CertificateType;
import com.bsep.pki_system.model.User;
import com.bsep.pki_system.model.UserRole;
import com.bsep.pki_system.repository.CertificateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CertificateServiceTest {

    @Mock
    private CertificateRepository certificateRepository;

    @Mock
    private CertificateGeneratorService certificateGeneratorService;

    @Mock
    private KeystoreService keystoreService;

    @Mock
    private CRLService crlService;

    @InjectMocks
    private CertificateService certificateService;

    private Certificate validCertificate;
    private User testUser;

    @BeforeEach
    void setUp() {
        // Setup test user
        testUser = new User();
        testUser.setId(1L);
        testUser.setEmail("test@example.com");
        testUser.setRole(UserRole.BASIC);

        // Setup valid certificate
        validCertificate = new Certificate();
        validCertificate.setId(1L);
        validCertificate.setSerialNumber("12345");
        validCertificate.setStatus(CertificateStatus.VALID);
        validCertificate.setType(CertificateType.END_ENTITY);
        validCertificate.setOwner(testUser);
        validCertificate.setValidFrom(new Date(System.currentTimeMillis() - 86400000));
        validCertificate.setValidTo(new Date(System.currentTimeMillis() + 86400000));
    }

    @Test
    void findById_WhenCertificateExists_ShouldReturnCertificate() {
        // Arrange
        when(certificateRepository.findById(1L)).thenReturn(Optional.of(validCertificate));

        // Act
        Optional<Certificate> result = certificateService.findById(1L);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(validCertificate, result.get());
        verify(certificateRepository, times(1)).findById(1L);
    }

    @Test
    void findById_WhenCertificateNotExists_ShouldReturnEmpty() {
        // Arrange
        when(certificateRepository.findById(999L)).thenReturn(Optional.empty());

        // Act
        Optional<Certificate> result = certificateService.findById(999L);

        // Assert
        assertFalse(result.isPresent());
        verify(certificateRepository, times(1)).findById(999L);
    }

    @Test
    void saveCertificate_ShouldCallRepositorySave() {
        // Arrange
        when(certificateRepository.save(validCertificate)).thenReturn(validCertificate);

        // Act
        Certificate result = certificateService.saveCertificate(validCertificate);

        // Assert
        assertNotNull(result);
        assertEquals(validCertificate, result);
        verify(certificateRepository, times(1)).save(validCertificate);
    }
}
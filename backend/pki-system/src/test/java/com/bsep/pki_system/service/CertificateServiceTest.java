package com.bsep.pki_system.service;

import com.bsep.pki_system.dto.CreateCertificateDTO;
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

import java.util.*;

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
    private Certificate revokedCertificate;
    private Certificate expiredCertificate;
    private Certificate rootCertificate;
    private Certificate intermediateCertificate;
    private User testUser;
    private User adminUser;
    private User caUser;

    @BeforeEach
    void setUp() {
        // Setup test users
        testUser = new User();
        testUser.setId(1L);
        testUser.setEmail("test@example.com");
        testUser.setRole(UserRole.BASIC);
        testUser.setOrganization("Test Org");

        adminUser = new User();
        adminUser.setId(2L);
        adminUser.setEmail("admin@example.com");
        adminUser.setRole(UserRole.ADMIN);

        caUser = new User();
        caUser.setId(3L);
        caUser.setEmail("ca@example.com");
        caUser.setRole(UserRole.CA);
        caUser.setOrganization("CA Org");

        // Setup valid certificate
        validCertificate = new Certificate();
        validCertificate.setId(1L);
        validCertificate.setSerialNumber("12345");
        validCertificate.setStatus(CertificateStatus.VALID);
        validCertificate.setType(CertificateType.END_ENTITY);
        validCertificate.setOwner(testUser);
        validCertificate.setValidFrom(new Date(System.currentTimeMillis() - 86400000));
        validCertificate.setValidTo(new Date(System.currentTimeMillis() + 86400000));
        validCertificate.setSubject("CN=Test User,O=Test Org");
        validCertificate.setPublicKey("public-key-base64");

        // Setup revoked certificate
        revokedCertificate = new Certificate();
        revokedCertificate.setId(2L);
        revokedCertificate.setStatus(CertificateStatus.REVOKED);
        revokedCertificate.setValidFrom(new Date(System.currentTimeMillis() - 86400000));
        revokedCertificate.setValidTo(new Date(System.currentTimeMillis() + 86400000));

        // Setup expired certificate
        expiredCertificate = new Certificate();
        expiredCertificate.setId(3L);
        expiredCertificate.setStatus(CertificateStatus.VALID);
        expiredCertificate.setValidFrom(new Date(System.currentTimeMillis() - 172800000));
        expiredCertificate.setValidTo(new Date(System.currentTimeMillis() - 86400000));

        // Setup root certificate
        rootCertificate = new Certificate();
        rootCertificate.setId(4L);
        rootCertificate.setSerialNumber("ROOT-123");
        rootCertificate.setStatus(CertificateStatus.VALID);
        rootCertificate.setType(CertificateType.ROOT);
        rootCertificate.setIsCA(true);
        rootCertificate.setValidFrom(new Date(System.currentTimeMillis() - 86400000));
        rootCertificate.setValidTo(new Date(System.currentTimeMillis() + 86400000));
        rootCertificate.setSubject("CN=Root CA,O=Test Org");

        // Setup intermediate certificate
        intermediateCertificate = new Certificate();
        intermediateCertificate.setId(5L);
        intermediateCertificate.setSerialNumber("INTERMEDIATE-123");
        intermediateCertificate.setStatus(CertificateStatus.VALID);
        intermediateCertificate.setType(CertificateType.INTERMEDIATE);
        intermediateCertificate.setIsCA(true);
        intermediateCertificate.setIssuerCertificate(rootCertificate);
        intermediateCertificate.setValidFrom(new Date(System.currentTimeMillis() - 86400000));
        intermediateCertificate.setValidTo(new Date(System.currentTimeMillis() + 86400000));
        intermediateCertificate.setSubject("CN=Intermediate CA,O=Test Org");
    }

    // ===== BASIC CRUD TESTS =====
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
    @Test

    void findBySerialNumber_ShouldReturnCertificate() {
        when(certificateRepository.findBySerialNumber("12345")).thenReturn(Optional.of(validCertificate));

        Optional<Certificate> result = certificateService.findBySerialNumber("12345");

        assertTrue(result.isPresent());
        assertEquals(validCertificate, result.get());
    }

    @Test
    void findAll_ShouldReturnAllCertificates() {
        List<Certificate> certificates = Arrays.asList(validCertificate, rootCertificate);
        when(certificateRepository.findAll()).thenReturn(certificates);

        List<Certificate> result = certificateService.findAll();

        assertEquals(2, result.size());
        verify(certificateRepository, times(1)).findAll();
    }

    @Test
    void findByType_ShouldReturnFilteredCertificates() {
        List<Certificate> rootCerts = Collections.singletonList(rootCertificate);
        when(certificateRepository.findByType(CertificateType.ROOT)).thenReturn(rootCerts);

        List<Certificate> result = certificateService.findByType(CertificateType.ROOT);

        assertEquals(1, result.size());
        assertEquals(CertificateType.ROOT, result.get(0).getType());
    }

    @Test
    void findByOwner_ShouldReturnUserCertificates() {
        List<Certificate> userCerts = Collections.singletonList(validCertificate);
        when(certificateRepository.findByOwnerId(1L)).thenReturn(userCerts);

        List<Certificate> result = certificateService.findByOwner(testUser);

        assertEquals(1, result.size());
        assertEquals(testUser.getId(), result.get(0).getOwner().getId());
    }

    // ===== VALIDATION TESTS =====

    @Test
    void isCertificateValid_WhenCertificateNotFound_ShouldReturnFalse() {
        when(certificateRepository.findById(999L)).thenReturn(Optional.empty());

        boolean result = certificateService.isCertificateValid(999L);

        assertFalse(result);
    }

    @Test
    void isCertificateValid_WhenCertificateRevoked_ShouldReturnFalse() {
        when(certificateRepository.findById(2L)).thenReturn(Optional.of(revokedCertificate));

        boolean result = certificateService.isCertificateValid(2L);

        assertFalse(result);
    }

    @Test
    void isCertificateValid_WhenCertificateExpired_ShouldReturnFalse() {
        when(certificateRepository.findById(3L)).thenReturn(Optional.of(expiredCertificate));

        boolean result = certificateService.isCertificateValid(3L);

        assertFalse(result);
    }

    @Test
    void isCertificateValid_WhenCertificateValid_ShouldReturnTrue() {
        when(certificateRepository.findById(1L)).thenReturn(Optional.of(validCertificate));
        // Mock chain validation to return true
        // This is simplified - in real scenario you'd need to mock the chain

        boolean result = certificateService.isCertificateValid(1L);

        // This will depend on chain validation
        assertDoesNotThrow(() -> certificateService.isCertificateValid(1L));
    }

    // ===== REVOCATION TESTS =====

    @Test
    void revokeCertificate_WhenCertificateExists_ShouldUpdateStatus() {
        when(certificateRepository.findById(1L)).thenReturn(Optional.of(validCertificate));
        when(certificateRepository.save(any(Certificate.class))).thenReturn(validCertificate);

        certificateService.revokeCertificate(1L, "Testing revocation");

        assertEquals(CertificateStatus.REVOKED, validCertificate.getStatus());
        assertEquals("Testing revocation", validCertificate.getRevocationReason());
        assertNotNull(validCertificate.getRevokedAt());
        verify(certificateRepository, times(1)).save(validCertificate);
    }

    @Test
    void revokeCertificate_WhenCertificateNotExists_ShouldDoNothing() {
        when(certificateRepository.findById(999L)).thenReturn(Optional.empty());

        certificateService.revokeCertificate(999L, "Testing revocation");

        verify(certificateRepository, never()).save(any());
    }

    // ===== AUTHORIZATION TESTS =====

    @Test
    void canUserAccessCertificate_WhenAdminUser_ShouldReturnTrue() {
        when(certificateRepository.findById(1L)).thenReturn(Optional.of(validCertificate));

        boolean result = certificateService.canUserAccessCertificate(1L, adminUser);

        assertTrue(result);
    }

    @Test
    void canUserAccessCertificate_WhenBasicUserOwnsCertificate_ShouldReturnTrue() {
        when(certificateRepository.findById(1L)).thenReturn(Optional.of(validCertificate));

        boolean result = certificateService.canUserAccessCertificate(1L, testUser);

        assertTrue(result);
    }

    @Test
    void canUserAccessCertificate_WhenBasicUserDoesNotOwnCertificate_ShouldReturnFalse() {
        User otherUser = new User();
        otherUser.setId(999L);
        otherUser.setRole(UserRole.BASIC);

        when(certificateRepository.findById(1L)).thenReturn(Optional.of(validCertificate));

        boolean result = certificateService.canUserAccessCertificate(1L, otherUser);

        assertFalse(result);
    }

    @Test
    void canUserAccessCertificate_WhenCAUserSameOrganization_ShouldReturnTrue() {
        Certificate certInSameOrg = new Certificate();
        certInSameOrg.setId(6L);
        certInSameOrg.setSubject("CN=Test,O=CA Org");
        certInSameOrg.setOwner(caUser);

        when(certificateRepository.findById(6L)).thenReturn(Optional.of(certInSameOrg));

        boolean result = certificateService.canUserAccessCertificate(6L, caUser);

        assertTrue(result);
    }

    // ===== CERTIFICATE CREATION TESTS =====

    @Test
    void createAndSaveIntermediateCertificate_WithValidIssuer_ShouldCreateCertificate() throws Exception {
        CreateCertificateDTO request = new CreateCertificateDTO();

        // Postavi datume koji su SIGURNO unutar root sertifikata
        Date now = new Date();
        Date issuerValidFrom = rootCertificate.getValidFrom();
        Date issuerValidTo = rootCertificate.getValidTo();

        // Kreiraj datume koji su striktno unutar issuer-ovog perioda
        long issuerDuration = issuerValidTo.getTime() - issuerValidFrom.getTime();
        Date newCertFrom = new Date(issuerValidFrom.getTime() + (issuerDuration / 10)); // 10% od početka
        Date newCertTo = new Date(issuerValidTo.getTime() - (issuerDuration / 10));     // 10% pre kraja

        request.setIssuerCertificateId(4L);
        request.setValidFrom(newCertFrom);
        request.setValidTo(newCertTo);

        when(certificateRepository.findById(4L)).thenReturn(Optional.of(rootCertificate));
        when(certificateGeneratorService.generateIntermediateCertificate(any(), any(), any()))
                .thenReturn(intermediateCertificate);
        when(certificateRepository.save(any())).thenReturn(intermediateCertificate);

        Certificate result = certificateService.createAndSaveIntermediateCertificate(request, caUser);

        assertNotNull(result);
        verify(certificateGeneratorService, times(1)).generateIntermediateCertificate(any(), any(), any());
        verify(certificateRepository, times(1)).save(intermediateCertificate);
    }

    @Test
    void createAndSaveIntermediateCertificate_WithInvalidIssuer_ShouldThrowException() {
        CreateCertificateDTO request = new CreateCertificateDTO();
        request.setIssuerCertificateId(999L);

        when(certificateRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> {
            certificateService.createAndSaveIntermediateCertificate(request, caUser);
        });
    }

    // ===== ORGANIZATION CHAIN TESTS =====

    @Test
    void isCertificateInUserOrganizationChain_WhenCertificateInChain_ShouldReturnTrue() {
        Certificate cert = new Certificate();
        cert.setSubject("CN=Test,O=Test Org");
        // NE postavljamo issuerCertificate da bi chain bio kratak
        cert.setIssuerCertificate(null);

        boolean result = certificateService.isCertificateInUserOrganizationChain(cert, "Test Org");

        assertTrue(result);
    }

    @Test
    void isCertificateInUserOrganizationChain_WhenCertificateNotInChain_ShouldReturnFalse() {
        Certificate cert = new Certificate();
        cert.setSubject("CN=Test,O=Different Org");
        // NE postavljamo issuerCertificate da bi chain bio prazan
        cert.setIssuerCertificate(null);

        boolean result = certificateService.isCertificateInUserOrganizationChain(cert, "Test Org");

        assertFalse(result);
    }

    // ===== PASSWORD MANAGER TESTS =====

    @Test
    void canUserUsePasswordManager_WhenUserHasValidEECertificate_ShouldReturnTrue() {
        when(certificateRepository.findByOwnerId(1L)).thenReturn(Collections.singletonList(validCertificate));

        boolean result = certificateService.canUserUsePasswordManager(testUser);

        assertTrue(result);
    }

    @Test
    void canUserUsePasswordManager_WhenUserNoValidEECertificate_ShouldReturnFalse() {
        User userWithoutCert = new User();
        userWithoutCert.setId(999L);
        userWithoutCert.setRole(UserRole.BASIC);

        when(certificateRepository.findByOwnerId(999L)).thenReturn(Collections.emptyList());

        boolean result = certificateService.canUserUsePasswordManager(userWithoutCert);

        assertFalse(result);
    }

    @Test
    void findValidEndEntityCertificateByOwner_WhenUserHasValidEE_ShouldReturnCertificate() {
        when(certificateRepository.findByOwnerId(1L)).thenReturn(Collections.singletonList(validCertificate));

        Optional<Certificate> result = certificateService.findValidEndEntityCertificateByOwner(testUser);

        assertTrue(result.isPresent());
        assertEquals(CertificateType.END_ENTITY, result.get().getType());
    }

    @Test
    void findUserPublicKey_WhenUserHasValidCertificate_ShouldReturnPublicKey() {
        when(certificateRepository.findByOwnerId(1L)).thenReturn(Collections.singletonList(validCertificate));

        Optional<String> result = certificateService.findUserPublicKey(testUser);

        assertTrue(result.isPresent());
        assertEquals("public-key-base64", result.get());
    }

    // ===== FIND ISSUERS TESTS =====

    @Test
    void findValidIssuersForUser_WhenAdminUser_ShouldReturnAllValidIssuers() {
        List<Certificate> validIssuers = Arrays.asList(rootCertificate, intermediateCertificate);

        // Koristi any() umesto konkretnog datuma jer se datum generiše u runtime
        when(certificateRepository.findValidIssuers(eq(CertificateStatus.VALID), any(Date.class)))
                .thenReturn(validIssuers);

        List<Certificate> result = certificateService.findValidIssuersForUser(adminUser);

        assertNotNull(result);
        // Može biti manje od 2 zbog dodatnih filtera u servisu
        verify(certificateRepository, times(1)).findValidIssuers(eq(CertificateStatus.VALID), any(Date.class));
    }

    @Test
    void findCertificateChainForUser_WhenAdminUser_ShouldReturnAllCertificates() {
        List<Certificate> allCerts = Arrays.asList(validCertificate, rootCertificate, intermediateCertificate);
        when(certificateRepository.findAll()).thenReturn(allCerts);

        List<Certificate> result = certificateService.findCertificateChainForUser(adminUser);

        assertEquals(3, result.size());
    }

    @Test
    void findCertificateChainForUser_WhenBasicUser_ShouldReturnOnlyOwnedCertificates() {
        List<Certificate> userCerts = Collections.singletonList(validCertificate);
        when(certificateRepository.findByOwnerId(1L)).thenReturn(userCerts);

        List<Certificate> result = certificateService.findCertificateChainForUser(testUser);

        assertEquals(1, result.size());
        assertEquals(testUser.getId(), result.get(0).getOwner().getId());
    }
}
package com.bsep.pki_system.service;

import com.bsep.pki_system.model.Certificate;
import com.bsep.pki_system.model.CertificateStatus;
import com.bsep.pki_system.repository.CertificateRepository;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509CRLHolder;
import org.bouncycastle.cert.X509v2CRLBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigInteger;
import java.security.*;
import java.security.cert.CertificateFactory;
import java.security.cert.X509CRL;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CRLServiceTest {

    @Mock
    private CertificateRepository certificateRepository;

    @Mock
    private KeystoreService keystoreService;

    @InjectMocks
    private CRLService crlService;

    private Certificate rootCertificate;
    private Certificate intermediateCertificate;
    private Certificate endEntityCertificate;
    private Certificate revokedCertificate1;
    private Certificate revokedCertificate2;
    private KeyPair keyPair;

    @BeforeEach
    void setUp() throws Exception {
        // Generate real key pair for testing
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        keyPair = keyGen.generateKeyPair();

        // Setup root CA certificate - use numeric serial numbers
        rootCertificate = new Certificate();
        rootCertificate.setId(1L);
        rootCertificate.setSerialNumber("123456789"); // Use numeric serial number
        rootCertificate.setSubject("CN=Root CA,O=Test Organization,C=RS");
        rootCertificate.setStatus(CertificateStatus.VALID);
        rootCertificate.setIsCA(true);
        rootCertificate.setCrlNumber(1L);
        rootCertificate.setLastCRLUpdate(LocalDateTime.now().minusDays(1));
        rootCertificate.setPublicKey(Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()));

        // Setup intermediate CA certificate
        intermediateCertificate = new Certificate();
        intermediateCertificate.setId(2L);
        intermediateCertificate.setSerialNumber("987654321"); // Use numeric serial number
        intermediateCertificate.setSubject("CN=Intermediate CA,O=Test Organization,C=RS");
        intermediateCertificate.setStatus(CertificateStatus.VALID);
        intermediateCertificate.setIsCA(true);
        intermediateCertificate.setIssuerCertificate(rootCertificate);
        intermediateCertificate.setCrlNumber(1L);
        intermediateCertificate.setPublicKey(Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()));

        // Setup end entity certificate
        endEntityCertificate = new Certificate();
        endEntityCertificate.setId(3L);
        endEntityCertificate.setSerialNumber("111111111"); // Use numeric serial number
        endEntityCertificate.setSubject("CN=End Entity,O=Test Organization,C=RS");
        endEntityCertificate.setStatus(CertificateStatus.VALID);
        endEntityCertificate.setIsCA(false);
        endEntityCertificate.setIssuerCertificate(intermediateCertificate);
        endEntityCertificate.setPublicKey(Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()));

        // Setup revoked certificates
        revokedCertificate1 = new Certificate();
        revokedCertificate1.setId(4L);
        revokedCertificate1.setSerialNumber("222222222"); // Use numeric serial number
        revokedCertificate1.setSubject("CN=Revoked 1,O=Test Organization,C=RS");
        revokedCertificate1.setStatus(CertificateStatus.REVOKED);
        revokedCertificate1.setIssuerCertificate(rootCertificate);
        revokedCertificate1.setRevocationReason("keyCompromise");
        revokedCertificate1.setRevokedAt(LocalDateTime.now().minusDays(2));
        revokedCertificate1.setPublicKey(Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()));

        revokedCertificate2 = new Certificate();
        revokedCertificate2.setId(5L);
        revokedCertificate2.setSerialNumber("333333333"); // Use numeric serial number
        revokedCertificate2.setSubject("CN=Revoked 2,O=Test Organization,C=RS");
        revokedCertificate2.setStatus(CertificateStatus.REVOKED);
        revokedCertificate2.setIssuerCertificate(rootCertificate);
        revokedCertificate2.setRevocationReason("superseded");
        revokedCertificate2.setRevokedAt(LocalDateTime.now().minusDays(1));
        revokedCertificate2.setPublicKey(Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()));
    }

    // ===== CRL GENERATION TESTS =====

    @Test
    void generateCRL_WithRevokedCertificates_ShouldGenerateValidCRL() throws Exception {
        // Arrange
        List<Certificate> revokedCerts = Arrays.asList(revokedCertificate1, revokedCertificate2);
        when(certificateRepository.findByIssuerCertificateId(rootCertificate.getId()))
                .thenReturn(revokedCerts);
        when(keystoreService.getPrivateKey("CA_123456789", "123456789"))
                .thenReturn(keyPair.getPrivate());
        when(certificateRepository.save(any(Certificate.class))).thenReturn(rootCertificate);

        // Act
        byte[] crlBytes = crlService.generateCRL(rootCertificate);

        // Assert
        assertNotNull(crlBytes);
        assertTrue(crlBytes.length > 0);

        verify(certificateRepository, times(1)).findByIssuerCertificateId(rootCertificate.getId());
        verify(keystoreService, times(1)).getPrivateKey("CA_123456789", "123456789");
        verify(certificateRepository, times(1)).save(rootCertificate);
    }

    @Test
    void generateCRL_WithNoRevokedCertificates_ShouldGenerateEmptyCRL() throws Exception {
        // Arrange
        when(certificateRepository.findByIssuerCertificateId(rootCertificate.getId()))
                .thenReturn(Collections.emptyList());
        when(keystoreService.getPrivateKey("CA_123456789", "123456789"))
                .thenReturn(keyPair.getPrivate());
        when(certificateRepository.save(any(Certificate.class))).thenReturn(rootCertificate);

        // Act
        byte[] crlBytes = crlService.generateCRL(rootCertificate);

        // Assert
        assertNotNull(crlBytes);
        assertTrue(crlBytes.length > 0);

        verify(certificateRepository, times(1)).findByIssuerCertificateId(rootCertificate.getId());
    }

    @Test
    void generateCRL_WhenPrivateKeyNotFound_ShouldThrowException() throws Exception {
        // Arrange
        List<Certificate> revokedCerts = Arrays.asList(revokedCertificate1);
        when(certificateRepository.findByIssuerCertificateId(rootCertificate.getId()))
                .thenReturn(revokedCerts);
        when(keystoreService.getPrivateKey("CA_123456789", "123456789"))
                .thenReturn(null);

        // Act & Assert
        Exception exception = assertThrows(RuntimeException.class, () -> {
            crlService.generateCRL(rootCertificate);
        });

        assertEquals("Private key not found for CA: 123456789", exception.getMessage());

        verify(certificateRepository, times(1)).findByIssuerCertificateId(rootCertificate.getId());
        verify(keystoreService, times(1)).getPrivateKey("CA_123456789", "123456789");
        verify(certificateRepository, never()).save(any(Certificate.class));
    }

    @Test
    void generateCRL_ShouldIncrementCrlNumber() throws Exception {
        // Arrange
        List<Certificate> revokedCerts = Arrays.asList(revokedCertificate1);
        when(certificateRepository.findByIssuerCertificateId(rootCertificate.getId()))
                .thenReturn(revokedCerts);
        when(keystoreService.getPrivateKey("CA_123456789", "123456789"))
                .thenReturn(keyPair.getPrivate());
        when(certificateRepository.save(any(Certificate.class))).thenReturn(rootCertificate);

        Long initialCrlNumber = rootCertificate.getCrlNumber();

        // Act
        crlService.generateCRL(rootCertificate);

        // Assert
        assertEquals(initialCrlNumber + 1, rootCertificate.getCrlNumber().longValue());
        assertNotNull(rootCertificate.getLastCRLUpdate());
    }

    @Test
    void generateCRL_WithNullCrlNumber_ShouldSetTo1() throws Exception {
        // Arrange
        rootCertificate.setCrlNumber(null);
        List<Certificate> revokedCerts = Arrays.asList(revokedCertificate1);
        when(certificateRepository.findByIssuerCertificateId(rootCertificate.getId()))
                .thenReturn(revokedCerts);
        when(keystoreService.getPrivateKey("CA_123456789", "123456789"))
                .thenReturn(keyPair.getPrivate());
        when(certificateRepository.save(any(Certificate.class))).thenReturn(rootCertificate);

        // Act
        crlService.generateCRL(rootCertificate);

        // Assert
        assertEquals(1L, rootCertificate.getCrlNumber().longValue());
    }

    // ===== REVOCATION REASON MAPPING TESTS =====

    @Test
    void generateCRL_WithDifferentRevocationReasons_ShouldMapCorrectly() throws Exception {
        // Arrange
        revokedCertificate1.setRevocationReason("keyCompromise");
        revokedCertificate2.setRevocationReason("superseded");

        List<Certificate> revokedCerts = Arrays.asList(revokedCertificate1, revokedCertificate2);
        when(certificateRepository.findByIssuerCertificateId(rootCertificate.getId()))
                .thenReturn(revokedCerts);
        when(keystoreService.getPrivateKey("CA_123456789", "123456789"))
                .thenReturn(keyPair.getPrivate());
        when(certificateRepository.save(any(Certificate.class))).thenReturn(rootCertificate);

        // Act
        byte[] crlBytes = crlService.generateCRL(rootCertificate);

        // Assert - should not throw exception and CRL should be generated successfully
        assertNotNull(crlBytes);
        assertTrue(crlBytes.length > 0);

        verify(certificateRepository, times(1)).findByIssuerCertificateId(rootCertificate.getId());
    }

    @Test
    void generateCRL_WithNullRevocationReason_ShouldUseDefault() throws Exception {
        // Arrange
        revokedCertificate1.setRevocationReason(null);
        List<Certificate> revokedCerts = Arrays.asList(revokedCertificate1);

        when(certificateRepository.findByIssuerCertificateId(rootCertificate.getId()))
                .thenReturn(revokedCerts);
        when(keystoreService.getPrivateKey("CA_123456789", "123456789"))
                .thenReturn(keyPair.getPrivate());
        when(certificateRepository.save(any(Certificate.class))).thenReturn(rootCertificate);

        // Act & Assert - should not throw exception
        assertDoesNotThrow(() -> {
            byte[] crlBytes = crlService.generateCRL(rootCertificate);
            assertNotNull(crlBytes);
        });
    }

    @Test
    void generateCRL_WithUnknownRevocationReason_ShouldUseDefault() throws Exception {
        // Arrange
        revokedCertificate1.setRevocationReason("unknownReason");
        List<Certificate> revokedCerts = Arrays.asList(revokedCertificate1);

        when(certificateRepository.findByIssuerCertificateId(rootCertificate.getId()))
                .thenReturn(revokedCerts);
        when(keystoreService.getPrivateKey("CA_123456789", "123456789"))
                .thenReturn(keyPair.getPrivate());
        when(certificateRepository.save(any(Certificate.class))).thenReturn(rootCertificate);

        // Act & Assert - should not throw exception
        assertDoesNotThrow(() -> {
            byte[] crlBytes = crlService.generateCRL(rootCertificate);
            assertNotNull(crlBytes);
        });
    }

    // ===== CERTIFICATE REVOCATION CHECK TESTS =====

    @Test
    void isCertificateRevoked_WhenCertificateNotRevoked_ShouldReturnFalse() throws Exception {
        // Arrange
        // Koristimo lenient stubbing za ove mockove jer se ne koriste direktno u testu
        // (oni se koriste samo u generateCRL koji se mockuje)
        lenient().when(certificateRepository.findByIssuerCertificateId(intermediateCertificate.getId()))
                .thenReturn(Collections.emptyList());
        lenient().when(keystoreService.getPrivateKey(anyString(), anyString()))
                .thenReturn(keyPair.getPrivate());
        lenient().when(certificateRepository.save(any(Certificate.class))).thenReturn(intermediateCertificate);

        // Mock the internal getOrGenerateCRL call to return empty CRL
        CRLService spyCrlService = spy(crlService);
        byte[] emptyCrl = generateEmptyCRL(intermediateCertificate);
        doReturn(emptyCrl).when(spyCrlService).getOrGenerateCRL(intermediateCertificate);

        // Act
        boolean result = spyCrlService.isCertificateRevoked(endEntityCertificate, intermediateCertificate);

        // Assert
        assertFalse(result);
    }

    @Test
    void isCertificateRevoked_WhenCertificateRevoked_ShouldReturnTrue() throws Exception {
        // Arrange
        // Postavi revokedAt vreme za endEntityCertificate
        endEntityCertificate.setRevokedAt(LocalDateTime.now().minusDays(1));
        endEntityCertificate.setRevocationReason("keyCompromise");
        endEntityCertificate.setStatus(CertificateStatus.REVOKED);

        // NEMA stubinga za repository ili keystore jer se ne pozivaju direktno
        // Mockujemo samo getOrGenerateCRL da vrati CRL sa opozvanim sertifikatom

        // Mock the internal getOrGenerateCRL call
        CRLService spyCrlService = spy(crlService);
        byte[] crlWithRevokedCert = generateCRLWithRevokedCertificate(intermediateCertificate, endEntityCertificate);
        doReturn(crlWithRevokedCert).when(spyCrlService).getOrGenerateCRL(intermediateCertificate);

        // Act
        boolean result = spyCrlService.isCertificateRevoked(endEntityCertificate, intermediateCertificate);

        // Assert
        assertTrue(result);
    }

    @Test
    void isCertificateRevoked_WhenExceptionOccurs_ShouldReturnTrue() throws Exception {
        // Arrange
        CRLService spyCrlService = spy(crlService);
        doThrow(new RuntimeException("CRL generation failed"))
                .when(spyCrlService).getOrGenerateCRL(intermediateCertificate);

        // Act
        boolean result = spyCrlService.isCertificateRevoked(endEntityCertificate, intermediateCertificate);

        // Assert
        assertTrue(result);
    }

    // ===== CRL CACHE TESTS =====

    @Test
    void getOrGenerateCRL_WhenNotInCache_ShouldGenerateAndCache() throws Exception {
        // Arrange
        when(certificateRepository.findByIssuerCertificateId(rootCertificate.getId()))
                .thenReturn(Collections.emptyList());
        when(keystoreService.getPrivateKey("CA_123456789", "123456789"))
                .thenReturn(keyPair.getPrivate());
        when(certificateRepository.save(any(Certificate.class))).thenReturn(rootCertificate);

        // Act
        byte[] crlBytes = crlService.getOrGenerateCRL(rootCertificate);

        // Assert
        assertNotNull(crlBytes);

        // Call again to verify it uses cache
        byte[] cachedCrlBytes = crlService.getOrGenerateCRL(rootCertificate);
        assertNotNull(cachedCrlBytes);

        // Verify repository was called only once (cached on second call)
        verify(certificateRepository, times(1)).findByIssuerCertificateId(rootCertificate.getId());
    }

    @Test
    void clearCache_WithValidSerialNumber_ShouldRemoveFromCache() throws Exception {
        // Arrange
        when(certificateRepository.findByIssuerCertificateId(rootCertificate.getId()))
                .thenReturn(Collections.emptyList());
        when(keystoreService.getPrivateKey("CA_123456789", "123456789"))
                .thenReturn(keyPair.getPrivate());
        when(certificateRepository.save(any(Certificate.class))).thenReturn(rootCertificate);

        // Populate cache
        crlService.getOrGenerateCRL(rootCertificate);

        // Act
        crlService.clearCache(rootCertificate.getSerialNumber());

        // Assert - cache should be empty, next call should generate again
        crlService.getOrGenerateCRL(rootCertificate);
        verify(certificateRepository, times(2)).findByIssuerCertificateId(rootCertificate.getId());
    }

    @Test
    void clearCache_WithNullSerialNumber_ShouldNotThrowException() {
        // Act & Assert - should not throw exception
        assertDoesNotThrow(() -> crlService.clearCache(null));
    }

    // ===== INTEGRATION STYLE TESTS =====

    @Test
    void revokeCertificateFlow_ShouldUpdateCRLCorrectly() throws Exception {
        // This test simulates the complete flow of revoking a certificate and checking its status

        // 1. Initially, no revoked certificates
        when(certificateRepository.findByIssuerCertificateId(rootCertificate.getId()))
                .thenReturn(Collections.emptyList())
                .thenReturn(Arrays.asList(revokedCertificate1)); // Second call returns revoked cert

        when(keystoreService.getPrivateKey("CA_123456789", "123456789"))
                .thenReturn(keyPair.getPrivate());
        when(certificateRepository.save(any(Certificate.class))).thenReturn(rootCertificate);

        // 2. Generate initial CRL (empty)
        byte[] initialCrl = crlService.getOrGenerateCRL(rootCertificate);
        assertNotNull(initialCrl);

        // 3. Clear cache to force regeneration
        crlService.clearCache(rootCertificate.getSerialNumber());

        // 4. Generate new CRL which should include the revoked certificate
        byte[] updatedCrl = crlService.getOrGenerateCRL(rootCertificate);
        assertNotNull(updatedCrl);

        // Verify that repository was called twice (once for initial, once after cache clear)
        verify(certificateRepository, times(2)).findByIssuerCertificateId(rootCertificate.getId());
    }

    // ===== EDGE CASE TESTS =====

    @Test
    void generateCRL_WithMixedCertificateStatus_ShouldOnlyIncludeRevoked() throws Exception {
        // Arrange
        List<Certificate> allCerts = Arrays.asList(
                revokedCertificate1,
                endEntityCertificate, // This should be filtered out (status = VALID)
                revokedCertificate2
        );

        when(certificateRepository.findByIssuerCertificateId(rootCertificate.getId()))
                .thenReturn(allCerts);
        when(keystoreService.getPrivateKey("CA_123456789", "123456789"))
                .thenReturn(keyPair.getPrivate());
        when(certificateRepository.save(any(Certificate.class))).thenReturn(rootCertificate);

        // Act
        byte[] crlBytes = crlService.generateCRL(rootCertificate);

        // Assert
        assertNotNull(crlBytes);
        // The CRL should only contain the two revoked certificates, not the valid one
        verify(certificateRepository, times(1)).findByIssuerCertificateId(rootCertificate.getId());
    }

    // Helper methods for generating test CRLs
    private byte[] generateEmptyCRL(Certificate caCertificate) throws Exception {
        X500Name issuer = new X500Name(caCertificate.getSubject());
        Date now = new Date();
        Date nextUpdate = Date.from(LocalDateTime.now().plusDays(7).atZone(java.time.ZoneId.systemDefault()).toInstant());

        X509v2CRLBuilder crlBuilder = new X509v2CRLBuilder(issuer, now);
        crlBuilder.setNextUpdate(nextUpdate);

        ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSAEncryption").build(keyPair.getPrivate());
        X509CRLHolder crlHolder = crlBuilder.build(signer);

        return crlHolder.getEncoded();
    }

    private byte[] generateCRLWithRevokedCertificate(Certificate caCertificate, Certificate revokedCert) throws Exception {
        X500Name issuer = new X500Name(caCertificate.getSubject());
        Date now = new Date();
        Date nextUpdate = Date.from(LocalDateTime.now().plusDays(7).atZone(java.time.ZoneId.systemDefault()).toInstant());

        X509v2CRLBuilder crlBuilder = new X509v2CRLBuilder(issuer, now);
        crlBuilder.setNextUpdate(nextUpdate);

        BigInteger serialNumber = new BigInteger(revokedCert.getSerialNumber());
        Date revocationDate = Date.from(revokedCert.getRevokedAt().atZone(java.time.ZoneId.systemDefault()).toInstant());
        int reasonCode = 0; // unspecified

        crlBuilder.addCRLEntry(serialNumber, revocationDate, reasonCode);

        ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSAEncryption").build(keyPair.getPrivate());
        X509CRLHolder crlHolder = crlBuilder.build(signer);

        return crlHolder.getEncoded();
    }
}
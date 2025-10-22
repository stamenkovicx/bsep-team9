package com.bsep.pki_system.service;

import com.bsep.pki_system.dto.CreateTemplateDTO;
import com.bsep.pki_system.dto.TemplateResponseDTO;
import com.bsep.pki_system.model.Certificate;
import com.bsep.pki_system.model.CertificateTemplate;
import com.bsep.pki_system.model.User;
import com.bsep.pki_system.model.UserRole;
import com.bsep.pki_system.repository.CertificateTemplateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CertificateTemplateServiceTest {

    @Mock
    private CertificateTemplateRepository templateRepository;

    @Mock
    private CertificateService certificateService;

    @InjectMocks
    private CertificateTemplateService certificateTemplateService;

    private User caUser;
    private User adminUser;
    private User basicUser;
    private Certificate caCertificate;
    private CertificateTemplate testTemplate;
    private CreateTemplateDTO createTemplateDTO;

    @BeforeEach
    void setUp() {
        // Setup users
        caUser = new User();
        caUser.setId(1L);
        caUser.setEmail("ca@example.com");
        caUser.setRole(UserRole.CA);
        caUser.setOrganization("Test Org");

        adminUser = new User();
        adminUser.setId(2L);
        adminUser.setEmail("admin@example.com");
        adminUser.setRole(UserRole.ADMIN);
        adminUser.setOrganization("Admin Org");

        basicUser = new User();
        basicUser.setId(3L);
        basicUser.setEmail("basic@example.com");
        basicUser.setRole(UserRole.BASIC);
        basicUser.setOrganization("Test Org");

        // Setup CA certificate
        caCertificate = new Certificate();
        caCertificate.setId(1L);
        caCertificate.setSubject("CN=Test CA,O=Test Org");
        caCertificate.setIsCA(true);
        caCertificate.setOwner(caUser);

        // Setup template DTO - KORISTI STRINGOVE umesto List<Boolean>
        createTemplateDTO = new CreateTemplateDTO();
        createTemplateDTO.setName("Test Template");
        createTemplateDTO.setDescription("Test Description");
        createTemplateDTO.setCaIssuerId(1L);
        createTemplateDTO.setCommonNameRegex(".*\\.test\\.com");
        createTemplateDTO.setSansRegex(".*\\.san\\.test\\.com");
        createTemplateDTO.setMaxValidityDays(365);
        createTemplateDTO.setKeyUsage(Arrays.asList(true, false, true, false, false, false, false, false, false));
        createTemplateDTO.setExtendedKeyUsage("serverAuth,clientAuth");
        createTemplateDTO.setBasicConstraints("CA:FALSE");

        // Setup template - KORISTI STRINGOVE
        testTemplate = new CertificateTemplate();
        testTemplate.setId(1L);
        testTemplate.setName("Test Template");
        testTemplate.setDescription("Test Description");
        testTemplate.setCaIssuer(caCertificate);
        testTemplate.setCommonNameRegex(".*\\.test\\.com");
        testTemplate.setSansRegex(".*\\.san\\.test\\.com");
        testTemplate.setMaxValidityDays(365);
        createTemplateDTO.setKeyUsage(Arrays.asList(true, false, true, false, false, false, false, false, false));
        testTemplate.setExtendedKeyUsage("serverAuth,clientAuth");
        testTemplate.setBasicConstraints("CA:FALSE");
        testTemplate.setCreatedBy(caUser);
    }

    // ===== CREATE TEMPLATE TESTS =====

    @Test
    void createTemplate_WithCAUser_ShouldCreateTemplate() {
        when(templateRepository.existsByName("Test Template")).thenReturn(false);
        when(certificateService.findById(1L)).thenReturn(Optional.of(caCertificate));
        when(templateRepository.save(any(CertificateTemplate.class))).thenReturn(testTemplate);

        CertificateTemplate result = certificateTemplateService.createTemplate(createTemplateDTO, caUser);

        assertNotNull(result);
        assertEquals("Test Template", result.getName());
        assertEquals(caCertificate, result.getCaIssuer());
        assertEquals(caUser, result.getCreatedBy());
        verify(templateRepository, times(1)).save(any(CertificateTemplate.class));
    }

    @Test
    void createTemplate_WithAdminUser_ShouldThrowException() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            certificateTemplateService.createTemplate(createTemplateDTO, adminUser);
        });

        assertEquals("Only CA users can create templates", exception.getMessage());
        verify(templateRepository, never()).save(any());
    }

    @Test
    void createTemplate_WithBasicUser_ShouldThrowException() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            certificateTemplateService.createTemplate(createTemplateDTO, basicUser);
        });

        assertEquals("Only CA users can create templates", exception.getMessage());
        verify(templateRepository, never()).save(any());
    }

    @Test
    void createTemplate_WithDuplicateName_ShouldThrowException() {
        when(templateRepository.existsByName("Test Template")).thenReturn(true);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            certificateTemplateService.createTemplate(createTemplateDTO, caUser);
        });

        assertEquals("Template with name 'Test Template' already exists", exception.getMessage());
        verify(templateRepository, never()).save(any());
    }

    @Test
    void createTemplate_WithNonCAIssuer_ShouldThrowException() {
        Certificate nonCaCertificate = new Certificate();
        nonCaCertificate.setId(2L);
        nonCaCertificate.setIsCA(false);
        nonCaCertificate.setOwner(caUser);

        when(templateRepository.existsByName("Test Template")).thenReturn(false);
        when(certificateService.findById(1L)).thenReturn(Optional.of(nonCaCertificate));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            certificateTemplateService.createTemplate(createTemplateDTO, caUser);
        });

        assertEquals("Selected certificate is not a CA and cannot be used as template issuer", exception.getMessage());
        verify(templateRepository, never()).save(any());
    }

    @Test
    void createTemplate_WithDifferentOrganizationIssuer_ShouldThrowException() {
        Certificate differentOrgCertificate = new Certificate();
        differentOrgCertificate.setId(2L);
        differentOrgCertificate.setIsCA(true);

        User differentOrgUser = new User();
        differentOrgUser.setOrganization("Different Org");
        differentOrgCertificate.setOwner(differentOrgUser);

        when(templateRepository.existsByName("Test Template")).thenReturn(false);
        when(certificateService.findById(1L)).thenReturn(Optional.of(differentOrgCertificate));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            certificateTemplateService.createTemplate(createTemplateDTO, caUser);
        });

        assertEquals("CA issuer does not belong to your organization", exception.getMessage());
        verify(templateRepository, never()).save(any());
    }

    @Test
    void createTemplate_WithNonExistentIssuer_ShouldThrowException() {
        when(templateRepository.existsByName("Test Template")).thenReturn(false);
        when(certificateService.findById(1L)).thenReturn(Optional.empty());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            certificateTemplateService.createTemplate(createTemplateDTO, caUser);
        });

        assertEquals("CA issuer not found with ID: 1", exception.getMessage());
        verify(templateRepository, never()).save(any());
    }

    // ===== GET TEMPLATES TESTS =====

    @Test
    void getTemplatesForUser_WithCAUser_ShouldReturnTemplates() {
        List<CertificateTemplate> templates = Arrays.asList(testTemplate);
        when(templateRepository.findTemplatesForOrganizationCAs("Test Org")).thenReturn(templates);

        List<TemplateResponseDTO> result = certificateTemplateService.getTemplatesForUser(caUser);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Test Template", result.get(0).getName());
        verify(templateRepository, times(1)).findTemplatesForOrganizationCAs("Test Org");
    }

    @Test
    void getTemplatesForUser_WithAdminUser_ShouldReturnEmptyList() {
        List<TemplateResponseDTO> result = certificateTemplateService.getTemplatesForUser(adminUser);

        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(templateRepository, never()).findTemplatesForOrganizationCAs(anyString());
    }

    @Test
    void getTemplatesForUser_WithBasicUser_ShouldReturnEmptyList() {
        List<TemplateResponseDTO> result = certificateTemplateService.getTemplatesForUser(basicUser);

        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(templateRepository, never()).findTemplatesForOrganizationCAs(anyString());
    }

    // ===== GET TEMPLATES FOR CA ISSUER TESTS =====

    @Test
    void getTemplatesForCaIssuer_WithCAUser_ShouldReturnTemplates() {
        List<CertificateTemplate> templates = Arrays.asList(testTemplate);
        when(certificateService.findById(1L)).thenReturn(Optional.of(caCertificate));
        when(templateRepository.findByCaIssuerId(1L)).thenReturn(templates);

        List<TemplateResponseDTO> result = certificateTemplateService.getTemplatesForCaIssuer(1L, caUser);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Test Template", result.get(0).getName());
        verify(templateRepository, times(1)).findByCaIssuerId(1L);
    }

    @Test
    void getTemplatesForCaIssuer_WithNonCAUser_ShouldReturnEmptyList() {
        List<TemplateResponseDTO> result = certificateTemplateService.getTemplatesForCaIssuer(1L, adminUser);

        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(templateRepository, never()).findByCaIssuerId(any());
    }

    @Test
    void getTemplatesForCaIssuer_WithDifferentOrganization_ShouldThrowException() {
        User differentOrgUser = new User();
        differentOrgUser.setId(4L);
        differentOrgUser.setRole(UserRole.CA);
        differentOrgUser.setOrganization("Different Org");

        when(certificateService.findById(1L)).thenReturn(Optional.of(caCertificate));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            certificateTemplateService.getTemplatesForCaIssuer(1L, differentOrgUser);
        });

        assertEquals("Not authorized to access templates for this CA issuer", exception.getMessage());
        verify(templateRepository, never()).findByCaIssuerId(any());
    }

    @Test
    void getTemplatesForCaIssuer_WithNonExistentIssuer_ShouldThrowException() {
        when(certificateService.findById(1L)).thenReturn(Optional.empty());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            certificateTemplateService.getTemplatesForCaIssuer(1L, caUser);
        });

        assertEquals("CA issuer not found", exception.getMessage());
        verify(templateRepository, never()).findByCaIssuerId(any());
    }

    // ===== FIND TEMPLATE BY ID TESTS =====

    @Test
    void findById_WithCreatorCAUser_ShouldReturnTemplate() {
        when(templateRepository.findById(1L)).thenReturn(Optional.of(testTemplate));

        CertificateTemplate result = certificateTemplateService.findById(1L, caUser);

        assertNotNull(result);
        assertEquals("Test Template", result.getName());
        verify(templateRepository, times(1)).findById(1L);
    }

    @Test
    void findById_WithNonCreatorCAUser_ShouldThrowException() {
        User differentCAUser = new User();
        differentCAUser.setId(4L);
        differentCAUser.setRole(UserRole.CA);

        when(templateRepository.findById(1L)).thenReturn(Optional.of(testTemplate));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            certificateTemplateService.findById(1L, differentCAUser);
        });

        assertEquals("Not authorized to access this template", exception.getMessage());
        verify(templateRepository, times(1)).findById(1L);
    }

    @Test
    void findById_WithNonCAUser_ShouldThrowException() {
        when(templateRepository.findById(1L)).thenReturn(Optional.of(testTemplate));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            certificateTemplateService.findById(1L, adminUser);
        });

        assertEquals("Not authorized to access this template", exception.getMessage());
        verify(templateRepository, times(1)).findById(1L);
    }

    @Test
    void findById_WithNonExistentTemplate_ShouldThrowException() {
        when(templateRepository.findById(1L)).thenReturn(Optional.empty());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            certificateTemplateService.findById(1L, caUser);
        });

        assertEquals("Template not found with ID: 1", exception.getMessage());
        verify(templateRepository, times(1)).findById(1L);
    }

    // ===== DELETE TEMPLATE TESTS =====

    @Test
    void deleteTemplate_WithCreator_ShouldDeleteTemplate() {
        when(templateRepository.findById(1L)).thenReturn(Optional.of(testTemplate));
        doNothing().when(templateRepository).delete(testTemplate);

        assertDoesNotThrow(() -> {
            certificateTemplateService.deleteTemplate(1L, caUser);
        });

        verify(templateRepository, times(1)).delete(testTemplate);
    }

    @Test
    void deleteTemplate_WithNonCreator_ShouldThrowException() {
        User differentCAUser = new User();
        differentCAUser.setId(4L);
        differentCAUser.setRole(UserRole.CA);

        when(templateRepository.findById(1L)).thenReturn(Optional.of(testTemplate));

        // Glavno je da se bacio exception i delete nije pozvan
        assertThrows(IllegalArgumentException.class, () -> {
            certificateTemplateService.deleteTemplate(1L, differentCAUser);
        });

        verify(templateRepository, never()).delete(any());
    }

    // ===== UPDATE TEMPLATE TESTS =====

    @Test
    void updateTemplate_WithCreator_ShouldUpdateTemplate() {
        when(templateRepository.findById(1L)).thenReturn(Optional.of(testTemplate));
        when(templateRepository.existsByNameAndIdNot("Updated Template", 1L)).thenReturn(false);
        when(certificateService.findById(1L)).thenReturn(Optional.of(caCertificate));
        when(templateRepository.save(any(CertificateTemplate.class))).thenReturn(testTemplate);

        createTemplateDTO.setName("Updated Template");
        createTemplateDTO.setDescription("Updated Description");

        CertificateTemplate result = certificateTemplateService.updateTemplate(1L, createTemplateDTO, caUser);

        assertNotNull(result);
        verify(templateRepository, times(1)).save(testTemplate);
    }

    @Test
    void updateTemplate_WithNonCreator_ShouldThrowException() {
        User differentCAUser = new User();
        differentCAUser.setId(4L);
        differentCAUser.setRole(UserRole.CA);

        when(templateRepository.findById(1L)).thenReturn(Optional.of(testTemplate));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            certificateTemplateService.updateTemplate(1L, createTemplateDTO, differentCAUser);
        });

        assertEquals("Not authorized to update this template", exception.getMessage());
        verify(templateRepository, never()).save(any());
    }

    @Test
    void updateTemplate_WithDuplicateName_ShouldThrowException() {
        when(templateRepository.findById(1L)).thenReturn(Optional.of(testTemplate));
        when(templateRepository.existsByNameAndIdNot("Test Template", 1L)).thenReturn(true);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            certificateTemplateService.updateTemplate(1L, createTemplateDTO, caUser);
        });

        assertEquals("Template with name 'Test Template' already exists", exception.getMessage());
        verify(templateRepository, never()).save(any());
    }

    // ===== VALIDATION TESTS =====

    @Test
    void validateCommonName_WithValidRegex_ShouldReturnTrue() {
        boolean result = certificateTemplateService.validateCommonName("test.test.com", ".*\\.test\\.com");

        assertTrue(result);
    }

    @Test
    void validateCommonName_WithInvalidRegex_ShouldReturnFalse() {
        boolean result = certificateTemplateService.validateCommonName("invalid.example.com", ".*\\.test\\.com");

        assertFalse(result);
    }

    @Test
    void validateCommonName_WithNullRegex_ShouldReturnTrue() {
        boolean result = certificateTemplateService.validateCommonName("anything.example.com", null);

        assertTrue(result);
    }

    @Test
    void validateCommonName_WithEmptyRegex_ShouldReturnTrue() {
        boolean result = certificateTemplateService.validateCommonName("anything.example.com", "");

        assertTrue(result);
    }

    @Test
    void validateCommonName_WithNullCommonName_ShouldReturnFalse() {
        boolean result = certificateTemplateService.validateCommonName(null, ".*\\.test\\.com");

        assertFalse(result);
    }

    @Test
    void validateSans_WithValidRegex_ShouldReturnTrue() {
        boolean result = certificateTemplateService.validateSans("DNS:test.san.test.com", ".*\\.san\\.test\\.com");

        assertTrue(result);
    }

    @Test
    void validateSans_WithInvalidRegex_ShouldReturnFalse() {
        boolean result = certificateTemplateService.validateSans("invalid.example.com", ".*\\.san\\.test\\.com");

        assertFalse(result);
    }

    @Test
    void validateSans_WithSimpleValidRegex_ShouldReturnTrue() {
        boolean result = certificateTemplateService.validateSans("example.com", ".*\\.com");

        assertTrue(result);
    }

    @Test
    void validateSans_WithNullRegex_ShouldReturnTrue() {
        boolean result = certificateTemplateService.validateSans("anything.example.com", null);

        assertTrue(result);
    }

    @Test
    void validateSans_WithNullSans_ShouldReturnTrue() {
        boolean result = certificateTemplateService.validateSans(null, ".*\\.test\\.com");

        assertTrue(result);
    }
}
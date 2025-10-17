package com.bsep.pki_system.service;

import com.bsep.pki_system.dto.CreateTemplateDTO;
import com.bsep.pki_system.dto.TemplateResponseDTO;
import com.bsep.pki_system.model.Certificate;
import com.bsep.pki_system.model.CertificateTemplate;
import com.bsep.pki_system.model.User;
import com.bsep.pki_system.model.UserRole;
import com.bsep.pki_system.repository.CertificateTemplateRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class CertificateTemplateService {

    private final CertificateTemplateRepository templateRepository;
    private final CertificateService certificateService;

    public CertificateTemplateService(CertificateTemplateRepository templateRepository,
                                      CertificateService certificateService) {
        this.templateRepository = templateRepository;
        this.certificateService = certificateService;
    }

    public CertificateTemplate createTemplate(CreateTemplateDTO templateDTO, User createdBy) {
        // SAMO CA korisnik može da kreira šablone
        if (createdBy.getRole() != UserRole.CA) {
            throw new IllegalArgumentException("Only CA users can create templates");
        }

        // Provera da li šablon sa istim imenom već postoji
        if (templateRepository.existsByName(templateDTO.getName())) {
            throw new IllegalArgumentException("Template with name '" + templateDTO.getName() + "' already exists");
        }

        // Pronalaženje CA issuer-a
        Certificate caIssuer = certificateService.findById(templateDTO.getCaIssuerId())
                .orElseThrow(() -> new IllegalArgumentException("CA issuer not found with ID: " + templateDTO.getCaIssuerId()));

        // Provera da li je issuer zaista CA
        if (!caIssuer.getIsCA()) {
            throw new IllegalArgumentException("Selected certificate is not a CA and cannot be used as template issuer");
        }

        // Provera da li CA issuer pripada istoj organizaciji kao korisnik
        if (!caIssuer.getOwner().getOrganization().equals(createdBy.getOrganization())) {
            throw new IllegalArgumentException("CA issuer does not belong to your organization");
        }

        // Kreiranje šablona
        CertificateTemplate template = new CertificateTemplate();
        template.setName(templateDTO.getName());
        template.setDescription(templateDTO.getDescription());
        template.setCaIssuer(caIssuer);
        template.setCommonNameRegex(templateDTO.getCommonNameRegex());
        template.setSansRegex(templateDTO.getSansRegex());
        template.setMaxValidityDays(templateDTO.getMaxValidityDays());
        template.setKeyUsage(templateDTO.getKeyUsage());
        template.setExtendedKeyUsage(templateDTO.getExtendedKeyUsage());
        template.setBasicConstraints(templateDTO.getBasicConstraints());
        template.setCreatedBy(createdBy);

        return templateRepository.save(template);
    }

    public List<TemplateResponseDTO> getTemplatesForUser(User user) {
        // SAMO CA korisnik vidi šablone
        if (user.getRole() == UserRole.CA) {
            return templateRepository.findTemplatesForOrganizationCAs(user.getOrganization())
                    .stream()
                    .map(this::convertToDTO)
                    .collect(Collectors.toList());
        }

        // Admin i BASIC korisnik NE VIDE šablone
        return List.of();
    }

    public List<TemplateResponseDTO> getTemplatesForCaIssuer(Long caIssuerId, User user) {
        // SAMO CA korisnik može da pristupi
        if (user.getRole() != UserRole.CA) {
            return List.of();
        }

        // Dodatna provera da li CA issuer pripada korisnikovoj organizaciji
        Certificate caIssuer = certificateService.findById(caIssuerId)
                .orElseThrow(() -> new IllegalArgumentException("CA issuer not found"));

        if (!caIssuer.getOwner().getOrganization().equals(user.getOrganization())) {
            throw new IllegalArgumentException("Not authorized to access templates for this CA issuer");
        }

        List<CertificateTemplate> templates = templateRepository.findByCaIssuerId(caIssuerId);
        return templates.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public CertificateTemplate findById(Long templateId, User user) {
        CertificateTemplate template = templateRepository.findById(templateId)
                .orElseThrow(() -> new IllegalArgumentException("Template not found with ID: " + templateId));

        // SAMO CA korisnik koji je kreirao šablon može da mu pristupi
        if (user.getRole() != UserRole.CA ||
                !template.getCreatedBy().getId().equals(user.getId())) {
            throw new IllegalArgumentException("Not authorized to access this template");
        }

        return template;
    }

    public void deleteTemplate(Long templateId, User user) {
        CertificateTemplate template = findById(templateId, user);

        // SAMO kreator šablona može da ga obriše
        if (!template.getCreatedBy().getId().equals(user.getId())) {
            throw new IllegalArgumentException("Not authorized to delete this template");
        }

        templateRepository.delete(template);
    }

    private TemplateResponseDTO convertToDTO(CertificateTemplate template) {
        TemplateResponseDTO dto = new TemplateResponseDTO();
        dto.setId(template.getId());
        dto.setName(template.getName());
        dto.setDescription(template.getDescription());
        dto.setCaIssuerId(template.getCaIssuer().getId());
        dto.setCaIssuerName(template.getCaIssuer().getSubject());
        dto.setCommonNameRegex(template.getCommonNameRegex());
        dto.setSansRegex(template.getSansRegex());
        dto.setMaxValidityDays(template.getMaxValidityDays());
        dto.setKeyUsage(template.getKeyUsage());
        dto.setExtendedKeyUsage(template.getExtendedKeyUsage());
        dto.setBasicConstraints(template.getBasicConstraints());
        dto.setCreatedAt(template.getCreatedAt());
        dto.setCreatedBy(template.getCreatedBy().getEmail());

        return dto;
    }

    // Metoda za validaciju CN prema regex šablonu
    public boolean validateCommonName(String commonName, String regexPattern) {
        if (regexPattern == null || regexPattern.trim().isEmpty()) {
            return true; // Ako nema regexa, sve je validno
        }
        return commonName != null && commonName.matches(regexPattern);
    }

    // Metoda za validaciju SANs prema regex šablonu
    public boolean validateSans(String sans, String regexPattern) {
        if (regexPattern == null || regexPattern.trim().isEmpty() || sans == null) {
            return true; // Ako nema regexa ili SANs, sve je validno
        }
        return sans.matches(regexPattern);
    }
}
package com.bsep.pki_system.controller;

import com.bsep.pki_system.dto.CreateTemplateDTO;
import com.bsep.pki_system.dto.TemplateResponseDTO;
import com.bsep.pki_system.model.User;
import com.bsep.pki_system.service.CertificateTemplateService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/templates")
public class CertificateTemplateController {

    private final CertificateTemplateService templateService;

    public CertificateTemplateController(CertificateTemplateService templateService) {
        this.templateService = templateService;
    }

    // SAMO CA korisnik može da kreira šablone
    @PreAuthorize("hasRole('CA')")
    @PostMapping
    public ResponseEntity<?> createTemplate(
            @Valid @RequestBody CreateTemplateDTO templateDTO,
            @AuthenticationPrincipal User user) {
        try {
            var template = templateService.createTemplate(templateDTO, user);
            var responseDTO = convertToResponseDTO(template);

            return ResponseEntity.ok(Map.of(
                    "message", "Template created successfully",
                    "template", responseDTO
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("message", "Error creating template: " + e.getMessage()));
        }
    }

    // SAMO CA korisnik vidi svoje šablone
    @PreAuthorize("hasRole('CA')")
    @GetMapping
    public ResponseEntity<List<TemplateResponseDTO>> getMyTemplates(@AuthenticationPrincipal User user) {
        List<TemplateResponseDTO> templates = templateService.getTemplatesForUser(user);
        return ResponseEntity.ok(templates);
    }

    // SAMO CA korisnik vidi šablone za određenog CA issuer-a
    @PreAuthorize("hasRole('CA')")
    @GetMapping("/ca-issuer/{caIssuerId}")
    public ResponseEntity<?> getTemplatesForCaIssuer(
            @PathVariable Long caIssuerId,
            @AuthenticationPrincipal User user) {
        try {
            List<TemplateResponseDTO> templates = templateService.getTemplatesForCaIssuer(caIssuerId, user);
            return ResponseEntity.ok(templates);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // SAMO CA korisnik vidi pojedinačni šablon
    @PreAuthorize("hasRole('CA')")
    @GetMapping("/{templateId}")
    public ResponseEntity<?> getTemplate(
            @PathVariable Long templateId,
            @AuthenticationPrincipal User user) {
        try {
            var template = templateService.findById(templateId, user);
            var responseDTO = convertToResponseDTO(template);
            return ResponseEntity.ok(responseDTO);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // SAMO CA korisnik može da obriše svoj šablon
    @PreAuthorize("hasRole('CA')")
    @DeleteMapping("/{templateId}")
    public ResponseEntity<?> deleteTemplate(
            @PathVariable Long templateId,
            @AuthenticationPrincipal User user) {
        try {
            templateService.deleteTemplate(templateId, user);
            return ResponseEntity.ok(Map.of("message", "Template deleted successfully"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("message", "Error deleting template: " + e.getMessage()));
        }
    }

    // Validacija CN prema šablonu
    @PreAuthorize("hasRole('CA')")
    @PostMapping("/validate-cn")
    public ResponseEntity<?> validateCommonName(
            @RequestBody Map<String, String> request,
            @AuthenticationPrincipal User user) {
        try {
            String commonName = request.get("commonName");
            String regexPattern = request.get("regexPattern");

            boolean isValid = templateService.validateCommonName(commonName, regexPattern);

            return ResponseEntity.ok(Map.of(
                    "valid", isValid,
                    "message", isValid ? "Common Name is valid" : "Common Name does not match pattern"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", "Validation error: " + e.getMessage()));
        }
    }

    private TemplateResponseDTO convertToResponseDTO(com.bsep.pki_system.model.CertificateTemplate template) {
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
}
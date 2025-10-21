package com.bsep.pki_system.controller;

import com.bsep.pki_system.dto.CreateTemplateDTO;
import com.bsep.pki_system.dto.TemplateResponseDTO;
import com.bsep.pki_system.jwt.UserPrincipal;
import com.bsep.pki_system.model.CertificateTemplate;
import com.bsep.pki_system.model.User;
import com.bsep.pki_system.service.CertificateTemplateService;
import com.bsep.pki_system.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.bsep.pki_system.audit.AuditLogService;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/templates")
public class CertificateTemplateController {

    private final CertificateTemplateService templateService;
    private final UserService userService;
    private final AuditLogService auditLogService;

    public CertificateTemplateController(CertificateTemplateService templateService,
                                         UserService userService,
                                         AuditLogService auditLogService) {
        this.templateService = templateService;
        this.userService = userService;
        this.auditLogService = auditLogService;
    }

    // SAMO CA korisnik može da kreira šablone
    @PreAuthorize("hasRole('CA')")
    @PostMapping
    public ResponseEntity<?> createTemplate(
            @Valid @RequestBody CreateTemplateDTO templateDTO,
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            HttpServletRequest httpRequest) {
        try {
            // Pronalazimo kompletan User objekat da bismo znali njegovu organizaciju
            User user = userService.findByEmail(userPrincipal.getEmail())
                    .orElseThrow(() -> new RuntimeException("Authenticated user not found"));

            var template = templateService.createTemplate(templateDTO, user);
            var responseDTO = convertToResponseDTO(template);

            // AUDIT LOG: Šablon kreiran
            auditLogService.logSecurityEvent(AuditLogService.EVENT_TEMPLATE_CREATED,
                    "Certificate template created", true,
                    "templateId=" + template.getId() + ", name=" + template.getName() +
                            ", caIssuer=" + template.getCaIssuer().getSubject(), httpRequest);


            return ResponseEntity.ok(Map.of(
                    "message", "Template created successfully",
                    "template", responseDTO
            ));
        } catch (IllegalArgumentException e) {
            // AUDIT LOG: Neuspešno kreiranje šablona
            auditLogService.logSecurityEvent(AuditLogService.EVENT_TEMPLATE_CREATED,
                    "Template creation failed", false,
                    "error=" + e.getMessage(), httpRequest);

            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            // AUDIT LOG: Greška pri kreiranju šablona
            auditLogService.logSecurityEvent(AuditLogService.EVENT_TEMPLATE_CREATED,
                    "Template creation error", false,
                    "error=" + e.getMessage(), httpRequest);

            return ResponseEntity.status(500).body(Map.of("message", "Error creating template: " + e.getMessage()));
        }
    }

    // SAMO CA korisnik vidi svoje šablone
    @PreAuthorize("hasRole('CA')")
    @GetMapping
    public ResponseEntity<List<TemplateResponseDTO>> getMyTemplates(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        // Pronalazimo kompletan User objekat da bismo znali njegovu organizaciju
        User user = userService.findByEmail(userPrincipal.getEmail())
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));

        List<TemplateResponseDTO> templates = templateService.getTemplatesForUser(user);
        return ResponseEntity.ok(templates);
    }

    // SAMO CA korisnik vidi šablone za određenog CA issuer-a
    @PreAuthorize("hasRole('CA')")
    @GetMapping("/ca-issuer/{caIssuerId}")
    public ResponseEntity<?> getTemplatesForCaIssuer(
            @PathVariable Long caIssuerId,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        try {
            User user = userService.findByEmail(userPrincipal.getEmail())
                    .orElseThrow(() -> new RuntimeException("Authenticated user not found"));

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
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            HttpServletRequest httpRequest) {
        try {
            User user = userService.findByEmail(userPrincipal.getEmail())
                    .orElseThrow(() -> new RuntimeException("Authenticated user not found"));

            var template = templateService.findById(templateId, user);
            var responseDTO = convertToResponseDTO(template);

            // AUDIT LOG: Pristup šablonu
            auditLogService.logSecurityEvent(AuditLogService.EVENT_TEMPLATE_ACCESSED,
                    "Certificate template accessed", true,
                    "templateId=" + templateId + ", name=" + template.getName(), httpRequest);

            return ResponseEntity.ok(responseDTO);
        } catch (IllegalArgumentException e) {
            // AUDIT LOG: Neovlašćen pristup šablonu
            auditLogService.logSecurityEvent(AuditLogService.EVENT_TEMPLATE_ACCESSED,
                    "Unauthorized template access", false,
                    "templateId=" + templateId + ", error=" + e.getMessage(), httpRequest);

            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // SAMO CA korisnik može da obriše svoj šablon
    @PreAuthorize("hasRole('CA')")
    @DeleteMapping("/{templateId}")
    public ResponseEntity<?> deleteTemplate(
            @PathVariable Long templateId,
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            HttpServletRequest httpRequest) {
        try {
            User user = userService.findByEmail(userPrincipal.getEmail())
                    .orElseThrow(() -> new RuntimeException("Authenticated user not found"));

            // Dohvati šablon pre brisanja za audit log
            var template = templateService.findById(templateId, user);

            templateService.deleteTemplate(templateId, user);

            // AUDIT LOG: Šablon obrisan
            auditLogService.logSecurityEvent(AuditLogService.EVENT_TEMPLATE_DELETED,
                    "Certificate template deleted", true,
                    "templateId=" + templateId + ", name=" + template.getName(), httpRequest);

            return ResponseEntity.ok(Map.of("message", "Template deleted successfully"));
        } catch (IllegalArgumentException e) {
            // AUDIT LOG: Neuspešno brisanje šablona
            auditLogService.logSecurityEvent(AuditLogService.EVENT_TEMPLATE_DELETED,
                    "Template deletion failed", false,
                    "templateId=" + templateId + ", error=" + e.getMessage(), httpRequest);

            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            // AUDIT LOG: Greška pri brisanju šablona
            auditLogService.logSecurityEvent(AuditLogService.EVENT_TEMPLATE_DELETED,
                    "Template deletion error", false,
                    "templateId=" + templateId + ", error=" + e.getMessage(), httpRequest);

            return ResponseEntity.status(500).body(Map.of("message", "Error deleting template: " + e.getMessage()));
        }
    }

    // Validacija CN prema šablonu
    @PreAuthorize("hasRole('CA')")
    @PostMapping("/validate-cn")
    public ResponseEntity<?> validateCommonName(
            @RequestBody Map<String, String> request,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        try {
            User user = userService.findByEmail(userPrincipal.getEmail())
                    .orElseThrow(() -> new RuntimeException("Authenticated user not found"));

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

    @PreAuthorize("hasRole('CA')")
    @PostMapping("/{templateId}/use")
    public ResponseEntity<?> useTemplate(
            @PathVariable Long templateId,
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            HttpServletRequest httpRequest) {

        try {
            User user = userService.findByEmail(userPrincipal.getEmail())
                    .orElseThrow(() -> new RuntimeException("Authenticated user not found"));

            CertificateTemplate template = templateService.findById(templateId, user);

            // AUDIT LOG: Šablon korišćen
            auditLogService.logSecurityEvent(AuditLogService.EVENT_TEMPLATE_USED,
                    "Certificate template used for certificate creation", true,
                    "templateId=" + templateId + ", name=" + template.getName(), httpRequest);


            // Vrati podatke šablona koji će se koristiti za formu sertifikata
            Map<String, Object> response = new HashMap<>();
            response.put("template", convertToResponseDTO(template));
            response.put("prefilledData", getPrefilledCertificateData(template));

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            // AUDIT LOG: Neuspešno korišćenje šablona
            auditLogService.logSecurityEvent(AuditLogService.EVENT_TEMPLATE_USED,
                    "Template usage failed", false,
                    "templateId=" + templateId + ", error=" + e.getMessage(), httpRequest);

            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {// AUDIT LOG: Greška pri korišćenju šablona
            auditLogService.logSecurityEvent(AuditLogService.EVENT_TEMPLATE_USED,
                    "Template usage error", false,
                    "templateId=" + templateId + ", error=" + e.getMessage(), httpRequest);

            return ResponseEntity.status(500).body(Map.of("message", "Error using template: " + e.getMessage()));
        }
    }

    // SAMO CA korisnik može da ažurira šablon
    @PreAuthorize("hasRole('CA')")
    @PutMapping("/{templateId}")
    public ResponseEntity<?> updateTemplate(
            @PathVariable Long templateId,
            @Valid @RequestBody CreateTemplateDTO templateDTO,
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            HttpServletRequest httpRequest) {
        try {
            User user = userService.findByEmail(userPrincipal.getEmail())
                    .orElseThrow(() -> new RuntimeException("Authenticated user not found"));

            // Pozovi update metodu iz servisa
            var updatedTemplate = templateService.updateTemplate(templateId, templateDTO, user);
            var responseDTO = convertToResponseDTO(updatedTemplate);

            // AUDIT LOG: Šablon ažuriran
            auditLogService.logSecurityEvent(AuditLogService.EVENT_TEMPLATE_UPDATED,
                    "Certificate template updated", true,
                    "templateId=" + templateId + ", name=" + updatedTemplate.getName(), httpRequest);

            return ResponseEntity.ok(Map.of(
                    "message", "Template updated successfully",
                    "template", responseDTO
            ));
        } catch (IllegalArgumentException e) {
            // AUDIT LOG: Neuspešno ažuriranje šablona
            auditLogService.logSecurityEvent(AuditLogService.EVENT_TEMPLATE_UPDATED,
                    "Template update failed", false,
                    "templateId=" + templateId + ", error=" + e.getMessage(), httpRequest);

            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            // AUDIT LOG: Greška pri ažuriranju šablona
            auditLogService.logSecurityEvent(AuditLogService.EVENT_TEMPLATE_UPDATED,
                    "Template update error", false,
                    "templateId=" + templateId + ", error=" + e.getMessage(), httpRequest);

            return ResponseEntity.status(500).body(Map.of("message", "Error updating template: " + e.getMessage()));
        }
    }

    // Metoda za prefilled data
    private Map<String, Object> getPrefilledCertificateData(CertificateTemplate template) {
        Map<String, Object> prefilledData = new HashMap<>();

        prefilledData.put("caIssuerId", template.getCaIssuer().getId());
        prefilledData.put("maxValidityDays", template.getMaxValidityDays());
        prefilledData.put("keyUsage", template.getKeyUsage());
        prefilledData.put("extendedKeyUsage", template.getExtendedKeyUsage());
        prefilledData.put("basicConstraints", template.getBasicConstraints());
        prefilledData.put("commonNameRegex", template.getCommonNameRegex());
        prefilledData.put("sansRegex", template.getSansRegex());

        return prefilledData;
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
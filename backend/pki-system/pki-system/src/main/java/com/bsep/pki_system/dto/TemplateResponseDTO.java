package com.bsep.pki_system.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TemplateResponseDTO {
    private Long id;
    private String name;
    private String description;
    private Long caIssuerId;
    private String caIssuerName;
    private String commonNameRegex;
    private String sansRegex;
    private Integer maxValidityDays;
    private String keyUsage;
    private String extendedKeyUsage;
    private String basicConstraints;
    private LocalDateTime createdAt;
    private String createdBy;
}
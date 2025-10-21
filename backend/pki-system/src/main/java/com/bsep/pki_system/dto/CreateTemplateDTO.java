package com.bsep.pki_system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreateTemplateDTO {

    @NotBlank(message = "Template name is required")
    private String name;

    private String description;

    @NotNull(message = "CA issuer is required")
    private Long caIssuerId;

    // Regularni izrazi za validaciju
    private String commonNameRegex;

    private String sansRegex;

    // Parametri sertifikata
    @Positive(message = "Max validity days must be positive")
    private Integer maxValidityDays;

    private List<Boolean> keyUsage;

    private String extendedKeyUsage;

    private String basicConstraints = "CA:TRUE";
}
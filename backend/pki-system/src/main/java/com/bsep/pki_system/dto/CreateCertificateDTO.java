package com.bsep.pki_system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Getter
@Setter
public class CreateCertificateDTO {
    @NotBlank(message = "Common Name is required")
    private String subjectCommonName;

    @NotBlank(message = "Organization is required")
    private String subjectOrganization;
    private String subjectOrganizationalUnit;

    @NotBlank(message = "Country is required")
    private String subjectCountry;

    private String subjectState;
    private String subjectLocality;
    private String subjectEmail;

    @NotNull(message = "Valid from date is required")
    private Date validFrom;

    @NotNull(message = "Valid to date is required")
    private Date validTo;

    // Ekstenzije
    private boolean[] keyUsage; // Digital Signature, Key Encipherment, etc.
    private String basicConstraints; // "CA:TRUE, pathlen:0"
    private String extendedKeyUsage; // "serverAuth, clientAuth"

    private Long issuerCertificateId; // Za Root će biti null (samopotpisani)
}
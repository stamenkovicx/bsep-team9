package com.bsep.pki_system.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Getter
@Setter
public class CreateCertificateDTO {
    private String subjectCommonName;
    private String subjectOrganization;
    private String subjectOrganizationalUnit;
    private String subjectCountry;
    private String subjectState;
    private String subjectLocality;
    private String subjectEmail;

    private Date validFrom;
    private Date validTo;

    // Ekstenzije
    private boolean[] keyUsage; // Digital Signature, Key Encipherment, etc.
    private String basicConstraints; // "CA:TRUE, pathlen:0"
    private String extendedKeyUsage; // "serverAuth, clientAuth"

    private Long issuerCertificateId; // Za Root će biti null (samopotpisani)
}
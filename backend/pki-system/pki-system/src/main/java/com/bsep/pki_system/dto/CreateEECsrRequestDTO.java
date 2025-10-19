package com.bsep.pki_system.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Date;

public class CreateEECsrRequestDTO {

    @NotBlank(message = "CSR PEM content is required")
    private String csrPem;

    @NotNull(message = "Valid To date is required")
    // 🔥 KLJUČNA ISPRAVKA: Jackson anotacija za ISO 8601 format
    // Pattern "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'" prepoznaje Z (Zulu/UTC) zonu.
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private Date validTo;

    @NotNull(message = "Issuer Certificate ID is required")
    private Long issuerCertificateId;

    // --- Getteri i Setteri ---

    public String getCsrPem() {
        return csrPem;
    }

    public void setCsrPem(String csrPem) {
        this.csrPem = csrPem;
    }

    public Date getValidTo() {
        return validTo;
    }

    public void setValidTo(Date validTo) {
        this.validTo = validTo;
    }

    public Long getIssuerCertificateId() {
        return issuerCertificateId;
    }

    public void setIssuerCertificateId(Long issuerCertificateId) {
        this.issuerCertificateId = issuerCertificateId;
    }
}
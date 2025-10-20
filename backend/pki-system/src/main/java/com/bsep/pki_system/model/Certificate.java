package com.bsep.pki_system.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.security.PublicKey;
import java.time.LocalDateTime;
import java.util.Date;

@Entity
@Table(name = "certificates")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Certificate {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String serialNumber;

    @Column(nullable = false)
    private String subject; // X500Name podaci o vlasniku

    @Column(nullable = false)
    private String issuer; // X500Name podaci o izdavaocu

    @Column(nullable = false)
    private Date validFrom;

    @Column(nullable = false)
    private Date validTo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CertificateType type; // ROOT, INTERMEDIATE, END_ENTITY

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CertificateStatus status = CertificateStatus.VALID;

    @Column(length = 1000)
    private String publicKey; // Čuvamo kao string (PEM format)

    // Za CA sertifikate
    private Boolean isCA = false;
    private String pathLengthConstraint; // Za Basic Constraints

    // Za revokaciju
    private String revocationReason;
    private LocalDateTime revokedAt;

    @ManyToOne
    @JoinColumn(name = "issuer_certificate_id")
    private Certificate issuerCertificate; // Referenca na sertifikat izdavaoca

    @ManyToOne
    @JoinColumn(name = "owner_id")
    private User owner; // Koji korisnik je kreirao/vlasnik je sertifikata

    // Dodatna polja za ekstenzije
    private String keyUsage;
    private String extendedKeyUsage;
    private String basicConstraints;

    @Column(name = "crl_number")
    private Long crlNumber; // Broj CRL liste (inkrementira se sa svakom novom)

    @Column(name = "last_crl_update")
    private LocalDateTime lastCRLUpdate;
}
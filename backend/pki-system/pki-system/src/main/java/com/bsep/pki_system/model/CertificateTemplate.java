package com.bsep.pki_system.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "certificate_templates")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CertificateTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(length = 1000)
    private String description;

    // Koji CA može da koristi ovaj šablon
    @ManyToOne
    @JoinColumn(name = "ca_issuer_id", nullable = false)
    private Certificate caIssuer;

    // Validacija - regularni izrazi
    @Column(name = "common_name_regex")
    private String commonNameRegex;

    @Column(name = "sans_regex")
    private String sansRegex;

    // Parametri sertifikata
    @Column(name = "max_validity_days")
    private Integer maxValidityDays;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "template_key_usage", joinColumns = @JoinColumn(name = "template_id"))
    @Column(name = "key_usage_bit")
    private List<Boolean> keyUsage;

    @Column(name = "extended_key_usage")
    private String extendedKeyUsage;

    // Basic Constraints za Intermediate CA
    @Column(name = "basic_constraints")
    private String basicConstraints = "CA:TRUE";

    // Dodatna polja po potrebi
    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @ManyToOne
    @JoinColumn(name = "created_by_id")
    private User createdBy;
}
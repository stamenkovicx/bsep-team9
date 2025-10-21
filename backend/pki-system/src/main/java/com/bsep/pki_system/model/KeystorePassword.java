package com.bsep.pki_system.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "keystore_passwords")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class KeystorePassword {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "certificate_id", nullable = false, unique = true)
    private Certificate certificate;

    @Column(name = "encrypted_password", nullable = false, length = 500)
    private String encryptedPassword;

    @Column(name = "encryption_algorithm", nullable = false)
    private String encryptionAlgorithm = "AES/GCM/NoPadding";

    @Column(name = "iv", nullable = false, length = 100)
    private String iv; // Initialization Vector

    @Column(name = "salt", length = 100)
    private String salt; // Za PBKDF2

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
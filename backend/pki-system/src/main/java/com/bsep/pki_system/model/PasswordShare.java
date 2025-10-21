package com.bsep.pki_system.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "password_shares")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PasswordShare {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "password_entry_id", nullable = false)
    private PasswordEntry passwordEntry;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user; // Korisnik sa kojim je dijeljeno

    @Column(nullable = false, length = 2000)
    private String encryptedPassword; // Lozinka enkriptovana javnim ključem ovog korisnika

    @Column(nullable = false)
    private LocalDateTime sharedAt;

    @ManyToOne
    @JoinColumn(name = "shared_by_id", nullable = false)
    private User sharedBy; // Ko je podijelio lozinku

    @PrePersist
    protected void onCreate() {
        sharedAt = LocalDateTime.now();
    }
}
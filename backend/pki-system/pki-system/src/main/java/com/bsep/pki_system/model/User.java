package com.bsep.pki_system.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "app_user")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String surname;

    @Column(unique = true, nullable = false)
    private String email;

    private String password;
    private String organization;

    private boolean enabled = false; // Korisnik je neaktivan dok ne potvrdi email

    @Enumerated(EnumType.STRING)
    private UserRole role;

    // Email verifikacija
    private String verificationToken;
    private LocalDateTime tokenExpiryDate;

}

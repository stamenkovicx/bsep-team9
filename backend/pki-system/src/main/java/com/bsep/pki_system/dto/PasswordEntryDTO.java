package com.bsep.pki_system.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PasswordEntryDTO {
    private Long id;
    private String siteName;
    private String username;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String notes;
    private Long ownerId;
    private String ownerEmail;

    // Ovo polje će biti null osim kada korisnik dekriptuje lozinku
    private String decryptedPassword;
}
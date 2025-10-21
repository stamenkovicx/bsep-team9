package com.bsep.pki_system.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EncryptedPasswordDTO {
    private String encryptedPassword; // Base64 enkriptovana lozinka
    private String publicKeyFingerprint; // Identifikator javnog ključa koji je korišćen
}
package com.bsep.pki_system.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreatePasswordEntryDTO {
    private String siteName;
    private String username;
    private String password; // Plain text password koji će se enkriptovati
    private String notes;
}
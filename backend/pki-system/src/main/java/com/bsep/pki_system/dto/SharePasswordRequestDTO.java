package com.bsep.pki_system.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SharePasswordRequestDTO {
    private Long passwordEntryId;
    private String targetUserEmail; // Email korisnika sa kojim želiš da dijeliš
}
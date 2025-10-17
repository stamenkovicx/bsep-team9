package com.bsep.pki_system.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EncryptionResultDTO {
    private String encryptedData;
    private String iv;
    private String salt;
    private String algorithm;
}
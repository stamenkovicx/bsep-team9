package com.bsep.pki_system.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
// Nasleđuje LoginDTO da bi preuzeo standardna polja za prijavu
public class LoginWith2FADTO extends LoginDTO {

    // Polje za prijem 2FA koda
    private String twoFactorCode;
}
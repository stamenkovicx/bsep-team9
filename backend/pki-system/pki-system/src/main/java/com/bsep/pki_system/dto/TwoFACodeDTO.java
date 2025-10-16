package com.bsep.pki_system.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TwoFACodeDTO {

    // Kod koji korisnik unosi sa autentikator aplikacije
    private String code;

    // Možete dodati i konstruktore po potrebi
}
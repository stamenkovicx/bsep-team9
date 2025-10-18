package com.bsep.pki_system.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ForgotPasswordDTO {

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;
}
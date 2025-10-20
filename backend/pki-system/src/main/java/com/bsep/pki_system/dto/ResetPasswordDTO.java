package com.bsep.pki_system.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ResetPasswordDTO {

    @NotBlank(message = "Token is required")
    private String token;

    @NotBlank(message = "Password is required")
    private String newPassword;

    @NotBlank(message = "Password confirmation is required")
    private String confirmPassword;
}
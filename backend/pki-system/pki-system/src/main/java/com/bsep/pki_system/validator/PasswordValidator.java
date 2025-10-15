package com.bsep.pki_system.validator;

import org.springframework.stereotype.Component;

@Component
public class PasswordValidator {

    //Validacija minimalne jacine: 8+ karaktera, veliko slovo, malo slovo, broj, specijalni karakter
    public boolean isValid(String password) {
        if (password == null || password.length() < 8) {
            return false;
        }

        boolean hasUpperCase = password.chars().anyMatch(Character::isUpperCase);
        boolean hasLowerCase = password.chars().anyMatch(Character::isLowerCase);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        boolean hasSpecial = password.chars().anyMatch(ch -> "!@#$%^&*()_+-=[]{}|;:',.<>?/".indexOf(ch) >= 0);

        return hasUpperCase && hasLowerCase && hasDigit && hasSpecial;
    }

    //slaba, srednja, teska sifra
    public String getPasswordStrength(String password) {
        if (password == null || password.length() < 8) {
            return "weak";
        }

        int strength = 0;

        if (password.length() >= 8) strength++;
        if (password.length() >= 12) strength++;
        if (password.chars().anyMatch(Character::isUpperCase)) strength++;
        if (password.chars().anyMatch(Character::isLowerCase)) strength++;
        if (password.chars().anyMatch(Character::isDigit)) strength++;
        if (password.chars().anyMatch(ch -> "!@#$%^&*()_+-=[]{}|;:',.<>?/".indexOf(ch) >= 0)) strength++;

        if (strength <= 2) return "weak";
        if (strength <= 4) return "medium";
        return "strong";
    }

    public String getValidationMessage() {
        return "Password must contain at least 8 characters, one uppercase letter, one lowercase letter, one digit, and one special character";
    }
}
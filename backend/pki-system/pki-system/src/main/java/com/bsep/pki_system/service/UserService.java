package com.bsep.pki_system.service;

import com.bsep.pki_system.model.User;
import com.bsep.pki_system.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public User registerUser(User user) {
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        return userRepository.save(user);
    }

    public User login(String email, String rawPassword) {
        return userRepository.findByEmail(email)
                .filter(user -> passwordEncoder.matches(rawPassword, user.getPassword()))
                .orElse(null);
    }

    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    public Optional<User> findByVerificationToken(String token) {
        return userRepository.findByVerificationToken(token);
    }
    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    public User updateUser(User user) {
        System.out.println("=== UPDATING USER ===");
        System.out.println("User ID: " + user.getId());
        System.out.println("Email: " + user.getEmail());
        System.out.println("is2faEnabled: " + user.getIs2faEnabled());
        System.out.println("twoFactorSecret: " + (user.getTwoFactorSecret() != null ? "SET" : "NULL"));

        if (user.getIs2faEnabled() == null) {
            user.setIs2faEnabled(false);
        }

        User savedUser = userRepository.save(user);
        System.out.println("User saved with ID: " + savedUser.getId());
        System.out.println("=== USER UPDATED ===");

        return savedUser;
    }

    public String generateTemporaryPassword() {
        // Generisanje nasumične lozinke od 12 karaktera
        String upperCase = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        String lowerCase = "abcdefghijklmnopqrstuvwxyz";
        String numbers = "0123456789";
        String specialChars = "!@#$%";
        String allChars = upperCase + lowerCase + numbers + specialChars;

        Random random = new Random();
        StringBuilder password = new StringBuilder();

        // Obavezno bar po jedan karakter iz svake grupe
        password.append(upperCase.charAt(random.nextInt(upperCase.length())));
        password.append(lowerCase.charAt(random.nextInt(lowerCase.length())));
        password.append(numbers.charAt(random.nextInt(numbers.length())));
        password.append(specialChars.charAt(random.nextInt(specialChars.length())));

        // Ostalih 8 nasumičnih karaktera
        for (int i = 0; i < 8; i++) {
            password.append(allChars.charAt(random.nextInt(allChars.length())));
        }

        // Mešanje karaktera za bolju sigurnost
        char[] passwordArray = password.toString().toCharArray();
        for (int i = passwordArray.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            char temp = passwordArray[i];
            passwordArray[i] = passwordArray[j];
            passwordArray[j] = temp;
        }

        return new String(passwordArray);
    }

    public void deleteUser(Long userId) {
        userRepository.deleteById(userId);
    }

    public void changePassword(User user, String newPassword) {
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    public Optional<User> findByPasswordResetToken(String token) {
        return userRepository.findByPasswordResetToken(token);
    }

    public void initiatePasswordReset(String email) throws Exception {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new Exception("User not found"));

        // Generisi token
        String token = UUID.randomUUID().toString();

        user.setPasswordResetToken(token);
        user.setPasswordResetTokenExpiryDate(LocalDateTime.now().plusHours(1));

        User savedUser = userRepository.save(user);
        System.out.println("Saved token: " + savedUser.getPasswordResetToken());
        System.out.println("Token expiry: " + savedUser.getPasswordResetTokenExpiryDate());
    }

    public void resetPassword(String token, String newPassword) throws Exception {
        Optional<User> userOpt = userRepository.findByPasswordResetToken(token);

        if (userOpt.isEmpty()) {
            throw new Exception("Invalid reset token");
        }

        User user = userOpt.get();

        // Proveri da li je token istekao
        if (user.getPasswordResetTokenExpiryDate().isBefore(LocalDateTime.now())) {
            throw new Exception("Reset token has expired");
        }
        // Promeni lozinku
        user.setPassword(passwordEncoder.encode(newPassword));
        // Obriši token (jednokratna upotreba)
        user.setPasswordResetToken(null);
        user.setPasswordResetTokenExpiryDate(null);

        userRepository.save(user);
    }
}
package com.bsep.pki_system.service;

import com.bsep.pki_system.model.User;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class EmailVerificationService {

    private final JavaMailSender mailSender;

    public EmailVerificationService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public String generateVerificationToken() {
        return UUID.randomUUID().toString();
    }

    public LocalDateTime generateExpiryDate() {
        return LocalDateTime.now().plusHours(24); // Token važi 24 sata
    }

    public void sendVerificationEmail(User user, String token) {
        String verificationLink = "http://localhost:8089/auth/verify?token=" + token;

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(user.getEmail());
        message.setSubject("Verify Your Account");
        message.setText("Hello " + user.getName() + ",\n\n" +
                "Thank you for registering. Please click the link below to verify your account:\n\n" +
                verificationLink + "\n\n" +
                "This link will expire in 24 hours.\n\n" +
                "If you did not register for this account, please ignore this email.\n\n" +
                "Best regards,\n" +
                "PKI System Team");

        mailSender.send(message);
    }

    public boolean isTokenExpired(LocalDateTime expiryDate) {
        return expiryDate.isBefore(LocalDateTime.now());
    }
}
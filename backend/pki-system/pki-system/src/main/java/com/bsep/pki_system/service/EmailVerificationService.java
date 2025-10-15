package com.bsep.pki_system.service;

import com.bsep.pki_system.model.User;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
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

    public void sendVerificationEmail(User user, String token) throws MessagingException {
        String verificationLink = "http://localhost:8089/auth/verify?token=" + token;

        MimeMessage mimeMessage = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, "utf-8");

        // Jednostavan HTML sadržaj bez stilova
        String htmlContent = "<h3>Hello " + user.getName() + ",</h3>" +
                "<p>Thank you for registering. Please click the link below to verify your account:</p>" +
                "<p><a href='" + verificationLink + "'>" + verificationLink + "</a></p>" +
                "<br>" +
                "<p>This link will expire in 24 hours.</p>" +
                "<p>If you did not create an account, please ignore this email.</p>" +
                "<br>" +
                "<p>Best regards,<br>PKI System Team</p>";

        helper.setTo(user.getEmail());
        helper.setSubject("Verify Your Account - PKI System");
        helper.setText(htmlContent, true); // true i dalje znači da je format HTML

        mailSender.send(mimeMessage);
    }

    public boolean isTokenExpired(LocalDateTime expiryDate) {
        return expiryDate.isBefore(LocalDateTime.now());
    }
}
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

    public void sendCAPasswordEmail(User user, String temporaryPassword) throws MessagingException {
        MimeMessage mimeMessage = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, "utf-8");

        String htmlContent = "<h3>Hello " + user.getName() + " " + user.getSurname() + ",</h3>" +
                "<p>Your Certificate Authority (CA) account has been created successfully.</p>" +
                "<br>" +
                "<p><strong>Login Credentials:</strong></p>" +
                "<ul>" +
                "<li><strong>Email:</strong> " + user.getEmail() + "</li>" +
                "<li><strong>Temporary Password:</strong> " + temporaryPassword + "</li>" +
                "</ul>" +
                "<br>" +
                "<p><strong>Important:</strong> You must change your password immediately after first login.</p>" +
                "<p>Login URL: <a href='http://localhost:4200/login'>http://localhost:4200/login</a></p>" +
                "<br>" +
                "<p>If you did not request this account, please contact the system administrator immediately.</p>" +
                "<br>" +
                "<p>Best regards,<br>PKI System Team</p>";

        helper.setTo(user.getEmail());
        helper.setSubject("Your CA Account Credentials - PKI System");
        helper.setText(htmlContent, true);

        mailSender.send(mimeMessage);
    }
    public void sendPasswordResetEmail(User user, String token) throws MessagingException {
        String resetLink = "http://localhost:4200/reset-password?token=" + token;

        MimeMessage mimeMessage = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, "utf-8");

        String htmlContent = "<h3>Hello " + user.getName() + ",</h3>" +
                "<p>You have requested to reset your password. Please click the link below to set a new password:</p>" +
                "<p><a href='" + resetLink + "'>" + resetLink + "</a></p>" +
                "<br>" +
                "<p><strong>Important:</strong> This link will expire in 1 hour.</p>" +
                "<p>If you did not request this, please ignore this email.</p>" +
                "<br>" +
                "<p>Best regards,<br>PKI System Team</p>";

        helper.setTo(user.getEmail());
        helper.setSubject("Password Reset Request - PKI System");
        helper.setText(htmlContent, true);

        mailSender.send(mimeMessage);
    }
}
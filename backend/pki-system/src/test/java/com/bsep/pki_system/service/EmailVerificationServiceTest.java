package com.bsep.pki_system.service;

import com.bsep.pki_system.model.User;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.mail.MailSendException;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EmailVerificationServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @InjectMocks
    private EmailVerificationService emailVerificationService;

    @Captor
    private ArgumentCaptor<MimeMessage> mimeMessageCaptor;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    private User createUser() {
        User user = new User();
        user.setName("Ivana");
        user.setSurname("Glamocic");
        user.setEmail("ivana@example.com");
        return user;
    }

    @Test
    void testGenerateVerificationToken_NotNullAndUnique() {
        String token1 = emailVerificationService.generateVerificationToken();
        String token2 = emailVerificationService.generateVerificationToken();

        assertNotNull(token1);
        assertNotNull(token2);
        assertNotEquals(token1, token2);
        assertDoesNotThrow(() -> UUID.fromString(token1)); // valid UUID format
    }

    @Test
    void testGenerateExpiryDate_24HoursAhead() {
        LocalDateTime expiry = emailVerificationService.generateExpiryDate();
        LocalDateTime now = LocalDateTime.now();

        long hoursBetween = java.time.Duration.between(now, expiry).toHours();
        assertTrue(hoursBetween >= 23 && hoursBetween <= 24);
    }

    @Test
    void testIsTokenExpired_WhenExpired() {
        LocalDateTime expired = LocalDateTime.now().minusMinutes(1);
        assertTrue(emailVerificationService.isTokenExpired(expired));
    }

    @Test
    void testIsTokenExpired_WhenNotExpired() {
        LocalDateTime notExpired = LocalDateTime.now().plusMinutes(10);
        assertFalse(emailVerificationService.isTokenExpired(notExpired));
    }

    @Test
    void testSendVerificationEmail_SendsCorrectEmail() throws MessagingException {
        User user = createUser();
        String token = "test-token";
        MimeMessage mimeMessage = mock(MimeMessage.class);

        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        emailVerificationService.sendVerificationEmail(user, token);

        verify(mailSender, times(1)).send(mimeMessage);
        verify(mailSender).createMimeMessage();

        // Nema exceptiona i mejl se poziva
        assertDoesNotThrow(() ->
                emailVerificationService.sendVerificationEmail(user, token)
        );
    }

    @Test
    void testSendCAPasswordEmail_SendsCorrectEmail() throws MessagingException {
        User user = createUser();
        String tempPassword = "Temp123!";
        MimeMessage mimeMessage = mock(MimeMessage.class);

        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        emailVerificationService.sendCAPasswordEmail(user, tempPassword);

        verify(mailSender, times(1)).send(mimeMessage);
    }

    @Test
    void testSendPasswordResetEmail_SendsCorrectEmail() throws MessagingException {
        User user = createUser();
        String token = "reset-token";
        MimeMessage mimeMessage = mock(MimeMessage.class);

        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        emailVerificationService.sendPasswordResetEmail(user, token);

        verify(mailSender, times(1)).send(mimeMessage);
    }

    @Test
    void testSendVerificationEmail_ThrowsMailSendException() throws Exception {
        User user = createUser();
        String token = "broken";
        MimeMessage mimeMessage = mock(MimeMessage.class);

        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        doThrow(new MailSendException("Mail server error")).when(mailSender).send(any(MimeMessage.class));

        assertThrows(MailSendException.class, () -> {
            emailVerificationService.sendVerificationEmail(user, token);
        });
    }
}

package com.bsep.pki_system.service;

import com.bsep.pki_system.model.User;
import com.warrenstrange.googleauth.GoogleAuthenticator;
import com.warrenstrange.googleauth.GoogleAuthenticatorKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TwoFactorServiceTest {

    @Mock
    private GoogleAuthenticator gAuth;

    @Mock
    private UserService userService;

    @InjectMocks
    private TwoFactorService twoFactorService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testGenerateSecretAndQr_Success() {
        // Arrange
        User user = new User();
        user.setEmail("test@example.com");
        user.setIs2faEnabled(null); // simuliraj NULL kao u kodu

        GoogleAuthenticatorKey key = mock(GoogleAuthenticatorKey.class);
        when(key.getKey()).thenReturn("SECRET123");
        when(gAuth.createCredentials()).thenReturn(key);

        // Act
        String qrUrl = twoFactorService.generateSecretAndQr(user);

        // Assert
        verify(userService, times(1)).updateUser(user);
        assertNotNull(qrUrl);
        assertTrue(qrUrl.contains("SECRET123"));
        assertTrue(qrUrl.contains("test%40example.com")); // proverava URL encoding
        assertEquals("SECRET123", user.getTwoFactorSecret());
        assertFalse(user.getIs2faEnabled()); // po defaultu false ako je null bio
    }

    @Test
    void testGenerateSecretAndQr_WhenAlreadyHas2FAEnabled() {
        // Arrange
        User user = new User();
        user.setEmail("john@example.com");
        user.setIs2faEnabled(true);

        GoogleAuthenticatorKey key = mock(GoogleAuthenticatorKey.class);
        when(key.getKey()).thenReturn("XYZ789");
        when(gAuth.createCredentials()).thenReturn(key);

        // Act
        String qrUrl = twoFactorService.generateSecretAndQr(user);

        // Assert
        verify(userService, times(1)).updateUser(user);
        assertNotNull(qrUrl);
        assertTrue(qrUrl.contains("XYZ789"));
        assertTrue(user.getIs2faEnabled());
    }

    @Test
    void testIsCodeValid_True() {
        when(gAuth.authorize("SECRET123", 123456)).thenReturn(true);
        assertTrue(twoFactorService.isCodeValid("SECRET123", 123456));
    }

    @Test
    void testIsCodeValid_False() {
        when(gAuth.authorize("SECRET123", 111111)).thenReturn(false);
        assertFalse(twoFactorService.isCodeValid("SECRET123", 111111));
    }

    @Test
    void testConfirm2faActivation() {
        User user = new User();
        user.setIs2faEnabled(false);

        twoFactorService.confirm2faActivation(user);

        assertTrue(user.getIs2faEnabled());
        verify(userService, times(1)).updateUser(user);
    }
}

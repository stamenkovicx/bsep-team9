package com.bsep.pki_system.service;

import com.bsep.pki_system.model.User;
import com.bsep.pki_system.model.UserRole;
import com.bsep.pki_system.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    private User testUser;
    private User adminUser;

    @BeforeEach
    void setUp() {
        // Setup test user
        testUser = new User();
        testUser.setId(1L);
        testUser.setEmail("test@example.com");
        testUser.setPassword("encodedPassword123");
        testUser.setName("John");
        testUser.setSurname("Doe");
        testUser.setRole(UserRole.BASIC);
        testUser.setOrganization("Test Org");
        testUser.setIs2faEnabled(false);

        // Setup admin user
        adminUser = new User();
        adminUser.setId(2L);
        adminUser.setEmail("admin@example.com");
        adminUser.setPassword("encodedAdminPassword");
        adminUser.setName("Admin");
        adminUser.setSurname("User");
        adminUser.setRole(UserRole.ADMIN);
        adminUser.setIs2faEnabled(true);
        adminUser.setTwoFactorSecret("2fa-secret-key");
    }

    // ===== REGISTRATION TESTS =====

    @Test
    void registerUser_ShouldEncodePasswordAndSave() {
        User newUser = new User();
        newUser.setEmail("new@example.com");
        newUser.setPassword("plainPassword");
        newUser.setName("New");
        newUser.setSurname("User");
        newUser.setRole(UserRole.BASIC);

        when(passwordEncoder.encode("plainPassword")).thenReturn("encodedPassword");
        when(userRepository.save(any(User.class))).thenReturn(newUser);

        User result = userService.registerUser(newUser);

        assertNotNull(result);
        verify(passwordEncoder, times(1)).encode("plainPassword");
        verify(userRepository, times(1)).save(newUser);
    }

    // ===== LOGIN TESTS =====

    @Test
    void login_WithValidCredentials_ShouldReturnUser() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("correctPassword", "encodedPassword123")).thenReturn(true);

        User result = userService.login("test@example.com", "correctPassword");

        assertNotNull(result);
        assertEquals(testUser.getEmail(), result.getEmail());
    }

    @Test
    void login_WithInvalidEmail_ShouldReturnNull() {
        when(userRepository.findByEmail("nonexistent@example.com")).thenReturn(Optional.empty());

        User result = userService.login("nonexistent@example.com", "anyPassword");

        assertNull(result);
    }

    @Test
    void login_WithInvalidPassword_ShouldReturnNull() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("wrongPassword", "encodedPassword123")).thenReturn(false);

        User result = userService.login("test@example.com", "wrongPassword");

        assertNull(result);
    }

    // ===== USER EXISTS TESTS =====

    @Test
    void existsByEmail_WhenUserExists_ShouldReturnTrue() {
        when(userRepository.existsByEmail("test@example.com")).thenReturn(true);

        boolean result = userService.existsByEmail("test@example.com");

        assertTrue(result);
    }

    @Test
    void existsByEmail_WhenUserNotExists_ShouldReturnFalse() {
        when(userRepository.existsByEmail("nonexistent@example.com")).thenReturn(false);

        boolean result = userService.existsByEmail("nonexistent@example.com");

        assertFalse(result);
    }

    // ===== FIND USER TESTS =====

    @Test
    void findByEmail_WhenUserExists_ShouldReturnUser() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));

        Optional<User> result = userService.findByEmail("test@example.com");

        assertTrue(result.isPresent());
        assertEquals(testUser.getEmail(), result.get().getEmail());
    }

    @Test
    void findByEmail_WhenUserNotExists_ShouldReturnEmpty() {
        when(userRepository.findByEmail("nonexistent@example.com")).thenReturn(Optional.empty());

        Optional<User> result = userService.findByEmail("nonexistent@example.com");

        assertFalse(result.isPresent());
    }

    @Test
    void findByVerificationToken_WhenTokenExists_ShouldReturnUser() {
        testUser.setVerificationToken("verification-token-123");
        when(userRepository.findByVerificationToken("verification-token-123")).thenReturn(Optional.of(testUser));

        Optional<User> result = userService.findByVerificationToken("verification-token-123");

        assertTrue(result.isPresent());
        assertEquals("verification-token-123", result.get().getVerificationToken());
    }

    // ===== UPDATE USER TESTS =====

    @Test
    void updateUser_ShouldSaveUser() {
        testUser.setIs2faEnabled(true);
        testUser.setTwoFactorSecret("new-2fa-secret");

        when(userRepository.save(testUser)).thenReturn(testUser);

        User result = userService.updateUser(testUser);

        assertNotNull(result);
        assertEquals(true, result.getIs2faEnabled());
        assertEquals("new-2fa-secret", result.getTwoFactorSecret());
        verify(userRepository, times(1)).save(testUser);
    }

    @Test
    void updateUser_When2faNull_ShouldSetToFalse() {
        testUser.setIs2faEnabled(null);

        when(userRepository.save(testUser)).thenReturn(testUser);

        User result = userService.updateUser(testUser);

        assertNotNull(result);
        assertEquals(false, result.getIs2faEnabled());
    }

    // ===== PASSWORD MANAGEMENT TESTS =====

    @Test
    void generateTemporaryPassword_ShouldReturnValidPassword() {
        String password = userService.generateTemporaryPassword();

        assertNotNull(password);
        assertTrue(password.length() >= 12);
        // Proveri da sadrži različite tipove karaktera
        assertTrue(password.matches(".*[A-Z].*")); // Bar jedno veliko slovo
        assertTrue(password.matches(".*[a-z].*")); // Bar jedno malo slovo
        assertTrue(password.matches(".*[0-9].*")); // Bar jedan broj
        assertTrue(password.matches(".*[!@#$%].*")); // Bar jedan specijalni karakter
    }

    @Test
    void changePassword_ShouldEncodeAndSave() {
        when(passwordEncoder.encode("newPassword123")).thenReturn("encodedNewPassword");
        when(userRepository.save(testUser)).thenReturn(testUser);

        userService.changePassword(testUser, "newPassword123");

        verify(passwordEncoder, times(1)).encode("newPassword123");
        verify(userRepository, times(1)).save(testUser);
    }

    // ===== PASSWORD RESET TESTS =====

    @Test
    void initiatePasswordReset_WhenUserExists_ShouldSetToken() throws Exception {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        userService.initiatePasswordReset("test@example.com");

        assertNotNull(testUser.getPasswordResetToken());
        assertNotNull(testUser.getPasswordResetTokenExpiryDate());
        assertTrue(testUser.getPasswordResetTokenExpiryDate().isAfter(LocalDateTime.now()));
        verify(userRepository, times(1)).save(testUser);
    }

    @Test
    void initiatePasswordReset_WhenUserNotExists_ShouldThrowException() {
        when(userRepository.findByEmail("nonexistent@example.com")).thenReturn(Optional.empty());

        assertThrows(Exception.class, () -> {
            userService.initiatePasswordReset("nonexistent@example.com");
        });
    }

    @Test
    void findByPasswordResetToken_WhenTokenExists_ShouldReturnUser() {
        testUser.setPasswordResetToken("reset-token-123");
        when(userRepository.findByPasswordResetToken("reset-token-123")).thenReturn(Optional.of(testUser));

        Optional<User> result = userService.findByPasswordResetToken("reset-token-123");

        assertTrue(result.isPresent());
        assertEquals("reset-token-123", result.get().getPasswordResetToken());
    }

    @Test
    void resetPassword_WithValidToken_ShouldUpdatePassword() throws Exception {
        testUser.setPasswordResetToken("valid-token");
        testUser.setPasswordResetTokenExpiryDate(LocalDateTime.now().plusHours(1));

        when(userRepository.findByPasswordResetToken("valid-token")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.encode("newPassword123")).thenReturn("encodedNewPassword");
        when(userRepository.save(testUser)).thenReturn(testUser);

        userService.resetPassword("valid-token", "newPassword123");

        assertEquals("encodedNewPassword", testUser.getPassword());
        assertNull(testUser.getPasswordResetToken());
        assertNull(testUser.getPasswordResetTokenExpiryDate());
        verify(userRepository, times(1)).save(testUser);
    }

    @Test
    void resetPassword_WithInvalidToken_ShouldThrowException() {
        when(userRepository.findByPasswordResetToken("invalid-token")).thenReturn(Optional.empty());

        assertThrows(Exception.class, () -> {
            userService.resetPassword("invalid-token", "newPassword123");
        });
    }

    @Test
    void resetPassword_WithExpiredToken_ShouldThrowException() {
        testUser.setPasswordResetToken("expired-token");
        testUser.setPasswordResetTokenExpiryDate(LocalDateTime.now().minusHours(1)); // Expired

        when(userRepository.findByPasswordResetToken("expired-token")).thenReturn(Optional.of(testUser));

        assertThrows(Exception.class, () -> {
            userService.resetPassword("expired-token", "newPassword123");
        });
    }

    // ===== DELETE USER TESTS =====

    @Test
    void deleteUser_ShouldCallRepository() {
        userService.deleteUser(1L);

        verify(userRepository, times(1)).deleteById(1L);
    }
}
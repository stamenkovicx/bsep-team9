package com.bsep.pki_system.service;

import com.bsep.pki_system.dto.CreatePasswordEntryDTO;
import com.bsep.pki_system.dto.PasswordEntryDTO;
import com.bsep.pki_system.dto.SharePasswordRequestDTO;
import com.bsep.pki_system.model.*;
import com.bsep.pki_system.repository.PasswordEntryRepository;
import com.bsep.pki_system.repository.PasswordShareRepository;
import com.bsep.pki_system.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PasswordManagerServiceTest {

    @Mock
    private PasswordEntryRepository passwordEntryRepository;

    @Mock
    private PasswordShareRepository passwordShareRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CertificateService certificateService;

    @Mock
    private EncryptionService encryptionService;

    @Mock
    private UserService userService;

    @InjectMocks
    private PasswordManagerService passwordManagerService;

    private User testUser;
    private User targetUser;
    private Certificate validCertificate;
    private PasswordEntry testPasswordEntry;
    private PasswordShare testPasswordShare;

    @BeforeEach
    void setUp() {
        // Setup test user
        testUser = new User();
        testUser.setId(1L);
        testUser.setEmail("test@example.com");
        testUser.setRole(UserRole.BASIC);

        // Setup target user for sharing
        targetUser = new User();
        targetUser.setId(2L);
        targetUser.setEmail("target@example.com");
        targetUser.setRole(UserRole.BASIC);

        // Setup valid certificate
        validCertificate = new Certificate();
        validCertificate.setId(1L);
        validCertificate.setSerialNumber("EE-12345");
        validCertificate.setStatus(CertificateStatus.VALID);
        validCertificate.setType(CertificateType.END_ENTITY);
        validCertificate.setOwner(testUser);
        validCertificate.setValidFrom(new Date(System.currentTimeMillis() - 86400000));
        validCertificate.setValidTo(new Date(System.currentTimeMillis() + 86400000));
        validCertificate.setPublicKey("test-public-key-base64");

        // Setup password entry
        testPasswordEntry = new PasswordEntry();
        testPasswordEntry.setId(1L);
        testPasswordEntry.setSiteName("Gmail");
        testPasswordEntry.setUsername("testuser@gmail.com");
        testPasswordEntry.setOwner(testUser);
        testPasswordEntry.setNotes("Personal Gmail account");
        testPasswordEntry.setCreatedAt(LocalDateTime.now());
        testPasswordEntry.setUpdatedAt(LocalDateTime.now());

        // Setup password share
        testPasswordShare = new PasswordShare();
        testPasswordShare.setId(1L);
        testPasswordShare.setPasswordEntry(testPasswordEntry);
        testPasswordShare.setUser(testUser);
        testPasswordShare.setEncryptedPassword("encrypted-password-base64");
        testPasswordShare.setSharedBy(testUser);
    }

    // ===== CREATE PASSWORD ENTRY TESTS =====

    @Test
    void createPasswordEntry_WithValidCertificate_ShouldCreateEntry() {
        CreatePasswordEntryDTO createDto = new CreatePasswordEntryDTO();
        createDto.setSiteName("Gmail");
        createDto.setUsername("testuser@gmail.com");
        createDto.setPassword("mySecretPassword123");
        createDto.setNotes("Personal account");

        // Kreiraj certificate sa dužim public key-om
        Certificate certificateWithLongKey = new Certificate();
        certificateWithLongKey.setId(1L);
        certificateWithLongKey.setSerialNumber("EE-12345");
        certificateWithLongKey.setStatus(CertificateStatus.VALID);
        certificateWithLongKey.setType(CertificateType.END_ENTITY);
        certificateWithLongKey.setOwner(testUser);
        certificateWithLongKey.setPublicKey("very-long-public-key-base64-encoding-that-is-more-than-50-characters-long-for-testing");

        when(certificateService.canUserUsePasswordManager(testUser)).thenReturn(true);
        when(certificateService.findValidEndEntityCertificateByOwner(testUser))
                .thenReturn(Optional.of(certificateWithLongKey));
        when(encryptionService.encryptWithPublicKey(anyString(), anyString()))
                .thenReturn("encrypted-password-base64");
        when(passwordEntryRepository.save(any(PasswordEntry.class))).thenReturn(testPasswordEntry);
        when(passwordShareRepository.save(any(PasswordShare.class))).thenReturn(testPasswordShare);

        PasswordEntryDTO result = passwordManagerService.createPasswordEntry(createDto, testUser);

        assertNotNull(result);
        assertEquals("Gmail", result.getSiteName());
        assertEquals("testuser@gmail.com", result.getUsername());
        verify(passwordEntryRepository, times(1)).save(any(PasswordEntry.class));
        verify(passwordShareRepository, times(1)).save(any(PasswordShare.class));
    }


    @Test
    void createPasswordEntry_WithoutValidCertificate_ShouldThrowException() {
        CreatePasswordEntryDTO createDto = new CreatePasswordEntryDTO();
        createDto.setSiteName("Gmail");
        createDto.setUsername("testuser@gmail.com");
        createDto.setPassword("mySecretPassword123");

        when(certificateService.canUserUsePasswordManager(testUser)).thenReturn(false);

        assertThrows(RuntimeException.class, () -> {
            passwordManagerService.createPasswordEntry(createDto, testUser);
        });
    }

    // ===== GET PASSWORD ENTRIES TESTS =====

    @Test
    void getUserPasswordEntries_WithValidCertificate_ShouldReturnEntries() {
        List<PasswordEntry> userEntries = Collections.singletonList(testPasswordEntry);

        when(certificateService.canUserUsePasswordManager(testUser)).thenReturn(true);
        when(passwordEntryRepository.findByOwner(testUser)).thenReturn(userEntries);
        when(passwordShareRepository.findSharedPasswordEntriesByUser(testUser))
                .thenReturn(Collections.emptyList());

        List<PasswordEntryDTO> result = passwordManagerService.getUserPasswordEntries(testUser);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Gmail", result.get(0).getSiteName());
    }

    @Test
    void getUserPasswordEntries_WithSharedEntries_ShouldReturnCombinedList() {
        PasswordEntry sharedEntry = new PasswordEntry();
        sharedEntry.setId(2L);
        sharedEntry.setSiteName("Netflix");
        sharedEntry.setUsername("shared@example.com");
        sharedEntry.setOwner(targetUser);

        PasswordShare sharedShare = new PasswordShare();
        sharedShare.setPasswordEntry(sharedEntry);
        sharedShare.setUser(testUser);

        when(certificateService.canUserUsePasswordManager(testUser)).thenReturn(true);
        when(passwordEntryRepository.findByOwner(testUser))
                .thenReturn(Collections.singletonList(testPasswordEntry));
        when(passwordShareRepository.findSharedPasswordEntriesByUser(testUser))
                .thenReturn(Collections.singletonList(sharedEntry));

        List<PasswordEntryDTO> result = passwordManagerService.getUserPasswordEntries(testUser);

        assertNotNull(result);
        assertEquals(2, result.size());
    }

    @Test
    void getUserPasswordEntries_WithoutValidCertificate_ShouldThrowException() {
        when(certificateService.canUserUsePasswordManager(testUser)).thenReturn(false);

        assertThrows(RuntimeException.class, () -> {
            passwordManagerService.getUserPasswordEntries(testUser);
        });
    }

    // ===== SHARE PASSWORD TESTS =====

    @Test
    void sharePasswordEntry_WithValidUsers_ShouldCreateShare() {
        SharePasswordRequestDTO shareRequest = new SharePasswordRequestDTO();
        shareRequest.setPasswordEntryId(1L);
        shareRequest.setTargetUserEmail("target@example.com");
        shareRequest.setPlainTextPassword("plainTextPassword");

        when(certificateService.canUserUsePasswordManager(testUser)).thenReturn(true);
        when(passwordEntryRepository.findByIdAndOwner(1L, testUser))
                .thenReturn(Optional.of(testPasswordEntry));
        when(userRepository.findByEmail("target@example.com"))
                .thenReturn(Optional.of(targetUser));
        when(certificateService.canUserUsePasswordManager(targetUser)).thenReturn(true);
        when(passwordShareRepository.existsByUserAndPasswordEntry(targetUser, testPasswordEntry))
                .thenReturn(false);
        when(passwordShareRepository.findByUserAndPasswordEntry(testUser, testPasswordEntry))
                .thenReturn(Optional.of(testPasswordShare));
        when(certificateService.findValidEndEntityCertificateByOwner(targetUser))
                .thenReturn(Optional.of(validCertificate));
        when(encryptionService.encryptWithPublicKey(anyString(), anyString()))
                .thenReturn("encrypted-for-target");

        assertDoesNotThrow(() -> {
            passwordManagerService.sharePasswordEntry(shareRequest, testUser);
        });

        verify(passwordShareRepository, times(1)).save(any(PasswordShare.class));
    }

    @Test
    void sharePasswordEntry_WhenEntryNotFound_ShouldThrowException() {
        SharePasswordRequestDTO shareRequest = new SharePasswordRequestDTO();
        shareRequest.setPasswordEntryId(999L);
        shareRequest.setTargetUserEmail("target@example.com");

        when(certificateService.canUserUsePasswordManager(testUser)).thenReturn(true);
        when(passwordEntryRepository.findByIdAndOwner(999L, testUser))
                .thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> {
            passwordManagerService.sharePasswordEntry(shareRequest, testUser);
        });
    }

    @Test
    void sharePasswordEntry_WhenTargetUserNotFound_ShouldThrowException() {
        SharePasswordRequestDTO shareRequest = new SharePasswordRequestDTO();
        shareRequest.setPasswordEntryId(1L);
        shareRequest.setTargetUserEmail("nonexistent@example.com");

        when(certificateService.canUserUsePasswordManager(testUser)).thenReturn(true);
        when(passwordEntryRepository.findByIdAndOwner(1L, testUser))
                .thenReturn(Optional.of(testPasswordEntry));
        when(userRepository.findByEmail("nonexistent@example.com"))
                .thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> {
            passwordManagerService.sharePasswordEntry(shareRequest, testUser);
        });
    }

    @Test
    void sharePasswordEntry_WhenTargetUserNoCertificate_ShouldThrowException() {
        SharePasswordRequestDTO shareRequest = new SharePasswordRequestDTO();
        shareRequest.setPasswordEntryId(1L);
        shareRequest.setTargetUserEmail("target@example.com");

        when(certificateService.canUserUsePasswordManager(testUser)).thenReturn(true);
        when(passwordEntryRepository.findByIdAndOwner(1L, testUser))
                .thenReturn(Optional.of(testPasswordEntry));
        when(userRepository.findByEmail("target@example.com"))
                .thenReturn(Optional.of(targetUser));
        when(certificateService.canUserUsePasswordManager(targetUser)).thenReturn(false);

        assertThrows(RuntimeException.class, () -> {
            passwordManagerService.sharePasswordEntry(shareRequest, testUser);
        });
    }

    @Test
    void sharePasswordEntry_WhenAlreadyShared_ShouldThrowException() {
        SharePasswordRequestDTO shareRequest = new SharePasswordRequestDTO();
        shareRequest.setPasswordEntryId(1L);
        shareRequest.setTargetUserEmail("target@example.com");

        when(certificateService.canUserUsePasswordManager(testUser)).thenReturn(true);
        when(passwordEntryRepository.findByIdAndOwner(1L, testUser))
                .thenReturn(Optional.of(testPasswordEntry));
        when(userRepository.findByEmail("target@example.com"))
                .thenReturn(Optional.of(targetUser));
        when(certificateService.canUserUsePasswordManager(targetUser)).thenReturn(true);
        when(passwordShareRepository.existsByUserAndPasswordEntry(targetUser, testPasswordEntry))
                .thenReturn(true);

        assertThrows(RuntimeException.class, () -> {
            passwordManagerService.sharePasswordEntry(shareRequest, testUser);
        });
    }

    // ===== DELETE PASSWORD ENTRY TESTS =====

    @Test
    void deletePasswordEntry_WhenUserOwnsEntry_ShouldDelete() {
        when(passwordEntryRepository.findByIdAndOwner(1L, testUser))
                .thenReturn(Optional.of(testPasswordEntry));

        assertDoesNotThrow(() -> {
            passwordManagerService.deletePasswordEntry(1L, testUser);
        });

        verify(passwordShareRepository, times(1)).deleteByPasswordEntry(testPasswordEntry);
        verify(passwordEntryRepository, times(1)).delete(testPasswordEntry);
    }

    @Test
    void deletePasswordEntry_WhenEntryNotFound_ShouldThrowException() {
        when(passwordEntryRepository.findByIdAndOwner(999L, testUser))
                .thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> {
            passwordManagerService.deletePasswordEntry(999L, testUser);
        });
    }

    // ===== UPDATE PASSWORD ENTRY TESTS =====

    @Test
    void updatePasswordEntry_WithValidData_ShouldUpdate() {
        CreatePasswordEntryDTO updateDto = new CreatePasswordEntryDTO();
        updateDto.setSiteName("Updated Gmail");
        updateDto.setUsername("updated@gmail.com");
        updateDto.setNotes("Updated notes");
        // No password change

        when(passwordEntryRepository.findByIdAndOwner(1L, testUser))
                .thenReturn(Optional.of(testPasswordEntry));
        when(passwordEntryRepository.save(any(PasswordEntry.class))).thenReturn(testPasswordEntry);

        PasswordEntryDTO result = passwordManagerService.updatePasswordEntry(1L, updateDto, testUser);

        assertNotNull(result);
        assertEquals("Updated Gmail", result.getSiteName());
        assertEquals("updated@gmail.com", result.getUsername());
        verify(passwordEntryRepository, times(1)).save(testPasswordEntry);
    }

    @Test
    void updatePasswordEntry_WithPasswordChange_ShouldUpdateAllShares() {
        CreatePasswordEntryDTO updateDto = new CreatePasswordEntryDTO();
        updateDto.setSiteName("Gmail");
        updateDto.setUsername("testuser@gmail.com");
        updateDto.setPassword("newPassword123");
        updateDto.setNotes("Notes");

        // Kreiraj certificate sa dužim public key-om
        Certificate certificateWithLongKey = new Certificate();
        certificateWithLongKey.setId(1L);
        certificateWithLongKey.setSerialNumber("EE-12345");
        certificateWithLongKey.setStatus(CertificateStatus.VALID);
        certificateWithLongKey.setType(CertificateType.END_ENTITY);
        certificateWithLongKey.setOwner(testUser);
        certificateWithLongKey.setPublicKey("very-long-public-key-base64-encoding-that-is-more-than-50-characters-long-for-testing");

        PasswordShare existingShare = new PasswordShare();
        existingShare.setUser(testUser);
        existingShare.setEncryptedPassword("old-encrypted-password");

        when(passwordEntryRepository.findByIdAndOwner(1L, testUser))
                .thenReturn(Optional.of(testPasswordEntry));
        when(passwordShareRepository.findByPasswordEntry(testPasswordEntry))
                .thenReturn(Collections.singletonList(existingShare));
        when(certificateService.findValidEndEntityCertificateByOwner(testUser))
                .thenReturn(Optional.of(certificateWithLongKey));
        when(encryptionService.encryptWithPublicKey(anyString(), anyString()))
                .thenReturn("new-encrypted-password");
        when(passwordEntryRepository.save(any(PasswordEntry.class))).thenReturn(testPasswordEntry);

        PasswordEntryDTO result = passwordManagerService.updatePasswordEntry(1L, updateDto, testUser);

        assertNotNull(result);
        verify(passwordShareRepository, times(1)).save(existingShare);
        assertEquals("new-encrypted-password", existingShare.getEncryptedPassword());
    }

    // ===== GET SHARED USERS TESTS =====

    @Test
    void getSharedUsersForEntry_ShouldReturnSharedUsers() {
        PasswordShare shareWithTarget = new PasswordShare();
        shareWithTarget.setUser(targetUser);

        when(passwordEntryRepository.findByIdAndOwner(1L, testUser))
                .thenReturn(Optional.of(testPasswordEntry));
        when(passwordShareRepository.findByPasswordEntry(testPasswordEntry))
                .thenReturn(Arrays.asList(testPasswordShare, shareWithTarget));

        List<User> result = passwordManagerService.getSharedUsersForEntry(1L, testUser);

        assertNotNull(result);
        assertEquals(1, result.size()); // Should exclude owner
        assertEquals(targetUser.getId(), result.get(0).getId());
    }

    @Test
    void getSharedUsersForEntry_WhenNoSharedUsers_ShouldReturnEmptyList() {
        when(passwordEntryRepository.findByIdAndOwner(1L, testUser))
                .thenReturn(Optional.of(testPasswordEntry));
        when(passwordShareRepository.findByPasswordEntry(testPasswordEntry))
                .thenReturn(Collections.singletonList(testPasswordShare)); // Only owner

        List<User> result = passwordManagerService.getSharedUsersForEntry(1L, testUser);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }
}
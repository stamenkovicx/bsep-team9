package com.bsep.pki_system.controller;

import com.bsep.pki_system.dto.*;
import com.bsep.pki_system.jwt.UserPrincipal;
import com.bsep.pki_system.model.PasswordShare;
import com.bsep.pki_system.model.User;
import com.bsep.pki_system.repository.PasswordShareRepository;
import com.bsep.pki_system.service.PasswordManagerService;
import com.bsep.pki_system.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import com.bsep.pki_system.audit.AuditLogService;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/passwords")
@PreAuthorize("isAuthenticated()") // SVI ulogovani korisnici mogu da pristupe
public class PasswordManagerController {

    @Autowired
    private PasswordManagerService passwordManagerService;

    @Autowired
    private UserService userService;
    private PasswordShareRepository passwordShareRepository;

    private final AuditLogService auditLogService;

    public PasswordManagerController(PasswordManagerService passwordManagerService,
                                     UserService userService,
                                     PasswordShareRepository passwordShareRepository,
                                     AuditLogService auditLogService) {
        this.passwordManagerService = passwordManagerService;
        this.userService = userService;
        this.passwordShareRepository = passwordShareRepository;
        this.auditLogService = auditLogService;
    }

    // Kreiraj novi password entry
    @PostMapping
    public ResponseEntity<PasswordEntryDTO> createPasswordEntry(
            @RequestBody CreatePasswordEntryDTO createDto,
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            HttpServletRequest httpRequest) {

        User currentUser = userService.findByEmail(userPrincipal.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Provera da li korisnik ima EE sertifikat
        PasswordEntryDTO createdEntry = passwordManagerService.createPasswordEntry(createDto, currentUser);

        // AUDIT LOG: Password entry kreiran
        auditLogService.logSecurityEvent(AuditLogService.EVENT_PASSWORD_SAVED,
                "Password entry created", true,
                "entryId=" + createdEntry.getId() + ", site=" + createdEntry.getSiteName() +
                        ", username=" + createdEntry.getUsername(), httpRequest);

        return ResponseEntity.ok(createdEntry);
    }

    // Dohvati sve password entry-je za trenutnog korisnika
    @GetMapping
    public ResponseEntity<List<PasswordEntryDTO>> getUserPasswordEntries(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            HttpServletRequest httpRequest) {

        User currentUser = userService.findByEmail(userPrincipal.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<PasswordEntryDTO> entries = passwordManagerService.getUserPasswordEntries(currentUser);

        // AUDIT LOG: Pregled password entries
        auditLogService.logSecurityEvent(AuditLogService.EVENT_PASSWORD_VIEWED,
                "Password entries list accessed", true,
                "count=" + entries.size(), httpRequest);

        return ResponseEntity.ok(entries);
    }

    // Deli password entry sa drugim korisnikom
    @PostMapping("/share")
    public ResponseEntity<?> sharePasswordEntry(
            @RequestBody SharePasswordRequestDTO shareRequest,
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            HttpServletRequest httpRequest) {

        User currentUser = userService.findByEmail(userPrincipal.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));

        passwordManagerService.sharePasswordEntry(shareRequest, currentUser);

        // AUDIT LOG: Password deljen
        auditLogService.logSecurityEvent(AuditLogService.EVENT_PASSWORD_SHARED,
                "Password entry shared with user", true,
                "entryId=" + shareRequest.getPasswordEntryId() +
                        ", sharedWithUserEmail=" + shareRequest.getTargetUserEmail(), httpRequest);

        return ResponseEntity.ok().build();
    }

    // Obriši password entry
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deletePasswordEntry(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            HttpServletRequest httpRequest) {

        User currentUser = userService.findByEmail(userPrincipal.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));

        passwordManagerService.deletePasswordEntry(id, currentUser);

        // AUDIT LOG: Password obrisan
        auditLogService.logSecurityEvent(AuditLogService.EVENT_PASSWORD_DELETED,
                "Password entry deleted", true,
                "entryId=" + id, httpRequest);

        return ResponseEntity.ok().build();
    }

    // Ažuriraj password entry
    @PutMapping("/{id}")
    public ResponseEntity<PasswordEntryDTO> updatePasswordEntry(
            @PathVariable Long id,
            @RequestBody CreatePasswordEntryDTO updateDto,
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            HttpServletRequest httpRequest) {

        User currentUser = userService.findByEmail(userPrincipal.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));

        PasswordEntryDTO updatedEntry = passwordManagerService.updatePasswordEntry(id, updateDto, currentUser);

        // AUDIT LOG: Password ažuriran
        auditLogService.logSecurityEvent(AuditLogService.EVENT_PASSWORD_UPDATED,
                "Password entry updated", true,
                "entryId=" + id + ", site=" + updatedEntry.getSiteName(), httpRequest);

        return ResponseEntity.ok(updatedEntry);
    }

    // Dohvati korisnike sa kojima je deljen password entry
    @GetMapping("/{id}/shared-users")
    public ResponseEntity<List<User>> getSharedUsersForEntry(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {

        User currentUser = userService.findByEmail(userPrincipal.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<User> sharedUsers = passwordManagerService.getSharedUsersForEntry(id, currentUser);
        return ResponseEntity.ok(sharedUsers);
    }

    @GetMapping("/{id}/encrypted-password")
    public ResponseEntity<String> getEncryptedPassword(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            HttpServletRequest httpRequest) {

        User currentUser = userService.findByEmail(userPrincipal.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Pronađi password share za ovog korisnika
        PasswordShare passwordShare = passwordShareRepository.findByUserAndPasswordEntryId(currentUser, id)
                .orElseThrow(() -> new RuntimeException("Password not found or access denied"));

        // AUDIT LOG: Pristup enkriptovanom passwordu
        auditLogService.logSecurityEvent(AuditLogService.EVENT_PASSWORD_VIEWED,
                "Encrypted password accessed", true,
                "entryId=" + id + ", shareId=" + passwordShare.getId(), httpRequest);

        return ResponseEntity.ok(passwordShare.getEncryptedPassword());
    }
}
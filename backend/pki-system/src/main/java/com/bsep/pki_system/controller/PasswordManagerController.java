package com.bsep.pki_system.controller;

import com.bsep.pki_system.dto.*;
import com.bsep.pki_system.jwt.UserPrincipal;
import com.bsep.pki_system.model.User;
import com.bsep.pki_system.service.PasswordManagerService;
import com.bsep.pki_system.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/passwords")
@PreAuthorize("isAuthenticated()") // SVI ulogovani korisnici mogu da pristupe
public class PasswordManagerController {

    @Autowired
    private PasswordManagerService passwordManagerService;

    @Autowired
    private UserService userService;

    // Kreiraj novi password entry
    @PostMapping
    public ResponseEntity<PasswordEntryDTO> createPasswordEntry(
            @RequestBody CreatePasswordEntryDTO createDto,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {

        User currentUser = userService.findByEmail(userPrincipal.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Provera da li korisnik ima EE sertifikat
        PasswordEntryDTO createdEntry = passwordManagerService.createPasswordEntry(createDto, currentUser);
        return ResponseEntity.ok(createdEntry);
    }

    // Dohvati sve password entry-je za trenutnog korisnika
    @GetMapping
    public ResponseEntity<List<PasswordEntryDTO>> getUserPasswordEntries(
            @AuthenticationPrincipal UserPrincipal userPrincipal) {

        User currentUser = userService.findByEmail(userPrincipal.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<PasswordEntryDTO> entries = passwordManagerService.getUserPasswordEntries(currentUser);
        return ResponseEntity.ok(entries);
    }

    // Deli password entry sa drugim korisnikom
    @PostMapping("/share")
    public ResponseEntity<?> sharePasswordEntry(
            @RequestBody SharePasswordRequestDTO shareRequest,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {

        User currentUser = userService.findByEmail(userPrincipal.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));

        passwordManagerService.sharePasswordEntry(shareRequest, currentUser);
        return ResponseEntity.ok().build();
    }

    // Obriši password entry
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deletePasswordEntry(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {

        User currentUser = userService.findByEmail(userPrincipal.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));

        passwordManagerService.deletePasswordEntry(id, currentUser);
        return ResponseEntity.ok().build();
    }

    // Ažuriraj password entry
    @PutMapping("/{id}")
    public ResponseEntity<PasswordEntryDTO> updatePasswordEntry(
            @PathVariable Long id,
            @RequestBody CreatePasswordEntryDTO updateDto,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {

        User currentUser = userService.findByEmail(userPrincipal.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));

        PasswordEntryDTO updatedEntry = passwordManagerService.updatePasswordEntry(id, updateDto, currentUser);
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
}
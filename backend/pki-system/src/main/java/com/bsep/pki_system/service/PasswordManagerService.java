package com.bsep.pki_system.service;

import com.bsep.pki_system.dto.*;
import com.bsep.pki_system.model.*;
import com.bsep.pki_system.repository.PasswordEntryRepository;
import com.bsep.pki_system.repository.PasswordShareRepository;
import com.bsep.pki_system.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional
public class PasswordManagerService {

    @Autowired
    private PasswordEntryRepository passwordEntryRepository;

    @Autowired
    private PasswordShareRepository passwordShareRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CertificateService certificateService;

    @Autowired
    private EncryptionService encryptionService;

    @Autowired
    private UserService userService;

    // Kreiraj novi password entry
    public PasswordEntryDTO createPasswordEntry(CreatePasswordEntryDTO createDto, User owner) {
        // Proveri da li korisnik može da koristi password manager
        if (!certificateService.canUserUsePasswordManager(owner)) {
            throw new RuntimeException("User does not have a valid End Entity certificate for password manager");
        }

        // Pronađi aktivni EE sertifikat korisnika za enkripciju
        Certificate userCertificate = certificateService.findValidEndEntityCertificateByOwner(owner)
                .orElseThrow(() -> new RuntimeException("User does not have a valid End Entity certificate"));

        // Enkriptuj lozinku korisničkim javnim ključem
        String encryptedPassword = encryptionService.encryptWithPublicKey(
                createDto.getPassword(),
                userCertificate.getPublicKey()
        );

        // Kreiraj password entry
        PasswordEntry entry = new PasswordEntry();
        entry.setSiteName(createDto.getSiteName());
        entry.setUsername(createDto.getUsername());
        entry.setOwner(owner);
        entry.setNotes(createDto.getNotes());

        PasswordEntry savedEntry = passwordEntryRepository.save(entry);

        // Kreiraj password share za vlasnika (prvi share)
        PasswordShare ownerShare = new PasswordShare();
        ownerShare.setPasswordEntry(savedEntry);
        ownerShare.setUser(owner);
        ownerShare.setEncryptedPassword(encryptedPassword);
        ownerShare.setSharedBy(owner);

        passwordShareRepository.save(ownerShare);

        return convertToDTO(savedEntry);
    }

    // Dohvati sve password entry-je za korisnika (samo meta podaci)
    public List<PasswordEntryDTO> getUserPasswordEntries(User user) {
        // Proveri da li korisnik može da koristi password manager
        if (!certificateService.canUserUsePasswordManager(user)) {
            throw new RuntimeException("User does not have a valid End Entity certificate for password manager");
        }

        List<PasswordEntry> userEntries = passwordEntryRepository.findByOwner(user);
        List<PasswordEntry> sharedEntries = passwordShareRepository.findSharedPasswordEntriesByUser(user);

        // Kombinuj lične i deljene entry-je
        List<PasswordEntry> allEntries = new ArrayList<>();
        allEntries.addAll(userEntries);
        allEntries.addAll(sharedEntries.stream()
                .filter(entry -> !userEntries.contains(entry))
                .collect(Collectors.toList()));

        return allEntries.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    // Deli password entry sa drugim korisnikom
    public void sharePasswordEntry(SharePasswordRequestDTO shareRequest, User sharer) {
        // Proveri da li sharer može da koristi password manager
        if (!certificateService.canUserUsePasswordManager(sharer)) {
            throw new RuntimeException("Sharer does not have a valid End Entity certificate for password manager");
        }

        PasswordEntry entry = passwordEntryRepository.findByIdAndOwner(shareRequest.getPasswordEntryId(), sharer)
                .orElseThrow(() -> new RuntimeException("Password entry not found or access denied"));

        User targetUser = userRepository.findByEmail(shareRequest.getTargetUserEmail())
                .orElseThrow(() -> new RuntimeException("Target user not found"));

        // Proveri da li target user može da koristi password manager
        if (!certificateService.canUserUsePasswordManager(targetUser)) {
            throw new RuntimeException("Target user does not have a valid End Entity certificate for password manager");
        }

        // Proveri da li je već deljeno
        if (passwordShareRepository.existsByUserAndPasswordEntry(targetUser, entry)) {
            throw new RuntimeException("Password already shared with this user");
        }

        // Pronađi originalnu enkriptovanu lozinku (sharer-ovu verziju)
        PasswordShare sharerShare = passwordShareRepository.findByUserAndPasswordEntry(sharer, entry)
                .orElseThrow(() -> new RuntimeException("Sharer does not have access to this password"));

        // Dekriptuj lozinku (OVAJ DEO ĆE SE RADITI NA FRONTENDU)
        // Za sada, koristimo sharer-ovu enkriptovanu lozinku
        String encryptedPassword = sharerShare.getEncryptedPassword();

        // Pronađi target user-ov sertifikat za re-encryption
        Certificate targetCertificate = certificateService.findValidEndEntityCertificateByOwner(targetUser)
                .orElseThrow(() -> new RuntimeException("Target user does not have a valid End Entity certificate"));

        // OVDE TREBA RE-ENKRIPCIJA SA TARGET USER-OVIM JAVNIM KLJUČEM
        // Za sada koristimo istu enkriptovanu lozinku (privremeno)
        String encryptedForTarget = encryptedPassword;

        // Kreiraj share
        PasswordShare share = new PasswordShare();
        share.setPasswordEntry(entry);
        share.setUser(targetUser);
        share.setEncryptedPassword(encryptedForTarget);
        share.setSharedBy(sharer);

        passwordShareRepository.save(share);
    }

    // Obriši password entry
    public void deletePasswordEntry(Long entryId, User user) {
        PasswordEntry entry = passwordEntryRepository.findByIdAndOwner(entryId, user)
                .orElseThrow(() -> new RuntimeException("Password entry not found or access denied"));

        // Obriši sve deljenja vezana za ovaj entry
        passwordShareRepository.deleteByPasswordEntry(entry);

        // Obriši sam entry
        passwordEntryRepository.delete(entry);
    }

    // Ažuriraj password entry
    public PasswordEntryDTO updatePasswordEntry(Long entryId, CreatePasswordEntryDTO updateDto, User user) {
        PasswordEntry entry = passwordEntryRepository.findByIdAndOwner(entryId, user)
                .orElseThrow(() -> new RuntimeException("Password entry not found or access denied"));

        // Ažuriraj osnovne podatke
        entry.setSiteName(updateDto.getSiteName());
        entry.setUsername(updateDto.getUsername());
        entry.setNotes(updateDto.getNotes());

        // Ako je promenjena lozinka, ažuriraj sve deljenja
        if (updateDto.getPassword() != null && !updateDto.getPassword().isEmpty()) {
            updatePasswordForAllShares(entry, updateDto.getPassword(), user);
        }

        PasswordEntry updatedEntry = passwordEntryRepository.save(entry);
        return convertToDTO(updatedEntry);
    }

    // Ažuriraj lozinku za sve deljenja
    private void updatePasswordForAllShares(PasswordEntry entry, String newPassword, User updater) {
        // Pronađi sve deljenja za ovaj entry
        List<PasswordShare> shares = passwordShareRepository.findByPasswordEntry(entry);

        for (PasswordShare share : shares) {
            // Pronađi sertifikat korisnika za koga je deljeno
            Certificate userCertificate = certificateService.findValidEndEntityCertificateByOwner(share.getUser())
                    .orElseThrow(() -> new RuntimeException("User certificate not found for: " + share.getUser().getEmail()));

            // Enkriptuj novu lozinku korisničkim javnim ključem
            String encryptedPassword = encryptionService.encryptWithPublicKey(newPassword, userCertificate.getPublicKey());

            // Ažuriraj deljenje
            share.setEncryptedPassword(encryptedPassword);
            passwordShareRepository.save(share);
        }
    }

    // Dohvati deljene korisnike za password entry
    public List<User> getSharedUsersForEntry(Long entryId, User owner) {
        PasswordEntry entry = passwordEntryRepository.findByIdAndOwner(entryId, owner)
                .orElseThrow(() -> new RuntimeException("Password entry not found or access denied"));

        List<PasswordShare> shares = passwordShareRepository.findByPasswordEntry(entry);

        return shares.stream()
                .map(PasswordShare::getUser)
                .filter(user -> !user.getId().equals(owner.getId())) // Isključi vlasnika
                .collect(Collectors.toList());
    }

    // Pomoćna metoda za konverziju u DTO
    private PasswordEntryDTO convertToDTO(PasswordEntry entry) {
        PasswordEntryDTO dto = new PasswordEntryDTO();
        dto.setId(entry.getId());
        dto.setSiteName(entry.getSiteName());
        dto.setUsername(entry.getUsername());
        dto.setCreatedAt(entry.getCreatedAt());
        dto.setUpdatedAt(entry.getUpdatedAt());
        dto.setNotes(entry.getNotes());
        dto.setOwnerId(entry.getOwner().getId());
        dto.setOwnerEmail(entry.getOwner().getEmail());
        return dto;
    }
}
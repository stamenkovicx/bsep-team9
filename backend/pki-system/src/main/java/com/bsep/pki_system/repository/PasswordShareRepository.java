package com.bsep.pki_system.repository;

import com.bsep.pki_system.model.PasswordEntry;
import com.bsep.pki_system.model.PasswordShare;
import com.bsep.pki_system.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PasswordShareRepository extends JpaRepository<PasswordShare, Long> {

    // Pronađi sva dijeljenja za određenog korisnika
    List<PasswordShare> findByUser(User user);

    // Pronađi sva dijeljenja za određeni password entry
    List<PasswordShare> findByPasswordEntry(PasswordEntry passwordEntry);

    // Pronađi specifično dijeljenje za korisnika i password entry
    Optional<PasswordShare> findByUserAndPasswordEntry(User user, PasswordEntry passwordEntry);

    // Provjeri da li je password entry već dijeljen sa korisnikom
    boolean existsByUserAndPasswordEntry(User user, PasswordEntry passwordEntry);

    // Pronađi sve password entry-je koji su dijeljeni sa korisnikom
    @Query("SELECT ps.passwordEntry FROM PasswordShare ps WHERE ps.user = :user")
    List<PasswordEntry> findSharedPasswordEntriesByUser(@Param("user") User user);

    // Obriši sva dijeljenja za određeni password entry
    void deleteByPasswordEntry(PasswordEntry passwordEntry);

    @Query("SELECT ps FROM PasswordShare ps WHERE ps.user = :user AND ps.passwordEntry.id = :passwordEntryId")
    Optional<PasswordShare> findByUserAndPasswordEntryId(@Param("user") User user, @Param("passwordEntryId") Long passwordEntryId);
}
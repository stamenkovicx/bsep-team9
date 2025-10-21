package com.bsep.pki_system.repository;

import com.bsep.pki_system.model.PasswordEntry;
import com.bsep.pki_system.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PasswordEntryRepository extends JpaRepository<PasswordEntry, Long> {

    // Pronađi sve password entry-je za vlasnika
    List<PasswordEntry> findByOwner(User owner);

    // Pronađi password entry po ID-u i vlasniku (za sigurnost)
    Optional<PasswordEntry> findByIdAndOwner(Long id, User owner);

    // Pronađi sve password entry-je sa određenim site name-om za vlasnika
    List<PasswordEntry> findBySiteNameAndOwner(String siteName, User owner);

    // Broj password entry-ja po vlasniku
    Long countByOwner(User owner);
}
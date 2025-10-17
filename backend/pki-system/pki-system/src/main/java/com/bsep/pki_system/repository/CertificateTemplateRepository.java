package com.bsep.pki_system.repository;

import com.bsep.pki_system.model.CertificateTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CertificateTemplateRepository extends JpaRepository<CertificateTemplate, Long> {

    // Pronalazi šablone po nazivu
    Optional<CertificateTemplate> findByName(String name);

    // Pronalazi sve šablone za određenog CA issuer-a
    List<CertificateTemplate> findByCaIssuerId(Long caIssuerId);

    // Pronalazi šablone po CA issuer-u i organizaciji
    @Query("SELECT ct FROM CertificateTemplate ct WHERE ct.caIssuer.owner.organization = :organization")
    List<CertificateTemplate> findByOrganization(@Param("organization") String organization);

    // Pronalazi šablone koje je kreirao određeni korisnik
    List<CertificateTemplate> findByCreatedById(Long userId);

    // Proverava da li šablon sa datim nazivom već postoji
    boolean existsByName(String name);

    // Pronalazi sve šablone za CA issuer-e iz određene organizacije
    @Query("SELECT ct FROM CertificateTemplate ct WHERE ct.caIssuer.owner.organization = :organization AND ct.caIssuer.isCA = true")
    List<CertificateTemplate> findTemplatesForOrganizationCAs(@Param("organization") String organization);
}
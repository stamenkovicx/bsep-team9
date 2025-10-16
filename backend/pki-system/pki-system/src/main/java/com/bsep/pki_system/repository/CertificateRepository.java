package com.bsep.pki_system.repository;

import com.bsep.pki_system.model.Certificate;
import com.bsep.pki_system.model.CertificateType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CertificateRepository extends JpaRepository<Certificate, Long> {
    Optional<Certificate> findBySerialNumber(String serialNumber);
    List<Certificate> findByType(CertificateType type);
    List<Certificate> findByOwnerId(Long ownerId);
    List<Certificate> findByIssuerCertificateId(Long issuerId);
    boolean existsBySerialNumber(String serialNumber);
}
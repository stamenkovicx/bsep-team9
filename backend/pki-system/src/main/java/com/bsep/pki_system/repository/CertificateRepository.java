package com.bsep.pki_system.repository;

import com.bsep.pki_system.model.Certificate;
import com.bsep.pki_system.model.CertificateStatus;
import com.bsep.pki_system.model.CertificateType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;
import java.util.Optional;

@Repository
public interface CertificateRepository extends JpaRepository<Certificate, Long> {
    Optional<Certificate> findBySerialNumber(String serialNumber);
    List<Certificate> findByType(CertificateType type);
    List<Certificate> findByOwnerId(Long ownerId);
    List<Certificate> findByIssuerCertificateId(Long issuerId);
    boolean existsBySerialNumber(String serialNumber);

    // Pronalazi sve sertifikate koji su CA, validni su i nisu istekli
    @Query("SELECT c FROM Certificate c WHERE c.isCA = true AND c.status = :status AND c.validTo > :currentDate")
    List<Certificate> findValidIssuers(@Param("status") CertificateStatus status, @Param("currentDate") Date currentDate);

    @Query("SELECT c FROM Certificate c WHERE c.owner.id = :ownerId AND c.type = :type")
    List<Certificate> findByOwnerIdAndType(@Param("ownerId") Long ownerId, @Param("type") CertificateType type);
}
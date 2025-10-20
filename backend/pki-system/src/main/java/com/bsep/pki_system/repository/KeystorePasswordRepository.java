package com.bsep.pki_system.repository;

import com.bsep.pki_system.model.KeystorePassword;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface KeystorePasswordRepository extends JpaRepository<KeystorePassword, Long> {

    Optional<KeystorePassword> findByCertificateId(Long certificateId);

    @Query("SELECT kp FROM KeystorePassword kp WHERE kp.certificate.serialNumber = :serialNumber")
    Optional<KeystorePassword> findByCertificateSerialNumber(@Param("serialNumber") String serialNumber);

    boolean existsByCertificateId(Long certificateId);

    void deleteByCertificateId(Long certificateId);
}
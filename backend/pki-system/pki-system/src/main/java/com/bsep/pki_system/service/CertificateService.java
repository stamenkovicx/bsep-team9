package com.bsep.pki_system.service;

import com.bsep.pki_system.model.Certificate;
import com.bsep.pki_system.model.CertificateStatus;
import com.bsep.pki_system.model.CertificateType;
import com.bsep.pki_system.model.User;
import com.bsep.pki_system.repository.CertificateRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.Optional;

@Service
public class CertificateService {

    private final CertificateRepository certificateRepository;

    public CertificateService(CertificateRepository certificateRepository) {
        this.certificateRepository = certificateRepository;
    }

    public Certificate saveCertificate(Certificate certificate) {
        return certificateRepository.save(certificate);
    }

    public Optional<Certificate> findById(Long id) {
        return certificateRepository.findById(id);
    }

    public Optional<Certificate> findBySerialNumber(String serialNumber) {
        return certificateRepository.findBySerialNumber(serialNumber);
    }

    public List<Certificate> findAll() {
        return certificateRepository.findAll();
    }

    public List<Certificate> findByType(CertificateType type) {
        return certificateRepository.findByType(type);
    }

    public List<Certificate> findByOwner(User owner) {
        return certificateRepository.findByOwnerId(owner.getId());
    }

    public List<Certificate> findByIssuer(Certificate issuer) {
        return certificateRepository.findByIssuerCertificateId(issuer.getId());
    }

    public boolean isCertificateValid(Long certificateId) {
        Optional<Certificate> certificateOpt = certificateRepository.findById(certificateId);
        if (certificateOpt.isEmpty()) {
            return false;
        }

        Certificate certificate = certificateOpt.get();
        Date now = new Date();

        return certificate.getStatus() == CertificateStatus.VALID &&
                certificate.getValidFrom().before(now) &&
                certificate.getValidTo().after(now);
    }

    public void revokeCertificate(Long certificateId, String reason) {
        Optional<Certificate> certificateOpt = certificateRepository.findById(certificateId);
        if (certificateOpt.isPresent()) {
            Certificate certificate = certificateOpt.get();
            certificate.setStatus(CertificateStatus.REVOKED);
            certificate.setRevocationReason(reason);
            certificate.setRevokedAt(LocalDateTime.now());
            certificateRepository.save(certificate);
        }
    }
}
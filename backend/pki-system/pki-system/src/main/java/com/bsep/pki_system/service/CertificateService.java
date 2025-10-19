package com.bsep.pki_system.service;

import com.bsep.pki_system.dto.CreateCertificateDTO;
import com.bsep.pki_system.model.*;
import com.bsep.pki_system.repository.CertificateRepository;
import org.springframework.context.annotation.Lazy;
import org.bouncycastle.cert.X509CRLHolder;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.security.spec.X509EncodedKeySpec;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class CertificateService {

    private final CertificateRepository certificateRepository;
    private final CertificateGeneratorService certificateGeneratorService;
    private final KeystoreService keystoreService;
    private final CRLService crlService;


    public CertificateService(CertificateRepository certificateRepository,
                              @Lazy CertificateGeneratorService certificateGeneratorService,
                              KeystoreService keystoreService, CRLService crlService) {
        this.certificateRepository = certificateRepository;
        this.certificateGeneratorService = certificateGeneratorService;
        this.keystoreService = keystoreService;
        this.crlService = crlService;
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
        // 1. Proverava status samog sertifikata (da li je povučen)
        // 2. Proverava period važenja samog sertifikata
        // 3. I na kraju, POZIVA isChainValid da proveri ceo lanac iznad
        return certificate.getStatus() == CertificateStatus.VALID &&
                certificate.getValidFrom().before(now) &&
                certificate.getValidTo().after(now) &&
                isChainValid(certificate);
    }

    public void revokeCertificate(Long certificateId, String reason) {
        Optional<Certificate> certificateOpt = certificateRepository.findById(certificateId);
        if (certificateOpt.isPresent()) {
            Certificate certificate = certificateOpt.get();
            certificate.setStatus(CertificateStatus.REVOKED);
            certificate.setRevocationReason(reason);
            certificate.setRevokedAt(LocalDateTime.now());
            certificateRepository.save(certificate);
            try {
                if (certificate.getIssuerCertificate() != null) {
                    // Ovo je Intermediate ili EE sertifikat. Obrisi kes njegovog IZDAVAOCA.
                    crlService.clearCache(certificate.getIssuerCertificate().getSerialNumber());
                } else {
                    // Ovo je Root sertifikat. Obrisi kes za NJEGA SAMOG.
                    crlService.clearCache(certificate.getSerialNumber());
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Provjerava da li korisnik može da pristupi sertifikatu
     */
    public boolean canUserAccessCertificate(Long certificateId, User user) {
        Optional<Certificate> certificateOpt = certificateRepository.findById(certificateId);
        if (certificateOpt.isEmpty()) {
            return false;
        }

        Certificate certificate = certificateOpt.get();

        // ADMIN može da pristupi svim sertifikatima
        if (user.getRole() == UserRole.ADMIN) {
            return true;
        }

        // CA korisnik može da pristupi sertifikatima iz svog lanca
        if (user.getRole() == UserRole.CA) {
            return isCertificateInUserOrganizationChain(certificate, user.getOrganization());
        }

        // BASIC korisnik može da pristupi samo svojim EE sertifikatima
        if (user.getRole() == UserRole.BASIC) {
            return certificate.getOwner().getId().equals(user.getId()) &&
                    certificate.getType() == CertificateType.END_ENTITY;
        }

        return false;
    }

    public Certificate createAndSaveIntermediateCertificate(CreateCertificateDTO request, User owner) throws Exception {
        // 1. Pronađi sertifikat izdavaoca (issuer)
        Certificate issuerCertificate = certificateRepository.findById(request.getIssuerCertificateId())
                .orElseThrow(() -> new IllegalArgumentException("Issuer certificate with ID " + request.getIssuerCertificateId() + " not found."));

        // 2. Validacija izdavaoca
        validateIssuerForSigning(issuerCertificate, request);

        // 3. Ako je sve u redu, generiši sertifikat
        Certificate intermediateCert = certificateGeneratorService.generateIntermediateCertificate(request, owner, issuerCertificate);

        // 4. Sačuvaj ga u bazi
        return saveCertificate(intermediateCert);
    }

    private void validateIssuerForSigning(Certificate issuer, CreateCertificateDTO newCertRequest) {
        // Provera statusa (da li je povucen)
        if (issuer.getStatus() != CertificateStatus.VALID) {
            throw new IllegalArgumentException("Issuer certificate is not valid (status: " + issuer.getStatus() + ").");
        }

        // Provera da li je uopste CA
        if (issuer.getIsCA() == null || !issuer.getIsCA()) {
            throw new IllegalArgumentException("Issuer certificate is not a CA and cannot sign other certificates.");
        }

        // Provera datuma vazenja samog izdavaoca
        Date now = new Date();
        if (issuer.getValidFrom().after(now) || issuer.getValidTo().before(now)) {
            throw new IllegalArgumentException("Issuer certificate is expired or not yet valid.");
        }

        // Ključna provera: validnost novog sertifikata mora biti unutar validnosti izdavaoca
        if (newCertRequest.getValidFrom().before(issuer.getValidFrom()) || newCertRequest.getValidTo().after(issuer.getValidTo())) {
            throw new IllegalArgumentException("The new certificate's validity period must be within the issuer's validity period.");
        }
    }


    //Pronalazi listu validnih sertifikata za potpisivanje (issuers) na osnovu uloge ulogovanog korisnika
    public List<Certificate> findValidIssuersForUser(User user) {
        Date now = new Date();
        if (user.getRole() == UserRole.ADMIN) {
            // Admin može da koristi bilo koji validan CA sertifikat iz sistema
            return certificateRepository.findValidIssuers(CertificateStatus.VALID, now);
        }
        // CA korisnik može da koristi samo validne CA sertifikate iz svoje organizacije
        if (user.getRole() == UserRole.CA) {
            // Prvo dobavljamo SVE validne CA sertifikate
            List<Certificate> allValidIssuers = certificateRepository.findValidIssuers(CertificateStatus.VALID, now);

            // Zatim ih filtriramo koristeći ISTU logiku kao za /my-chain
            // Proveravamo da li sertifikat pripada lancu organizacije CA korisnika
            return allValidIssuers.stream()
                    .filter(cert -> isCertificateInUserOrganizationChain(cert, user.getOrganization()))
                    .collect(Collectors.toList());
        }
        // Ako uloga nije ni ADMIN ni CA, vrati praznu listu
        return Collections.emptyList();
    }

    //Pronalazi sve sertifikate koji pripadaju "lancu" ulogovanog korisnika.
    public List<Certificate> findCertificateChainForUser(User user) {
        // Admin uvek vidi sve sertifikate u sistemu.
        if (user.getRole() == UserRole.ADMIN) {
            return findAll();
        }

        // Za CA korisnika, pronalazimo sve sertifikate koji su deo lanca njegove organizacije.
        if (user.getRole() == UserRole.CA) {
            // 1. Prvo dobavi SVE sertifikate iz baze.
            List<Certificate> allCertificates = findAll();

            // 2. Filtriraj listu: zadrži samo one koji pripadaju lancu organizacije CA korisnika.
            return allCertificates.stream()
                    .filter(cert -> isCertificateInUserOrganizationChain(cert, user.getOrganization()))
                    .collect(Collectors.toList());
        }

        // Basic korisnik vidi samo sertifikate čiji je on vlasnik (owner).
        return findByOwner(user);
    }

    // proverava da li sertifikat pripada lancu određene organizacije.Prolazi uz lanac od datog sertifikata sve do Root-a.
    public boolean isCertificateInUserOrganizationChain(Certificate certificate, String userOrganization) {
        Certificate current = certificate;
        while (current != null) {
            // Izvlači organizaciju iz Subject polja trenutnog sertifikata u lancu.
            String certOrganization = getOrganizationFromSubject(current.getSubject());

            // Ako se organizacije poklapaju, sertifikat je deo lanca.
            if (userOrganization != null && userOrganization.equals(certOrganization)) {
                return true;
            }
            // Pređi na sledeći sertifikat u lancu (roditelja).
            current = current.getIssuerCertificate();
        }
        return false; // Nismo našli poklapanje u celom lancu.
    }

    //izvlači vrednost organizacije (O=) iz Subject stringa.
    private String getOrganizationFromSubject(String subject) {
        if (subject == null) return null;

        // Koristimo regularni izraz da pronađemo vrednost O=...
        Pattern pattern = Pattern.compile("O=([^,]+)");
        Matcher matcher = pattern.matcher(subject);

        if (matcher.find()) {
            return matcher.group(1); // Vraća samo tekst između "O=" i sledećeg zareza.
        }
        return null; // Nije pronađena organizacija.
    }

    private boolean isChainValid(Certificate certificate) {
        Certificate current = certificate;

        // Idemo uz lanac sve dok ne dođemo do Root-a (koji nema issuera)
        while (current.getIssuerCertificate() != null) {
            Certificate issuer = current.getIssuerCertificate();

            // Provera #1: Status i datum važenja roditelja
            if (issuer.getStatus() != CertificateStatus.VALID || issuer.getValidTo().before(new Date())) {
                return false;
            }

            // Provera #2: Ispravnost digitalnog potpisa
            try {
                // 1. Uzmi stvarni X509 objekat za "dete" iz keystore-a
                X509Certificate currentX509 = (X509Certificate) keystoreService.getCertificate("CA_" + current.getSerialNumber());

                // 2. Rekonstruiši javni ključ "roditelja" (izdavaoca) iz stringa u bazi
                byte[] issuerPublicKeyBytes = java.util.Base64.getDecoder().decode(issuer.getPublicKey());
                KeyFactory keyFactory = KeyFactory.getInstance("RSA");
                PublicKey issuerPublicKey = keyFactory.generatePublic(new X509EncodedKeySpec(issuerPublicKeyBytes));

                // 3. Verifikuj potpis deteta koristeći javni ključ roditelja
                //    Ako potpis nije ispravan, ova linija će baciti izuzetak
                currentX509.verify(issuerPublicKey);
            } catch (Exception e) {
                // to znači da potpis nije validan.
                e.printStackTrace();
                return false;
            }
            if (crlService.isCertificateRevoked(current, issuer)) {
                // Ako JESTE na listi, lanac NIJE validan
                return false;
            }
            // Predji na sledeci sertifikat u lancu
            current = issuer;
        }

        // Stigli smo do Root-a i svi potpisi u lancu su bili ispravni.
        return true;
    }
}
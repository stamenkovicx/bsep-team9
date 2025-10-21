package com.bsep.pki_system.util;

import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemWriter;
import java.io.StringWriter;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;

public class CertificateUtil {

    /**
     * Konvertuje X509Certificate objekat u PEM format (String).
     */
    public static String toPem(X509Certificate cert) throws CertificateEncodingException {
        try (StringWriter sw = new StringWriter();
             PemWriter pw = new PemWriter(sw)) {

            // Sertifikat se uvek piše kao "CERTIFICATE" objekat u PEM-u
            pw.writeObject(new PemObject("CERTIFICATE", cert.getEncoded()));
            pw.flush();
            return sw.toString();

        } catch (Exception e) {
            throw new CertificateEncodingException("Greška pri konvertovanju u PEM format", e);
        }
    }
}
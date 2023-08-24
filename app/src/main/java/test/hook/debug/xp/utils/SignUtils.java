package test.hook.debug.xp.utils;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.security.MessageDigest;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

public class SignUtils {
    public static byte[] generateSign(File file) throws Exception {
        byte[] sign;
        try (V2 v2 = new V2(file)) {
            v2.skipZipEntry();
            sign = v2.parseSign();
        }
        if (sign == null) {
            return null;
        }
        CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
        X509Certificate certificate = (X509Certificate) certificateFactory.generateCertificate(new ByteArrayInputStream(sign));
        return SignUtils.sha1(certificate.getPublicKey().getEncoded());
    }

    public static byte[] sha1(byte[] data) {
        try {
            MessageDigest instance = MessageDigest.getInstance("SHA1");
            return instance.digest(data);
        } catch (Exception ignored) {
            return null;
        }
    }
}

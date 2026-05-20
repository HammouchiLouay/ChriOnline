package common.crypto;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Parse des clés RSA encodées en PEM (PKCS8 private key / X509 public key).
 *
 * <p>Formats supportés :
 * <ul>
 *   <li>PrivateKey PEM : {@code BEGIN PRIVATE KEY}</li>
 *   <li>PublicKey PEM : {@code BEGIN PUBLIC KEY}</li>
 * </ul>
 */
public final class PemKeyUtil {

    private PemKeyUtil() {}

    public static PrivateKey parsePkcs8PrivateKeyPem(String pem) throws Exception {
        if (pem == null || pem.isBlank()) {
            throw new IllegalArgumentException("pem");
        }
        byte[] der = Base64.getDecoder().decode(stripPem(pem));
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(der);
        return KeyFactory.getInstance("RSA").generatePrivate(spec);
    }

    public static PublicKey parseX509PublicKeyPem(String pem) throws Exception {
        if (pem == null || pem.isBlank()) {
            throw new IllegalArgumentException("pem");
        }
        byte[] der = Base64.getDecoder().decode(stripPem(pem));
        X509EncodedKeySpec spec = new X509EncodedKeySpec(der);
        return KeyFactory.getInstance("RSA").generatePublic(spec);
    }

    private static String stripPem(String pem) {
        return pem.replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s+", "");
    }
}


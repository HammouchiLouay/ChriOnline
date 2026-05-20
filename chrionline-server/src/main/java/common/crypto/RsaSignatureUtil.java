package common.crypto;

import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;

/** Signature RSA SHA-256 (client) + vérification (serveur). */
public final class RsaSignatureUtil {

    private RsaSignatureUtil() {}

    public static byte[] signChallenge(String challenge, PrivateKey privateKey) throws Exception {
        if (challenge == null || privateKey == null) {
            throw new IllegalArgumentException("challenge/privateKey");
        }
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(privateKey);
        signature.update(challenge.getBytes(StandardCharsets.UTF_8));
        return signature.sign();
    }

    public static boolean verifyChallenge(String challenge, byte[] signatureBytes, PublicKey publicKey)
            throws Exception {
        if (challenge == null || signatureBytes == null || publicKey == null) {
            return false;
        }
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initVerify(publicKey);
        signature.update(challenge.getBytes(StandardCharsets.UTF_8));
        return signature.verify(signatureBytes);
    }
}


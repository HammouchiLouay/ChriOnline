package common.crypto;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.PrivateKey;
import java.security.PublicKey;

/** Encapsule une clé AES avec RSA-OAEP-SHA256 (échange de clé symétrique). */
public final class RsaOaepAesKeyWrap {

    private static final String TRANSFORM = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";

    private RsaOaepAesKeyWrap() {}

    public static byte[] wrapAesKey(PublicKey publicKey, SecretKey aesKey) throws Exception {
        Cipher c = Cipher.getInstance(TRANSFORM);
        c.init(Cipher.ENCRYPT_MODE, publicKey);
        return c.doFinal(aesKey.getEncoded());
    }

    public static SecretKey unwrapAesKey(PrivateKey privateKey, byte[] wrapped) throws Exception {
        Cipher c = Cipher.getInstance(TRANSFORM);
        c.init(Cipher.DECRYPT_MODE, privateKey);
        byte[] raw = c.doFinal(wrapped);
        return new SecretKeySpec(raw, "AES");
    }
}

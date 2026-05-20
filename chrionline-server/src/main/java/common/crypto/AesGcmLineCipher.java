package common.crypto;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/** Chiffrement des lignes protocole en AES-256-GCM (IV 12 octets + tag intégré). */
public final class AesGcmLineCipher {

    private static final int IV_LEN = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RNG = new SecureRandom();

    private AesGcmLineCipher() {}

    public static String encryptLine(String plaintext, SecretKey aesKey) throws Exception {
        if (plaintext == null) {
            plaintext = "";
        }
        byte[] iv = new byte[IV_LEN];
        RNG.nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, aesKey, new GCMParameterSpec(TAG_BITS, iv));
        byte[] ct = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
        ByteBuffer bb = ByteBuffer.allocate(iv.length + ct.length);
        bb.put(iv);
        bb.put(ct);
        return Base64.getEncoder().encodeToString(bb.array());
    }

    public static String decryptLine(String base64Line, SecretKey aesKey) throws Exception {
        byte[] all = Base64.getDecoder().decode(base64Line.trim());
        if (all.length < IV_LEN + 16) {
            throw new IllegalArgumentException("ciphertext too short");
        }
        ByteBuffer bb = ByteBuffer.wrap(all);
        byte[] iv = new byte[IV_LEN];
        bb.get(iv);
        byte[] ct = new byte[bb.remaining()];
        bb.get(ct);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, aesKey, new GCMParameterSpec(TAG_BITS, iv));
        byte[] pt = cipher.doFinal(ct);
        return new String(pt, StandardCharsets.UTF_8);
    }
}

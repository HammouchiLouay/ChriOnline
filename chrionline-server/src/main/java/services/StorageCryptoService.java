package services;

import common.ChrionlineLog;
import common.ssl.SslConfigLoader;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Properties;

/**
 * Chiffrement au repos (AES-GCM) pour données sensibles — activé via {@code ssl-config.properties} /
 * variables d’environnement.
 */
public final class StorageCryptoService {

    private static final String PREFIX = "ENC1:";
    private static final int IV_LEN = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RNG = new SecureRandom();

    private static volatile Boolean enabledCache;
    private static volatile SecretKey keyCache;

    private StorageCryptoService() {}

    public static void reloadConfig() {
        synchronized (StorageCryptoService.class) {
            enabledCache = null;
            keyCache = null;
        }
    }

    private static boolean enabledInternal() {
        Properties p = SslConfigLoader.load();
        return Boolean.parseBoolean(p.getProperty("storage.crypto.enabled", "false").trim());
    }

    private static SecretKey aesKeyInternal() {
        Properties p = SslConfigLoader.load();
        String b64 = p.getProperty("storage.crypto.aes.key.base64", "").trim();
        if (b64.isBlank()) {
            return null;
        }
        byte[] raw = Base64.getDecoder().decode(b64);
        if (raw.length != 32) {
            ChrionlineLog.err("[StorageCrypto] storage.crypto.aes.key.base64 must decode to 32 bytes (AES-256).");
            return null;
        }
        return new SecretKeySpec(raw, "AES");
    }

    private static boolean resolvedEnabled() {
        if (enabledCache == null) {
            synchronized (StorageCryptoService.class) {
                if (enabledCache == null) {
                    enabledCache = enabledInternal();
                    keyCache = enabledCache ? aesKeyInternal() : null;
                    if (Boolean.TRUE.equals(enabledCache) && keyCache == null) {
                        ChrionlineLog.warn(
                                "[StorageCrypto] storage.crypto.enabled=true but key missing/invalid — storing plaintext.");
                        enabledCache = false;
                    }
                }
            }
        }
        return Boolean.TRUE.equals(enabledCache);
    }

    /** Si le chiffrement est actif et la clé valide, retourne une valeur préfixée {@link #PREFIX}. */
    public static String sealIfEnabled(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        if (!resolvedEnabled()) {
            return plaintext;
        }
        SecretKey key = keyCache;
        if (key == null) {
            return plaintext;
        }
        try {
            byte[] iv = new byte[IV_LEN];
            RNG.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            ByteBuffer bb = ByteBuffer.allocate(iv.length + ct.length);
            bb.put(iv);
            bb.put(ct);
            return PREFIX + Base64.getEncoder().encodeToString(bb.array());
        } catch (Exception e) {
            ChrionlineLog.err("[StorageCrypto] seal failed: " + e.getMessage());
            return plaintext;
        }
    }

    public static String openIfNeeded(String stored) {
        if (stored == null || !stored.startsWith(PREFIX)) {
            return stored;
        }
        if (!resolvedEnabled()) {
            return stored;
        }
        SecretKey key = keyCache;
        if (key == null) {
            return stored;
        }
        try {
            String b64 = stored.substring(PREFIX.length());
            byte[] all = Base64.getDecoder().decode(b64);
            ByteBuffer bb = ByteBuffer.wrap(all);
            byte[] iv = new byte[IV_LEN];
            bb.get(iv);
            byte[] ct = new byte[bb.remaining()];
            bb.get(ct);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] pt = cipher.doFinal(ct);
            return new String(pt, StandardCharsets.UTF_8);
        } catch (Exception e) {
            ChrionlineLog.err("[StorageCrypto] open failed: " + e.getMessage());
            return stored;
        }
    }
}

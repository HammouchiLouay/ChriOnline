package common.crypto;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

/** Génération de paires de clés RSA (2048) + export PEM. Pour écrire les fichiers, exécuter {@link GenerateAdminRsaKeys}. */
public final class RsaKeyPairGenerator {

    private RsaKeyPairGenerator() {}

    public static KeyPair generateKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    public static String toPublicKeyPem(KeyPair kp) {
        String b64 = Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(kp.getPublic().getEncoded());
        return "-----BEGIN PUBLIC KEY-----\n" + b64 + "\n-----END PUBLIC KEY-----\n";
    }

    public static String toPrivateKeyPem(KeyPair kp) {
        String b64 = Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(kp.getPrivate().getEncoded());
        return "-----BEGIN PRIVATE KEY-----\n" + b64 + "\n-----END PRIVATE KEY-----\n";
    }
}


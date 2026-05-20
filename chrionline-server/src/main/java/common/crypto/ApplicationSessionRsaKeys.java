package common.crypto;

import common.ChrionlineLog;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.util.Properties;

/**
 * Charge la paire RSA serveur pour le protocole applicatif (Mini-Projet 2). Réutilise optionnellement le keystore TLS.
 */
public final class ApplicationSessionRsaKeys {

    private static volatile KeyPair cached;

    private ApplicationSessionRsaKeys() {}

    public static void warmup(Properties securityProps) throws Exception {
        ensureLoaded(securityProps);
    }

    public static KeyPair ensureLoaded(Properties securityProps) throws Exception {
        KeyPair kp = cached;
        if (kp != null) {
            return kp;
        }
        synchronized (ApplicationSessionRsaKeys.class) {
            if (cached != null) {
                return cached;
            }
            String path = securityProps.getProperty("server.crypto.session.rsa.keystore.path", "").trim();
            String pass = securityProps.getProperty("server.crypto.session.rsa.keystore.password", "").trim();
            String alias = securityProps.getProperty("server.crypto.session.rsa.key.alias", "").trim();

            if (path.isEmpty()) {
                path = securityProps.getProperty("server.ssl.key-store.path", "").trim();
                if (pass.isEmpty()) {
                    pass = securityProps.getProperty("server.ssl.key-store.password", "").trim();
                }
            }
            if (alias.isEmpty()) {
                alias = securityProps.getProperty("server.ssl.key-alias", "").trim();
            }
            if (alias.isEmpty()) {
                alias = "chrionline-session";
            }

            if (path.isBlank() || pass.isBlank()) {
                KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
                generator.initialize(2048);
                cached = generator.generateKeyPair();
                ChrionlineLog.warn(
                        "[AppCrypto] No RSA session keystore configured; generated an ephemeral RSA keypair for this server run.");
                return cached;
            }

            Path p = Path.of(path);
            if (!Files.isRegularFile(p)) {
                throw new IllegalStateException("RSA session keystore missing: " + p.toAbsolutePath());
            }

            String ksType =
                    securityProps.getProperty("server.crypto.session.rsa.keystore.type", "").trim();
            if (ksType.isEmpty()) {
                ksType = securityProps.getProperty("server.ssl.key-store.type", "PKCS12").trim();
            }

            KeyStore ks = KeyStore.getInstance(ksType);
            try (InputStream in = Files.newInputStream(p)) {
                ks.load(in, pass.toCharArray());
            }

            Key key = ks.getKey(alias, pass.toCharArray());
            if (!(key instanceof PrivateKey privateKey)) {
                throw new IllegalStateException("Private key missing for alias '" + alias + "' in " + p);
            }
            Certificate cert = ks.getCertificate(alias);
            if (cert == null) {
                throw new IllegalStateException("Certificate missing for alias '" + alias + "' in " + p);
            }
            PublicKey pub = cert.getPublicKey();
            cached = new KeyPair(pub, privateKey);
            ChrionlineLog.info("[AppCrypto] Loaded RSA session keypair from keystore: " + p.toAbsolutePath());
            return cached;
        }
    }

    public static PublicKey publicKey(Properties securityProps) throws Exception {
        return ensureLoaded(securityProps).getPublic();
    }

    public static PrivateKey privateKey(Properties securityProps) throws Exception {
        return ensureLoaded(securityProps).getPrivate();
    }
}

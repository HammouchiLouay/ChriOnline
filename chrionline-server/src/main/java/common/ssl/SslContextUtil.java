package common.ssl;

import common.ChrionlineLog;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.Properties;

/**
 * Construit des {@link SSLContext} à partir de keystore/truststore Java (PKCS12 recommandé).
 *
 * <p>Objectif: sécuriser le transport socket (ServerSocket/Socket) via TLS, sans changer le protocole applicatif.
 */
public final class SslContextUtil {

    private SslContextUtil() {}

    public static boolean isServerTlsEnabled(Properties p) {
        return Boolean.parseBoolean(p.getProperty("server.ssl.enabled", "false").trim());
    }

    public static boolean isClientTlsEnabled(Properties p) {
        return Boolean.parseBoolean(p.getProperty("client.ssl.enabled", "false").trim());
    }

    public static SSLContext buildServerContext(Properties p) throws Exception {
        String protocol = p.getProperty("server.ssl.protocol", "TLS").trim();
        Path ksPath = Path.of(p.getProperty("server.ssl.key-store.path", "").trim());
        String ksPass = p.getProperty("server.ssl.key-store.password", "").trim();
        String ksType = p.getProperty("server.ssl.key-store.type", "PKCS12").trim();
        String keyPass = p.getProperty("server.ssl.key.password", ksPass).trim();

        if (ksPath.toString().isBlank() || ksPass.isBlank()) {
            throw new IllegalStateException("server.ssl.key-store.path/password required when server.ssl.enabled=true");
        }
        if (!Files.isRegularFile(ksPath)) {
            throw new IllegalStateException("server keystore not found: " + ksPath);
        }

        KeyStore ks = KeyStore.getInstance(ksType);
        try (InputStream in = Files.newInputStream(ksPath)) {
            ks.load(in, ksPass.toCharArray());
        }

        KeyManagerFactory kmf =
                KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, keyPass.toCharArray());

        SSLContext ctx = SSLContext.getInstance(protocol);
        ctx.init(kmf.getKeyManagers(), null, null);
        ChrionlineLog.info("[TLS] Server SSLContext ready (keystore=" + ksPath + ", type=" + ksType + ")");
        return ctx;
    }

    public static SSLContext buildClientContext(Properties p) throws Exception {
        String protocol = p.getProperty("client.ssl.protocol", "TLS").trim();
        String tsPathRaw = p.getProperty("client.ssl.trust-store.path", "").trim();
        String tsPass = p.getProperty("client.ssl.trust-store.password", "").trim();
        String tsType = p.getProperty("client.ssl.trust-store.type", "PKCS12").trim();

        if (tsPathRaw.isBlank() || tsPass.isBlank()) {
            throw new IllegalStateException("client.ssl.trust-store.path/password required when client.ssl.enabled=true");
        }
        Path tsPath = Path.of(tsPathRaw);
        if (!Files.isRegularFile(tsPath)) {
            throw new IllegalStateException("client truststore not found: " + tsPath);
        }

        KeyStore ts = KeyStore.getInstance(tsType);
        try (InputStream in = Files.newInputStream(tsPath)) {
            ts.load(in, tsPass.toCharArray());
        }
        TrustManagerFactory tmf =
                TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(ts);

        SSLContext ctx = SSLContext.getInstance(protocol);
        ctx.init(null, tmf.getTrustManagers(), null);
        ChrionlineLog.info("[TLS] Client SSLContext ready (truststore=" + tsPath + ", type=" + tsType + ")");
        return ctx;
    }
}


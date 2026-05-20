package server;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Protocole de découverte LAN par multicast : les clients trouvent le serveur socket sans saisir d’IP.
 */
public final class LanDiscoveryProtocol {

    /** Groupe multicast IPv4. */
    public static final String MULTICAST_GROUP = "239.255.42.73";
    /** Port UDP partagé pour les annonces et l’écoute client. */
    public static final int MULTICAST_PORT = 47474;
    /** Préfixe magique du paquet UDP. */
    public static final String MAGIC = "CHRIONLINE";

    private LanDiscoveryProtocol() {}

    /** Encode {@code MAGIC|hôte|port} en UTF-8 pour émission UDP. */
    public static byte[] encode(String host, int port) {
        String payload = MAGIC + "|" + host + "|" + port;
        return payload.getBytes(StandardCharsets.UTF_8);
    }

    /** Valide et parse un paquet reçu ; sinon retourne vide. */
    public static Optional<InetSocketAddress> parse(byte[] data, int len) {
        if (len <= 0 || len > 512) {
            return Optional.empty();
        }
        String s = new String(data, 0, len, StandardCharsets.UTF_8).trim();
        String[] parts = s.split("\\|");
        if (parts.length != 3 || !MAGIC.equals(parts[0])) {
            return Optional.empty();
        }
        String host = parts[1].trim();
        if (host.isEmpty()) {
            return Optional.empty();
        }
        int p;
        try {
            p = Integer.parseInt(parts[2].trim());
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
        if (p < 1 || p > 65535) {
            return Optional.empty();
        }
        return Optional.of(new InetSocketAddress(host, p));
    }
}

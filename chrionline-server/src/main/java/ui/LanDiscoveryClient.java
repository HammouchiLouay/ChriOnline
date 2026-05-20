package ui;

import server.LanDiscoveryProtocol;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Enumeration;
import java.util.Optional;

/**
 * Écoute les annonces multicast LAN (et broadcast UDP) émises par {@link server.ServerMain}.
 */
public final class LanDiscoveryClient {

    private LanDiscoveryClient() {}

    /**
     * Écoute multicast courte pour inciter le pare-feu Windows à proposer d’autoriser la JVM
     * ({@code java.exe} / {@code javaw.exe}) sur les réseaux privés. Aucune API ne force la boîte de dialogue ;
     * lier le même port UDP que la découverte déclenche souvent l’invite au premier usage.
     *
     * <p>Aucun serveur requis ; la socket se ferme après un court délai.
     */
    public static void firewallWarmupForWindows() {
        int t = 220;
        MulticastSocket socket = null;
        try {
            socket = new MulticastSocket(LanDiscoveryProtocol.MULTICAST_PORT);
            socket.setReuseAddress(true);
            socket.setBroadcast(true);
            socket.setSoTimeout(t);
            InetSocketAddress group =
                    new InetSocketAddress(
                            InetAddress.getByName(LanDiscoveryProtocol.MULTICAST_GROUP),
                            LanDiscoveryProtocol.MULTICAST_PORT);
            joinAllUpInterfaces(socket, group);
            byte[] buf = new byte[512];
            DatagramPacket packet = new DatagramPacket(buf, buf.length);
            try {
                socket.receive(packet);
            } catch (SocketTimeoutException e) {
                // expected when no beacon
            }
        } catch (Exception ignored) {
        } finally {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        }
    }

    /**
     * Several short attempts with pauses — helps when Wi‑Fi or multicast is slow to come up.
     *
     * @param timeoutMs per attempt (listen window)
     * @param rounds number of attempts (≥ 1)
     * @param pauseMs idle time between attempts
     */
    public static Optional<InetSocketAddress> discoverWithRetries(
            int timeoutMs, int rounds, int pauseMs) {
        int r = Math.max(1, Math.min(12, rounds));
        int pause = Math.max(0, Math.min(5000, pauseMs));
        for (int i = 0; i < r; i++) {
            Optional<InetSocketAddress> hit = discover(timeoutMs);
            if (hit.isPresent()) {
                return hit;
            }
            if (i + 1 < r && pause > 0) {
                try {
                    Thread.sleep(pause);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return Optional.empty();
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Attend jusqu’à {@code timeoutMs} le premier paquet CHRIONLINE valide (multicast ou broadcast).
     * Retourne vide si rien n’est reçu (serveur arrêté, autre VLAN, multicast filtré).
     */
    public static Optional<InetSocketAddress> discover(int timeoutMs) {
        int t = Math.max(800, Math.min(60_000, timeoutMs));
        MulticastSocket socket = null;
        try {
            socket = new MulticastSocket(LanDiscoveryProtocol.MULTICAST_PORT);
            socket.setReuseAddress(true);
            socket.setBroadcast(true);
            socket.setSoTimeout(t);
            InetSocketAddress group =
                    new InetSocketAddress(
                            InetAddress.getByName(LanDiscoveryProtocol.MULTICAST_GROUP),
                            LanDiscoveryProtocol.MULTICAST_PORT);
            joinAllUpInterfaces(socket, group);

            long deadline = System.currentTimeMillis() + t;
            byte[] buf = new byte[512];
            while (System.currentTimeMillis() < deadline) {
                int remaining = (int) Math.max(1, deadline - System.currentTimeMillis());
                socket.setSoTimeout(remaining);
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                try {
                    socket.receive(packet);
                    Optional<InetSocketAddress> parsed =
                            LanDiscoveryProtocol.parse(packet.getData(), packet.getLength());
                    if (parsed.isPresent()) {
                        return parsed;
                    }
                } catch (SocketTimeoutException e) {
                    break;
                }
            }
        } catch (SocketException e) {
            return Optional.empty();
        } catch (Exception ignored) {
        } finally {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        }
        return Optional.empty();
    }

    /** Rejoint le groupe multicast sur toutes les interfaces actives non loopback. */
    private static void joinAllUpInterfaces(MulticastSocket socket, InetSocketAddress group)
            throws SocketException {
        Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces();
        while (en.hasMoreElements()) {
            NetworkInterface ni = en.nextElement();
            if (!ni.isUp() || ni.isLoopback()) {
                continue;
            }
            try {
                socket.joinGroup(group, ni);
            } catch (Exception ignored) {
            }
        }
    }
}

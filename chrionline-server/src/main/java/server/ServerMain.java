package server;

import chrionline.BaseDonnees;
import common.ChrionlineLog;
import common.crypto.ApplicationSessionRsaKeys;
import common.ssl.SslConfigLoader;
import common.ssl.SslContextUtil;
import services.MailService;
import services.StorageCryptoService;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.SQLException;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

import javax.net.ServerSocketFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocketFactory;

/**
 * Point d’entrée du serveur socket ChriOnline : charge la config e-mail, vérifie MySQL, écoute sur un port TCP
 * et lance un {@link ClientHandler} par connexion ; annonce le service sur le LAN (multicast / broadcast).
 */
public class ServerMain {

    private static volatile boolean lanAnnouncerStarted;

    /**
     * Démarre le serveur sur le port indiqué (défaut 6000) : {@code java ... ServerMain [port]}.
     */
    public static void main(String[] args) {
        int port = 6000;
        if (args.length >= 1) {
            try {
                port = Integer.parseInt(args[0].trim());
            } catch (NumberFormatException ignored) {
            }
        }
        try {
            ChrionlineLog.info("ChriOnline socket server");
            MailService.reload();
            MailService.logMailDiagnostics();
            if (MailService.isMailConfigured()) {
                ChrionlineLog.info("Mail SMTP/API : configuration chargée (envoi possible).");
            } else {
                ChrionlineLog.info(
                        "Mail : non configuré (SMTP sans mot de passe ou host vide, ou Resend) — codes en console.");
            }
            ChrionlineLog.info("Connecting to MySQL on this machine (127.0.0.1:3306/chrionline)...");
            ChrionlineLog.info("JDBC URL used by this server: " + BaseDonnees.jdbcUrl());
            try {
                BaseDonnees.verifyConnection();
                ChrionlineLog.info("MySQL OK - database=" + BaseDonnees.currentDatabaseName());
                ChrionlineLog.info("DESCRIBE products: " + BaseDonnees.describeProductsTable());
                ChrionlineLog.info("MySQL OK — catalogue, comptes et commandes utilisent cette base.");
            } catch (SQLException e) {
                ChrionlineLog.err("Echec connexion MySQL sur cette machine. Demarrez MySQL et la base chrionline.", e);
                System.exit(1);
                return;
            }

            Properties sslProps = SslConfigLoader.load();
            StorageCryptoService.reloadConfig();
            ApplicationSessionRsaKeys.warmup(sslProps);
            ChrionlineLog.info("[AppCrypto] RSA/AES is mandatory: supported=true, required=true.");
            ChrionlineLog.info("[AppCrypto] Every socket session uses RSA-OAEP key exchange + AES-GCM lines.");

            InetAddress bind = InetAddress.getByName("0.0.0.0");
            ServerSocketFactory factory = ServerSocketFactory.getDefault();
            if (SslContextUtil.isServerTlsEnabled(sslProps)) {
                SSLContext ctx = SslContextUtil.buildServerContext(sslProps);
                factory = ctx.getServerSocketFactory();
                ChrionlineLog.info("[TLS] Enabled (server.ssl.enabled=true) — expecting TLS clients.");
            } else {
                ChrionlineLog.info("[TLS] Disabled — using plain TCP.");
            }
            try (ServerSocket serverSocket = factory.createServerSocket(port, 200, bind)) {
                ChrionlineLog.info("Listening on ALL interfaces, port " + port + " (LAN, WAN port-forward, etc.)");
                ChrionlineLog.info("MySQL reste sur 127.0.0.1 sur cette machine — les clients n’utilisent que ce port TCP.");
                List<String> ips = NetworkInfo.localIPv4Addresses();
                if (ips.isEmpty()) {
                    ChrionlineLog.info(
                            "Aucune IPv4 « LAN » détectée (seulement loopback / VirtualBox host-only ?). "
                                    + "Connectez le Wi‑Fi ou Ethernet, ou désactivez l’adaptateur host-only inutile.");
                    ChrionlineLog.info(
                            "  (Les adresses 192.168.56.x sont du VirtualBox — invisibles pour les autres PC.)");
                } else {
                    ChrionlineLog.info(
                            "Clients (JavaFX) : utilisez une de ces adresses (pas localhost), même sur ce PC :");
                    for (String ip : ips) {
                        ChrionlineLog.info("  -> " + ip + ":" + port);
                    }
                }
                ChrionlineLog.info(
                        "WAN : redirection TCP "
                                + port
                                + " vers ce PC + IP publique ou DNS dans le client (multicast ne traverse pas Internet).");
                Optional<String> pub = PublicIpHint.fetchPublicIpv4();
                if (pub.isPresent()) {
                    ChrionlineLog.info("");
                    ChrionlineLog.info("=== Clients sur un AUTRE réseau (autre Wi‑Fi, 4G, autre ville) ===");
                    ChrionlineLog.info(
                            "Les IP 192.168.x / 10.x ci-dessus ne sont PAS des adresses Internet : elles ne "
                                    + "fonctionnent que sur VOTRE réseau local.");
                    ChrionlineLog.info(
                            "Depuis l'extérieur, il faut l'IP publique du routeur + redirection de port TCP "
                                    + port
                                    + " vers ce PC.");
                    ChrionlineLog.info("IP publique (aperçu sortant) : " + pub.get() + "  ->  client : " + pub.get() + ":" + port);
                    ChrionlineLog.info(
                            "(Si ça ne marche pas : pare-feu Windows, pare-feu du routeur, ou box en CGNAT sans "
                                    + "IPv4 publique — essayez IPv4 du FAI, VPN type Tailscale, ou hébergement cloud.)");
                    ChrionlineLog.info("");
                } else {
                    ChrionlineLog.info(
                            "(IP publique non détectée — vérifiez la connexion ou cherchez « what is my ip » dans un navigateur.)");
                }
                ChrionlineLog.info("Waiting for client connections...");
                ChrionlineLog.info(
                        "Pare-feu Windows : autorisez TCP entrant sur le port "
                                + port
                                + " (et UDP "
                                + LanDiscoveryProtocol.MULTICAST_PORT
                                + " si la détection LAN échoue).");
                ChrionlineLog.info(
                        "  Exemple (invite admin) : netsh advfirewall firewall add rule name=\"ChriOnline TCP\" "
                                + "dir=in action=allow protocol=TCP localport="
                                + port);
                startLanDiscoveryAnnouncer(port);
                while (true) {
                    Socket clientSocket = serverSocket.accept();
                    ChrionlineLog.info("Client connected : " + clientSocket.getInetAddress());
                    ClientHandler handler = new ClientHandler(clientSocket);
                    new Thread(handler).start();
                }
            }
        } catch (Exception e) {
            ChrionlineLog.err("Server startup or accept loop failed.", e);
        }
    }

    /**
     * Envoie périodiquement des annonces multicast (et secours broadcast) pour que les clients JavaFX
     * {@link ui.LanDiscoveryClient} découvrent l’adresse du serveur sur le même LAN.
     */
    private static void startLanDiscoveryAnnouncer(int serverPort) {
        if (lanAnnouncerStarted) {
            return;
        }
        lanAnnouncerStarted = true;
        Thread t =
                new Thread(
                        () -> {
                            try {
                                InetAddress group =
                                        InetAddress.getByName(LanDiscoveryProtocol.MULTICAST_GROUP);
                                try (DatagramSocket socket = new DatagramSocket()) {
                                    socket.setBroadcast(true);
                                    InetAddress broadcastGlobal =
                                            InetAddress.getByName("255.255.255.255");
                                    while (true) {
                                        List<String> ips = NetworkInfo.localIPv4Addresses();
                                        if (ips.isEmpty()) {
                                            Thread.sleep(2000);
                                            continue;
                                        }
                                        for (String ip : ips) {
                                            byte[] data = LanDiscoveryProtocol.encode(ip, serverPort);
                                            DatagramPacket packet =
                                                    new DatagramPacket(
                                                            data,
                                                            data.length,
                                                            group,
                                                            LanDiscoveryProtocol.MULTICAST_PORT);
                                            socket.send(packet);
                                            // Broadcast fallback (some Wi‑Fi / APs block multicast)
                                            try {
                                                DatagramPacket broad =
                                                        new DatagramPacket(
                                                                data,
                                                                data.length,
                                                                broadcastGlobal,
                                                                LanDiscoveryProtocol.MULTICAST_PORT);
                                                socket.send(broad);
                                            } catch (Exception ignored) {
                                            }
                                            sendSubnetDirectedBroadcasts(socket, data);
                                        }
                                        Thread.sleep(2000);
                                    }
                                }
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            } catch (Exception e) {
                                ChrionlineLog.err("LAN discovery announcer stopped: " + e.getMessage(), e);
                            }
                        },
                        "chrionline-lan-announce");
        t.setDaemon(true);
        t.start();
    }

    /**
     * Envoie aussi sur les adresses de broadcast dirigées par sous-réseau (ex. 192.168.1.255) lorsque le multicast
     * global est filtré.
     */
    private static void sendSubnetDirectedBroadcasts(DatagramSocket socket, byte[] data) {
        try {
            Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces();
            while (en.hasMoreElements()) {
                NetworkInterface ni = en.nextElement();
                if (!ni.isUp() || ni.isLoopback()) {
                    continue;
                }
                for (InterfaceAddress ia : ni.getInterfaceAddresses()) {
                    InetAddress b = ia.getBroadcast();
                    if (b == null) {
                        continue;
                    }
                    try {
                        DatagramPacket p =
                                new DatagramPacket(
                                        data, data.length, b, LanDiscoveryProtocol.MULTICAST_PORT);
                        socket.send(p);
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }
}

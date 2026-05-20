package server;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;

/**
 * Liste les adresses IPv4 locales utilisables par <strong>d’autres machines</strong> pour joindre cet hôte (Wi‑Fi /
 * Ethernet). Exclut VirtualBox / VMware host-only, ponts Docker, etc., non joignables depuis d’autres PC physiques.
 */
public final class NetworkInfo {

    private NetworkInfo() {}

    /**
     * IPv4 addresses that remote ChriOnline clients should use (same LAN or port‑forwarded WAN). Filters
     * out loopback, VirtualBox host‑only ({@code 192.168.56.x}), typical VM host‑only adapters, and
     * Docker internal bridges when identifiable by interface name.
     */
    public static List<String> localIPv4Addresses() {
        List<String> out = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
            while (ifaces.hasMoreElements()) {
                NetworkInterface ni = ifaces.nextElement();
                if (!ni.isUp() || ni.isLoopback()) {
                    continue;
                }
                if (isVirtualOrHostOnlyInterface(ni)) {
                    continue;
                }
                Enumeration<InetAddress> addrs = ni.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress a = addrs.nextElement();
                    if (a instanceof Inet4Address && !a.isLoopbackAddress()) {
                        Inet4Address v4 = (Inet4Address) a;
                        if (isVirtualOnlyOrNonRoutableSubnet(v4)) {
                            continue;
                        }
                        out.add(v4.getHostAddress());
                    }
                }
            }
        } catch (Exception ignored) {
        }
        Collections.sort(out);
        return out;
    }

    /**
     * {@code false} pour les adresses de type host-only VirtualBox : d’autres PC du LAN ne peuvent pas s’y connecter en TCP.
     */
    public static boolean isLikelyReachableFromOtherMachines(String ipv4) {
        if (ipv4 == null || ipv4.isBlank()) {
            return false;
        }
        try {
            InetAddress a = InetAddress.getByName(ipv4.trim());
            if (!(a instanceof Inet4Address)) {
                return true;
            }
            return !isVirtualOnlyOrNonRoutableSubnet((Inet4Address) a);
        } catch (Exception e) {
            return true;
        }
    }

    /**
     * {@code true} si {@code host} résout vers une adresse privée / site-local / link-local : les clients sur un
     * <strong>autre</strong> réseau (autre FAI, 4G) ne peuvent pas l’atteindre sur Internet public (IP publique +
     * redirection de port ou VPN nécessaires).
     */
    public static boolean isNotRoutableFromOtherNetworks(String host) {
        if (host == null || host.isBlank()) {
            return false;
        }
        try {
            InetAddress a = InetAddress.getByName(host.trim());
            if (a.isLoopbackAddress()) {
                return true;
            }
            if (a.isAnyLocalAddress()) {
                return true;
            }
            if (a.isLinkLocalAddress()) {
                return true;
            }
            return a.isSiteLocalAddress();
        } catch (Exception e) {
            return false;
        }
    }

    /** Heuristique sur le nom d’interface réseau (VirtualBox, Docker, etc.). */
    private static boolean isVirtualOrHostOnlyInterface(NetworkInterface ni) {
        String name = ni.getName() != null ? ni.getName().toLowerCase(Locale.ROOT) : "";
        String display = ni.getDisplayName() != null ? ni.getDisplayName().toLowerCase(Locale.ROOT) : "";
        if (name.contains("vbox") || name.contains("vmnet") || name.contains("virtual")) {
            return true;
        }
        if (name.contains("docker") || name.contains("br-") || name.contains("virbr")) {
            return true;
        }
        if (display.contains("virtualbox")
                || display.contains("vmware")
                || display.contains("hyper-v")
                || display.contains("vethernet")
                || display.contains("docker")
                || display.contains("virtual network")) {
            return true;
        }
        return false;
    }

    /**
     * Exclut les sous-réseaux host-only connus (défaut VirtualBox) même si le nom de la carte est atypique.
     */
    private static boolean isVirtualOnlyOrNonRoutableSubnet(Inet4Address a) {
        byte[] b = a.getAddress();
        int o1 = b[0] & 0xFF;
        int o2 = b[1] & 0xFF;
        int o3 = b[2] & 0xFF;
        // VirtualBox Host-Only default (not reachable from other physical PCs on Wi‑Fi)
        if (o1 == 192 && o2 == 168 && o3 == 56) {
            return true;
        }
        return false;
    }
}

package server;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Récupère l’IPv4 publique (sortante) du réseau pour aider à la redirection de port et au partage d’adresse avec
 * des clients sur d’autres réseaux / FAI.
 */
public final class PublicIpHint {

    private static final Pattern IPV4 = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}$");

    private static final HttpClient HTTP =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();

    private PublicIpHint() {}

    /** Meilleur effort ; vide si hors ligne ou si les services bloquent. */
    public static Optional<String> fetchPublicIpv4() {
        String[] urls = {
            "https://api.ipify.org",
            "https://icanhazip.com",
            "https://ifconfig.me/ip"
        };
        for (String u : urls) {
            try {
                HttpRequest req =
                        HttpRequest.newBuilder()
                                .uri(URI.create(u))
                                .timeout(Duration.ofSeconds(6))
                                .GET()
                                .build();
                HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
                if (res.statusCode() >= 200 && res.statusCode() < 300 && res.body() != null) {
                    String line = res.body().trim().split("\\s")[0];
                    if (IPV4.matcher(line).matches()) {
                        return Optional.of(line);
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return Optional.empty();
    }
}

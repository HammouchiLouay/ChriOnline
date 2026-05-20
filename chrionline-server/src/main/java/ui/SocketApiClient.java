package ui;

import common.ClientConfigLoader;
import common.JsonUtil;
import common.Message;
import common.crypto.AesGcmLineCipher;
import common.crypto.RsaOaepAesKeyWrap;
import common.ssl.SslConfigLoader;
import common.ssl.SslContextUtil;
import product.Product;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.net.SocketFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Client TCP du protocole socket ChriOnline (lignes JSON {@link Message}).
 *
 * <p>Every connection opens a mandatory RSA→AES session, then sends all JSON lines encrypted with AES-GCM.
 */
public final class SocketApiClient {

    private final String host;
    private final int port;

    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private SecretKey sessionAesKey;
    private volatile boolean cryptoHandshakeDone;

    /** @param host adresse du serveur (LAN, WAN ou loopback si autorisé) */
    public SocketApiClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    /** RSA→AES is mandatory; the setter remains for older call sites but cannot disable it. */
    public void setApplicationCryptoEnabled(boolean enabled) {
        if (!enabled) {
            closeQuietly();
        }
    }

    public boolean isApplicationCryptoEnabled() {
        return true;
    }

    /** {@code true} si une session AES applicative est établie sur la connexion courante. */
    public boolean isApplicationCryptoSessionActive() {
        return cryptoHandshakeDone && sessionAesKey != null;
    }

    /** Referme la connexion persistante (nouvelle poignée de main au prochain envoi). */
    public void closeQuietly() {
        synchronized (this) {
            cryptoHandshakeDone = false;
            sessionAesKey = null;
            try {
                if (socket != null) {
                    socket.close();
                }
            } catch (IOException ignored) {
            }
            socket = null;
            in = null;
            out = null;
        }
    }

    private SocketFactory buildSocketFactory() throws Exception {
        Properties merged = ClientConfigLoader.load();
        Properties sslProps = SslConfigLoader.load();
        boolean tls =
                SslContextUtil.isClientTlsEnabled(sslProps)
                        || Boolean.parseBoolean(merged.getProperty("client.ssl.enabled", "false").trim());
        if (tls) {
            SSLContext ctx = SslContextUtil.buildClientContext(sslProps);
            return ctx.getSocketFactory();
        }
        return SocketFactory.getDefault();
    }

    private void resetIoFieldsLocked() {
        socket = null;
        in = null;
        out = null;
        sessionAesKey = null;
        cryptoHandshakeDone = false;
    }

    private void closeIoLocked() {
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException ignored) {
        }
        resetIoFieldsLocked();
    }

    private void ensureConnectedLocked() throws IOException {
        if (socket != null && socket.isConnected() && !socket.isClosed()) {
            return;
        }
        closeIoLocked();

        SocketFactory factory;
        try {
            factory = buildSocketFactory();
        } catch (Exception e) {
            throw new IOException("TLS config error: " + e.getMessage(), e);
        }

        Socket s = factory.createSocket();
        try {
            s.connect(new InetSocketAddress(host, port), 10_000);
            s.setSoTimeout(120_000);
            if (s instanceof SSLSocket ssl) {
                ssl.startHandshake();
            }
            socket = s;
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            out =
                    new PrintWriter(
                            new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8),
                            true);

            performHandshakeLocked();
        } catch (IOException e) {
            closeIoLocked();
            throw e;
        } catch (Exception e) {
            closeIoLocked();
            throw new IOException("Handshake / connexion: " + e.getMessage(), e);
        }
    }

    private void performHandshakeLocked() throws Exception {
        out.println(JsonUtil.toJson(Message.request("CLIENT_HELLO", "0", "{\"crypto\":\"true\"}")));
        String line1 = in.readLine();
        if (line1 == null) {
            throw new IOException("Flux vide avant SERVER_HELLO");
        }
        Message ann = JsonUtil.fromJson(line1);
        if (!"SERVER_HELLO".equalsIgnoreCase(ann.getType())) {
            throw new IOException("Attendu SERVER_HELLO, recu " + ann.getType());
        }
        if (!"SUCCESS".equals(ann.getStatus())) {
            if ("CRYPTO_UNSUPPORTED".equals(ann.getErrorCode())) {
                throw new IOException("Le serveur ne supporte pas le mode RSA/AES actuellement.");
            }
            throw new IOException("Poignee de main refusee: " + ann.getErrorCode());
        }
        Map<String, String> am = JsonUtil.toMap(ann.getPayload());
        String spki = am.get("publicKeySpkiB64");
        if (spki == null || spki.isBlank()) {
            throw new IOException("SERVER_HELLO sans publicKeySpkiB64");
        }
        byte[] der = Base64.getDecoder().decode(spki.trim());
        PublicKey pub = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));

        KeyGenerator kg = KeyGenerator.getInstance("AES");
        kg.init(256);
        SecretKey aes = kg.generateKey();

        byte[] wrapped = RsaOaepAesKeyWrap.wrapAesKey(pub, aes);
        String payload =
                "{\"wrappedKey\":\"" + Base64.getEncoder().encodeToString(wrapped) + "\"}";
        Message exch = Message.request("SECURE_KEY_EXCHANGE", "1", payload);
        out.println(JsonUtil.toJson(exch));

        String okLine = in.readLine();
        if (okLine == null) {
            throw new IOException("Flux vide avant SECURE_SESSION_OK");
        }
        Message ok = JsonUtil.fromJson(okLine);
        if (!"SECURE_SESSION_OK".equalsIgnoreCase(ok.getType())) {
            throw new IOException("Attendu SECURE_SESSION_OK, reçu " + ok.getType());
        }
        if (!"SUCCESS".equals(ok.getStatus())) {
            throw new IOException("Poignée de main refusée: " + ok.getErrorCode());
        }
        sessionAesKey = aes;
        cryptoHandshakeDone = true;
    }

    public Message send(Message request) throws IOException {
        synchronized (this) {
            try {
                ensureConnectedLocked();

                String plainReq = JsonUtil.toJson(request);
                out.println(AesGcmLineCipher.encryptLine(plainReq, sessionAesKey));

                String respLine = in.readLine();
                if (respLine == null) {
                    closeIoLocked();
                    throw new IOException("Server closed connection without response");
                }
                String plainResp = AesGcmLineCipher.decryptLine(respLine, sessionAesKey);
                return JsonUtil.fromJson(plainResp);
            } catch (IOException e) {
                closeIoLocked();
                throw e;
            } catch (Exception e) {
                closeIoLocked();
                throw new IOException("Crypto/protocol error: " + e.getMessage(), e);
            }
        }
    }

    /** Décode la charge utile Base64+Java sérialisé de {@code PRODUCT_LIST}. */
    public List<Product> fetchProductList(Message response) throws IOException, ClassNotFoundException {
        if (response == null || !"SUCCESS".equals(response.getStatus())) {
            return Collections.emptyList();
        }
        String b64 = response.getPayload();
        if (b64 == null || b64.isEmpty()) {
            return Collections.emptyList();
        }
        byte[] bytes = Base64.getDecoder().decode(b64);
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            Object o = ois.readObject();
            if (o instanceof List) {
                List<?> raw = (List<?>) o;
                List<Product> out = new ArrayList<>();
                for (Object x : raw) {
                    if (x instanceof Product) {
                        out.add((Product) x);
                    }
                }
                return out;
            }
        }
        return Collections.emptyList();
    }

    /** Décode un seul {@link Product} depuis {@code PRODUCT_DETAILS}. */
    public Product fetchProductDetails(Message response) throws IOException, ClassNotFoundException {
        if (response == null || !"SUCCESS".equals(response.getStatus())) {
            return null;
        }
        String b64 = response.getPayload();
        if (b64 == null || b64.isEmpty()) {
            return null;
        }
        byte[] bytes = Base64.getDecoder().decode(b64);
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            Object o = ois.readObject();
            if (o instanceof Product) {
                return (Product) o;
            }
        }
        return null;
    }

    /** Résumé d’une commande pour listes compactes. */
    public record CommandeSummary(int id, int userId, String status, double total) {
        /** Ligne lisible avec icône de statut et montant en MAD. */
        public String toDisplayLine() {
            String icon =
                    switch (status == null ? "" : status.toUpperCase(Locale.ROOT)) {
                        case "PAYEE", "VALIDEE", "PAID" -> "OK";
                        case "EN_ATTENTE", "PENDING" -> "...";
                        case "ANNULEE", "REFUSEE", "FAILED" -> "X";
                        case "EXPEDIEE", "SHIPPED" -> ">";
                        case "LIVREE", "DELIVERED" -> "+";
                        case "REMBOURSEE", "REFUNDED" -> "<";
                        default -> "*";
                    };
            return icon
                    + " Commande "
                    + id
                    + " — "
                    + status
                    + " — "
                    + String.format(Locale.FRENCH, "%.2f", total)
                    + " MAD";
        }
    }

    /** Extrait des résumés depuis un tableau JSON simplifié (regex). */
    public static List<CommandeSummary> parseCommandeSummaries(String jsonArray) {
        if (jsonArray == null || jsonArray.length() < 5) {
            return List.of();
        }
        List<CommandeSummary> list = new ArrayList<>();
        Pattern p =
                Pattern.compile(
                        "\"id\":(\\d+),\"userId\":(\\d+),\"status\":\"([^\"]+)\",\"total\":([0-9.]+)"
                );
        Matcher m = p.matcher(jsonArray);
        while (m.find()) {
            int id = Integer.parseInt(m.group(1));
            int uid = Integer.parseInt(m.group(2));
            String status = m.group(3);
            double total = Double.parseDouble(m.group(4));
            list.add(new CommandeSummary(id, uid, status, total));
        }
        return list;
    }

    /**
     * Transforme les charges {@link models.Commande#toJson()} renvoyées par {@code GET_COMMANDES} en lignes affichables.
     */
    public static List<String> summarizeCommandesPayload(String jsonArray) {
        List<CommandeSummary> summaries = parseCommandeSummaries(jsonArray);
        List<String> lines = new ArrayList<>();
        for (CommandeSummary s : summaries) {
            lines.add(s.toDisplayLine());
        }
        return lines;
    }

    /** Une ligne du tableau {@code lignes} de {@link models.Commande#toJson()}. */
    public record OrderLineSnapshot(int produitId, String nom, int quantite, double prixUnitaire) {}

    /** Commande complète telle que renvoyée par {@code GET_COMMANDES} (lignes + date optionnelle). */
    public record CommandeFull(
            int id, int userId, String status, double total, long dateCommandeMs, List<OrderLineSnapshot> lignes) {}

    /** Ligne de modèle de paiement issue de {@code LIST_SAVED_PAYMENT_METHODS}. */
    public record SavedPaymentEntry(int id, String type, String label, String createdAt) {}

    /**
     * Parse le tableau JSON de {@code GET_COMMANDES} en objets riches (lignes + date).
     * Gère les charges avec ou sans {@code dateCommande}.
     */
    public static List<CommandeFull> parseCommandesFull(String jsonArray) {
        List<CommandeFull> out = new ArrayList<>();
        if (jsonArray == null || jsonArray.length() < 5) {
            return out;
        }
        Pattern withDate =
                Pattern.compile(
                        "\"id\":(\\d+),\"userId\":(\\d+),\"status\":\"([^\"]+)\",\"total\":([0-9.]+),"
                                + "\"dateCommande\":(\\d+),\"lignes\":\\[(.*?)\\]\\}",
                        Pattern.DOTALL);
        Matcher md = withDate.matcher(jsonArray);
        while (md.find()) {
            out.add(
                    new CommandeFull(
                            Integer.parseInt(md.group(1)),
                            Integer.parseInt(md.group(2)),
                            md.group(3),
                            Double.parseDouble(md.group(4)),
                            Long.parseLong(md.group(5)),
                            parseLignesBlock(md.group(6))));
        }
        if (!out.isEmpty()) {
            return out;
        }
        Pattern withoutDate =
                Pattern.compile(
                        "\"id\":(\\d+),\"userId\":(\\d+),\"status\":\"([^\"]+)\",\"total\":([0-9.]+),"
                                + "\"lignes\":\\[(.*?)\\]\\}",
                        Pattern.DOTALL);
        Matcher mo = withoutDate.matcher(jsonArray);
        while (mo.find()) {
            out.add(
                    new CommandeFull(
                            Integer.parseInt(mo.group(1)),
                            Integer.parseInt(mo.group(2)),
                            mo.group(3),
                            Double.parseDouble(mo.group(4)),
                            0L,
                            parseLignesBlock(mo.group(5))));
        }
        return out;
    }

    private static List<OrderLineSnapshot> parseLignesBlock(String block) {
        List<OrderLineSnapshot> list = new ArrayList<>();
        if (block == null || block.isBlank()) {
            return list;
        }
        Pattern pl =
                Pattern.compile(
                        "\"produitId\":(\\d+),\"nom\":\"([^\"]*)\",\"quantite\":(\\d+),\"prixUnitaire\":([0-9.]+)");
        Matcher m = pl.matcher(block);
        while (m.find()) {
            list.add(
                    new OrderLineSnapshot(
                            Integer.parseInt(m.group(1)),
                            m.group(2).replace("\\\\", "\\").replace("\\\"", "\""),
                            Integer.parseInt(m.group(3)),
                            Double.parseDouble(m.group(4))));
        }
        return list;
    }

    /** Extrait un score anti-fraude depuis du texte ou du JSON de paiement. */
    public static Integer parsePaymentFraudScore(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Pattern[] patterns =
                new Pattern[] {
                    Pattern.compile("\"scoreFraude\"\\s*:\\s*(\\d+)"),
                    Pattern.compile("score\\s*fraude\\s*:?\\s*(\\d+)", Pattern.CASE_INSENSITIVE),
                    Pattern.compile("fraude\\s*:?\\s*(\\d+)", Pattern.CASE_INSENSITIVE)
                };
        for (Pattern pat : patterns) {
            Matcher m = pat.matcher(text);
            if (m.find()) {
                return Integer.parseInt(m.group(1));
            }
        }
        return null;
    }

    /** Premier champ {@code "id"} numérique dans un fragment JSON commande. */
    public static Integer parseCommandeId(String commandeJson) {
        if (commandeJson == null) {
            return null;
        }
        Matcher m = Pattern.compile("\"id\":(\\d+)").matcher(commandeJson);
        if (m.find()) {
            return Integer.parseInt(m.group(1));
        }
        return null;
    }

    /** Identifiant utilisateur depuis une réponse LOGIN / REGISTER / profil. */
    public static Integer parseAuthUserId(String json) {
        if (json == null) {
            return null;
        }
        Matcher m = Pattern.compile("\"userId\"\\s*:\\s*(\\d+)").matcher(json);
        if (m.find()) {
            return Integer.parseInt(m.group(1));
        }
        return null;
    }

    /** Rôle métier ({@code CLIENT}, {@code SELLER}, {@code ADMIN}, …) depuis LOGIN / REGISTER. */
    public static String parseAuthRole(String json) {
        if (json == null) {
            return "CLIENT";
        }
        Matcher m = Pattern.compile("\"role\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
        if (m.find()) {
            return m.group(1).replace("\\\\", "\\").replace("\\\"", "\"");
        }
        return "CLIENT";
    }

    /** Jeton de session opaque (réponse LOGIN / REGISTER) pour les requêtes authentifiées côté serveur. */
    public static String parseAuthSessionToken(String json) {
        if (json == null) {
            return "";
        }
        Matcher m = Pattern.compile("\"sessionToken\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
        if (m.find()) {
            return m.group(1).replace("\\\\", "\\").replace("\\\"", "\"");
        }
        return "";
    }

    /** Ligne de fiche produit (modération / espace vendeur). */
    public record PendingListingRow(
            int productId,
            String nomProduit,
            String sku,
            int sellerId,
            String listingStatus,
            long submittedAtMs,
            String rejectionReason) {}

    /**
     * Parse le JSON tableau renvoyé par {@code LIST_PENDING_PRODUCTS} / {@code LIST_MY_PRODUCT_LISTINGS}.
     */
    public static List<PendingListingRow> parsePendingListingRows(String jsonArray) {
        List<PendingListingRow> out = new ArrayList<>();
        if (jsonArray == null || jsonArray.isBlank()) {
            return out;
        }
        Matcher m =
                Pattern.compile(
                                "\"productId\":(\\d+),\"nomProduit\":\"([^\"]*)\",\"sku\":\"([^\"]*)\",\"sellerId\":(\\d+),\"listingStatus\":\"([^\"]*)\",\"submittedAtMs\":(\\d+),\"rejectionReason\":\"([^\"]*)\"")
                        .matcher(jsonArray);
        while (m.find()) {
            out.add(
                    new PendingListingRow(
                            Integer.parseInt(m.group(1)),
                            m.group(2).replace("\\\\", "\\").replace("\\\"", "\""),
                            m.group(3),
                            Integer.parseInt(m.group(4)),
                            m.group(5),
                            Long.parseLong(m.group(6)),
                            m.group(7).replace("\\\\", "\\").replace("\\\"", "\"")));
        }
        return out;
    }

    /** Nom d’utilisateur (chaîne échappée JSON). */
    public static String parseAuthUsername(String json) {
        if (json == null) {
            return "";
        }
        Matcher m = Pattern.compile("\"username\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
        return m.find() ? m.group(1).replace("\\\\", "\\").replace("\\\"", "\"") : "";
    }

    /** Adresse e-mail. */
    public static String parseAuthEmail(String json) {
        if (json == null) {
            return "";
        }
        Matcher m = Pattern.compile("\"email\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
        return m.find() ? m.group(1).replace("\\\\", "\\").replace("\\\"", "\"") : "";
    }

    /** Chiffres du téléphone depuis le JSON login / inscription / profil. */
    public static String parseAuthPhone(String json) {
        if (json == null) {
            return "";
        }
        Matcher m = Pattern.compile("\"phone\"\\s*:\\s*(\\d+)").matcher(json);
        return m.find() ? m.group(1) : "";
    }

    /** Lit {@code emailVerified} ; {@code null} si absent. */
    public static Boolean parseAuthEmailVerified(String json) {
        if (json == null) {
            return null;
        }
        Matcher m = Pattern.compile("\"emailVerified\"\\s*:\\s*(true|false)").matcher(json);
        if (m.find()) {
            return Boolean.parseBoolean(m.group(1));
        }
        return null;
    }

    /** Lit une valeur chaîne dans un petit objet JSON (ex. indices de contact masqués). */
    public static String extractJsonStringValue(String json, String key) {
        if (json == null || key == null) {
            return "";
        }
        Matcher m =
                Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
        if (!m.find()) {
            return "";
        }
        return m.group(1).replace("\\\\", "\\").replace("\\\"", "\"");
    }

    /**
     * Résume le JSON de paiement serveur en texte court pour l’utilisateur (sans noms de champs techniques).
     */
    public static String formatPaymentSummaryForUser(String json) {
        if (json == null || json.isBlank()) {
            return "Paiement enregistré.";
        }
        Double amount = null;
        Matcher ma = Pattern.compile("\"montantFinal\"\\s*:\\s*([0-9.+-eE]+)").matcher(json);
        if (ma.find()) {
            try {
                amount = Double.parseDouble(ma.group(1));
            } catch (NumberFormatException ignored) {
            }
        }
        String statut = "";
        Matcher ms = Pattern.compile("\"statut\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
        if (ms.find()) {
            statut = ms.group(1);
        }
        StringBuilder sb = new StringBuilder();
        if ("ACCEPTE".equals(statut)) {
            sb.append("Paiement accepté.");
        } else if ("REFUSE".equals(statut) || "REMBOURSE".equals(statut)) {
            sb.append("Le paiement n'a pas été finalisé.");
        } else {
            sb.append("Paiement traité.");
        }
        if (amount != null) {
            sb.append("\nMontant : ")
                    .append(String.format(Locale.FRENCH, "%.2f", amount))
                    .append(" USD.");
        }
        String detail = extractJsonString(json, "message");
        if (detail != null && !detail.isBlank() && !detail.contains("PAY-") && !detail.contains("id")) {
            sb.append("\n").append(detail);
        }
        return sb.toString().trim();
    }

    private static String extractJsonString(String json, String key) {
        Matcher m = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
        if (!m.find()) {
            return "";
        }
        return m.group(1).replace("\\\\", "\\").replace("\\\"", "\"");
    }

    /** Ligne renvoyée par {@code ADMIN_PRODUCT_LIST}. */
    public record AdminCatalogRow(
            int productId,
            String nom,
            String sku,
            String marque,
            String categorieMetier,
            int stock,
            String listingStatus,
            double prixUsd) {}

    /** Parse le tableau JSON {@code ADMIN_PRODUCT_LIST}. */
    public static List<AdminCatalogRow> parseAdminCatalogRows(String jsonArray) {
        List<AdminCatalogRow> out = new ArrayList<>();
        if (jsonArray == null || jsonArray.length() < 5) {
            return out;
        }
        Pattern p =
                Pattern.compile(
                        "\"productId\":(\\d+),\"nomProduit\":\"([^\"]*)\",\"sku\":\"([^\"]*)\","
                                + "\"marque\":\"([^\"]*)\",\"categorieMetier\":\"([^\"]*)\","
                                + "\"stock\":(\\d+),\"listingStatus\":\"([^\"]*)\",\"prixUsd\":([0-9.+-eE]+)");
        Matcher m = p.matcher(jsonArray);
        while (m.find()) {
            out.add(
                    new AdminCatalogRow(
                            Integer.parseInt(m.group(1)),
                            jsonUnesc(m.group(2)),
                            jsonUnesc(m.group(3)),
                            jsonUnesc(m.group(4)),
                            jsonUnesc(m.group(5)),
                            Integer.parseInt(m.group(6)),
                            jsonUnesc(m.group(7)),
                            Double.parseDouble(m.group(8))));
        }
        return out;
    }

    /** Parse la réponse {@code LIST_SAVED_PAYMENT_METHODS} en objets typés. */
    public static List<SavedPaymentEntry> parseSavedPaymentEntries(String json) {
        List<SavedPaymentEntry> out = new ArrayList<>();
        if (json == null || json.isBlank()) {
            return out;
        }
        Pattern p =
                Pattern.compile(
                        "\\{\"id\":(\\d+),\"type\":\"([^\"]*)\",\"label\":\"([^\"]*)\",\"createdAt\":\"([^\"]*)\"\\}");
        Matcher m = p.matcher(json);
        while (m.find()) {
            out.add(
                    new SavedPaymentEntry(
                            Integer.parseInt(m.group(1)),
                            jsonUnesc(m.group(2)),
                            jsonUnesc(m.group(3)),
                            jsonUnesc(m.group(4))));
        }
        return out;
    }

    private static String jsonUnesc(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\\\", "\\").replace("\\\"", "\"");
    }

    /** Parse un tableau JSON de chaînes, ex. {@code ["Tous","A"]} pour les catégories. */
    public static List<String> parseStringArray(String json) {
        if (json == null || json.length() < 2) {
            return List.of();
        }
        String inner = json.trim();
        if (inner.startsWith("[")) {
            inner = inner.substring(1, inner.length() - 1).trim();
        }
        if (inner.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String part : inner.split(",")) {
            String s = part.trim();
            if (s.startsWith("\"")) {
                s = s.substring(1);
            }
            if (s.endsWith("\"")) {
                s = s.substring(0, s.length() - 1);
            }
            out.add(s.replace("\\\"", "\""));
        }
        return out;
    }
}


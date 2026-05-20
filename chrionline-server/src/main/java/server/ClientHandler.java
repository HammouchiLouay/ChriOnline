package server;

import common.ChrionlineLog;
import common.JsonUtil;
import common.Message;
import common.crypto.AesGcmLineCipher;
import common.crypto.ApplicationSessionRsaKeys;
import common.crypto.RsaOaepAesKeyWrap;
import common.ssl.SslConfigLoader;

import javax.crypto.SecretKey;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;
import java.util.Map;
import java.util.Properties;

/**
 * Traite une connexion TCP client : lit des lignes JSON ({@link Message}), passe par {@link RequestRouter},
 * renvoie la réponse JSON sur la même socket.
 *
 * <p>The RSA→AES application protocol is mandatory: the server sends its RSA public key, unwraps the client's AES
 * session key with RSA-OAEP, then all following lines are encrypted with AES-GCM.
 */
public class ClientHandler implements Runnable {

    private final Socket socket;

    /** @param socket connexion acceptée par le {@link java.net.ServerSocket} du serveur */
    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    /** Boucle lecture / routage / écriture jusqu’à fermeture du client. */
    @Override
    public void run() {
        Properties sec = SslConfigLoader.load();
        try {
            BufferedReader input =
                    new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            PrintWriter output =
                    new PrintWriter(new java.io.OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);

            ChrionlineLog.info("Handler started for : " + socket.getInetAddress());

            runNegotiatedProtocol(sec, input, output);

        } catch (Exception e) {
            ChrionlineLog.err("Client disconnected unexpectedly: " + e.getMessage(), e);
        } finally {
            try {
                socket.close();
                ChrionlineLog.info("Connection closed : " + socket.getInetAddress());
            } catch (Exception e) {
                ChrionlineLog.err("Error closing socket", e);
            }
        }
    }

    private static void runNegotiatedProtocol(Properties sec, BufferedReader input, PrintWriter output)
            throws Exception {
        String firstLine = input.readLine();
        if (firstLine == null) {
            return;
        }
        Message first = JsonUtil.fromJson(firstLine);
        if (!"CLIENT_HELLO".equalsIgnoreCase(first.getType())) {
            Message err = new Message("SERVER_HELLO", first.getRequestId(), "ERROR", "", "CRYPTO_REQUIRED");
            output.println(JsonUtil.toJson(err));
            ChrionlineLog.warn("Protocol mismatch: RSA/AES is mandatory but first message was " + first.getType());
            return;
        }

        Map<String, String> hello = JsonUtil.toMap(first.getPayload());
        boolean clientWantsCrypto = Boolean.parseBoolean(hello.getOrDefault("crypto", "false"));
        ChrionlineLog.info(
                "Socket handshake: clientRequestedCrypto="
                        + clientWantsCrypto
                        + ", serverSupported=true, serverRequired=true");

        if (!clientWantsCrypto) {
            Message err = new Message("SERVER_HELLO", first.getRequestId(), "ERROR", "", "CRYPTO_REQUIRED");
            output.println(JsonUtil.toJson(err));
            ChrionlineLog.warn("Socket handshake rejected: RSA/AES is mandatory.");
            return;
        }

        PrivateKey serverPrivate = ApplicationSessionRsaKeys.privateKey(sec);
        PublicKey serverPublic = ApplicationSessionRsaKeys.publicKey(sec);

        String spkiB64 = Base64.getEncoder().encodeToString(serverPublic.getEncoded());
        String announcePayload = "{\"crypto\":\"rsa-aes\",\"publicKeySpkiB64\":\"" + spkiB64 + "\"}";
        Message announce = new Message("SERVER_HELLO", first.getRequestId(), "SUCCESS", announcePayload, "");
        output.println(JsonUtil.toJson(announce));

        String keyLine = input.readLine();
        if (keyLine == null) {
            return;
        }
        ChrionlineLog.info("Secure handshake: SECURE_KEY_EXCHANGE (" + keyLine.length() + " chars)");
        Message keyMsg = JsonUtil.fromJson(keyLine);
        if (!"SECURE_KEY_EXCHANGE".equalsIgnoreCase(keyMsg.getType())) {
            ChrionlineLog.warn("Expected SECURE_KEY_EXCHANGE, got " + keyMsg.getType());
            output.println(JsonUtil.toJson(new Message("SECURE_SESSION_OK", keyMsg.getRequestId(), "ERROR", "", "PROTOCOL_ERROR")));
            return;
        }
        Map<String, String> m = JsonUtil.toMap(keyMsg.getPayload());
        String w = m.get("wrappedKey");
        if (w == null || w.isBlank()) {
            ChrionlineLog.warn("SECURE_KEY_EXCHANGE missing wrappedKey");
            output.println(JsonUtil.toJson(new Message("SECURE_SESSION_OK", keyMsg.getRequestId(), "ERROR", "", "PROTOCOL_ERROR")));
            return;
        }
        byte[] wrapped = Base64.getDecoder().decode(w.trim());
        SecretKey aesSession = RsaOaepAesKeyWrap.unwrapAesKey(serverPrivate, wrapped);

        Message ok = new Message("SECURE_SESSION_OK", keyMsg.getRequestId(), "SUCCESS", "{}", "");
        output.println(JsonUtil.toJson(ok));
        ChrionlineLog.info("Socket handshake success: RSA/AES session established.");

        String encRequest;
        while ((encRequest = input.readLine()) != null) {
            ChrionlineLog.info("Request received (AES-GCM wire), len=" + encRequest.length());
            String plainRequest = AesGcmLineCipher.decryptLine(encRequest, aesSession);
            Message message = JsonUtil.fromJson(plainRequest);
            Message response = RequestRouter.route(message);
            logRouterError(message, response);
            String plainResponse = JsonUtil.toJson(response);
            output.println(AesGcmLineCipher.encryptLine(plainResponse, aesSession));
        }
    }

    private static void logRouterError(Message message, Message response) {
        if (message != null && "ERROR".equals(response.getStatus())) {
            String code = response.getErrorCode();
            ChrionlineLog.warn(
                    "Router ERROR type="
                            + message.getType()
                            + " code="
                            + (code != null ? code : ""));
        }
    }
}

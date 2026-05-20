package client;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

import common.Message;
import common.JsonUtil;

/**
 * Client console de test du protocole socket : menu interactif pour envoyer les types de messages courants.
 */
public class TestClient {

    /** Connexion à {@code CHRIONLINE_SERVER_HOST} ou premier argument ; port second argument ou 6000. */
    public static void main(String[] args) {
        try {
            String host = System.getenv("CHRIONLINE_SERVER_HOST");
            if (host == null || host.isBlank()) {
                host = args.length > 0 ? args[0].trim() : "";
            }
            int port = 6000;
            if (args.length >= 2) {
                try {
                    port = Integer.parseInt(args[1].trim());
                } catch (NumberFormatException e) {
                    System.err.println("Port invalide.");
                    System.exit(1);
                    return;
                }
            }
            if (host.isBlank()) {
                host = "127.0.0.1";
            }
            Socket socket = new Socket(host, port);
            System.out.println("Connected to server " + host + ":" + port);

            BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter output = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader console = new BufferedReader(new InputStreamReader(System.in));

            while (true) {
                System.out.println("\nChoisir une commande :");
                System.out.println("1. PING");
                System.out.println("2. LOGIN");
                System.out.println("3. REGISTER");
                System.out.println("4. PRODUCT_LIST");
                System.out.println("5. PRODUCT_DETAILS");
                System.out.println("6. STOCK_UPDATE");
                System.out.println("7. CREATE_COMMANDE");
                System.out.println("8. GET_COMMANDES");
                System.out.println("9. VALIDER_COMMANDE");
                System.out.println("10. ANNULER_COMMANDE");
                System.out.println("11. SIMULATE_PAYMENT (paiement)");
                System.out.println("0. EXIT");

                System.out.print("Choix: ");
                String choix = console.readLine();

                if ("0".equals(choix)) {
                    break;
                }

                Message message = null;

                switch (choix) {

                    case "1":
                        message = Message.request("PING", "1", "");
                        break;

                    case "2":
                        message = Message.request("LOGIN", "2", "");
                        break;

                    case "3":
                        message = Message.request("REGISTER", "3", "");
                        break;

                    case "4":
                        message = Message.request("PRODUCT_LIST", "4", "");
                        break;

                    case "5":
                        System.out.print("Product ID: ");
                        String productId = console.readLine();
                        message = Message.request("PRODUCT_DETAILS", "5", productId);
                        break;

                    case "6":
                        System.out.print("Product ID: ");
                        String updateId = console.readLine();
                        System.out.print("New stock: ");
                        String stock = console.readLine();
                        String stockPayload = "{\"id\":\"" + updateId + "\",\"stock\":" + stock + "}";
                        message = Message.request("STOCK_UPDATE", "6", stockPayload);
                        break;

                    case "7":
                        System.out.print("User ID: ");
                        String userId = console.readLine();
                        System.out.print("Produits (ex: 1:2;3:1): ");
                        String produits = console.readLine();
                        String payloadCreate = "{\"userId\":\"" + userId + "\",\"produits\":\"" + produits + "\"}";
                        message = Message.request("CREATE_COMMANDE", "7", payloadCreate);
                        break;

                    case "8":
                        System.out.print("Session token (UUID from LOGIN / REGISTER, champ sessionToken): ");
                        String sessionTok = console.readLine();
                        if (sessionTok == null) {
                            sessionTok = "";
                        }
                        sessionTok = sessionTok.trim();
                        message =
                                Message.request(
                                        "GET_COMMANDES",
                                        "8",
                                        "{\"sessionToken\":\"" + sessionTok.replace("\\", "\\\\").replace("\"", "\\\"")
                                                + "\"}");
                        break;

                    case "9":
                        System.out.print("ID commande: ");
                        String idVal = console.readLine();
                        message = Message.request("VALIDER_COMMANDE", "9", idVal);
                        break;

                    case "10":
                        System.out.print("Session token (LOGIN / REGISTER): ");
                        String tokAnn = console.readLine();
                        if (tokAnn == null) {
                            tokAnn = "";
                        }
                        tokAnn = tokAnn.trim().replace("\\", "\\\\").replace("\"", "\\\"");
                        System.out.print("ID commande: ");
                        String idAnn = console.readLine();
                        message =
                                Message.request(
                                        "ANNULER_COMMANDE",
                                        "10",
                                        "{\"sessionToken\":\""
                                                + tokAnn
                                                + "\",\"commandeId\":\""
                                                + (idAnn != null ? idAnn.trim() : "")
                                                + "\"}");
                        break;

                    case "11":
                        System.out.print("ID commande: ");
                        String idPay = console.readLine();
                        System.out.print("User ID (doit correspondre au propriétaire de la commande): ");
                        String uidPay = console.readLine();
                        System.out.print("Type paiement (ex: CARTE_BANCAIRE, A_LA_LIVRAISON, PAYPAL): ");
                        String typePay = console.readLine();
                        System.out.print("Coupon (vide si aucun, ex: PROMO10): ");
                        String coupon = console.readLine();
                        String payPayload =
                                "{\"commandeId\":\""
                                        + idPay
                                        + "\",\"userId\":\""
                                        + uidPay
                                        + "\",\"typePaiement\":\""
                                        + typePay
                                        + "\",\"coupon\":\""
                                        + coupon
                                        + "\",\"saveTemplate\":\"false\",\"holderName\":\"\",\"lastFour\":\"4242\","
                                        + "\"brand\":\"Visa\",\"expMonth\":\"12\",\"expYear\":\"28\",\"paypalCode\":\"PAYPAL-TEST-889912\","
                                        + "\"walletAlias\":\"\"}";
                        message = Message.request("SIMULATE_PAYMENT", "11", payPayload);
                        break;

                    default:
                        System.out.println("Choix invalide");
                        continue;
                }

                String json = JsonUtil.toJson(message);
                output.println(json);

                String response = input.readLine();
                System.out.println("Server response : " + response);
            }

            socket.close();
            System.out.println("Connection closed");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

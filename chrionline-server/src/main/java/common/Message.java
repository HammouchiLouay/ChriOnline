package common;

/**
 * Enveloppe d’un échange socket : type de requête, identifiant, statut, corps JSON et code d’erreur éventuel.
 */
public class Message {

    private String type;
    private String requestId;
    private String status;
    private String payload;
    private String errorCode;

    /** Constructeur vide pour désérialisation ou remplissage manuel. */
    public Message() {}

    /**
     * Construit un message complet (réponse serveur ou requête déjà formée).
     *
     * @param type nom logique du message (ex. LOGIN, PRODUCT_LIST)
     * @param requestId identifiant de corrélation côté client
     * @param status ex. SUCCESS ou ERROR
     * @param payload corps texte (souvent JSON ou Base64)
     * @param errorCode code machine si échec, sinon chaîne vide
     */
    public Message(String type, String requestId, String status, String payload, String errorCode) {
        this.type = type;
        this.requestId = requestId;
        this.status = status;
        this.payload = payload;
        this.errorCode = errorCode;
    }

    /** @return type du message (commande) */
    public String getType() {
        return type;
    }

    /** @return identifiant de requête pour associer la réponse */
    public String getRequestId() {
        return requestId;
    }

    /** @return statut d’exécution (SUCCESS, ERROR, …) */
    public String getStatus() {
        return status;
    }

    /** @return charge utile (JSON, texte libre ou données encodées) */
    public String getPayload() {
        return payload;
    }

    /** @return code d’erreur lorsque {@link #getStatus()} indique un échec */
    public String getErrorCode() {
        return errorCode;
    }

    public void setType(String type) {
        this.type = type;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    /**
     * Fabrique une requête sortante sans statut ni code d’erreur (le serveur les remplira dans la réponse).
     *
     * @param type commande (LOGIN, REGISTER, …)
     * @param requestId id de corrélation
     * @param payload corps JSON ou vide
     */
    public static Message request(String type, String requestId, String payload) {
        Message m = new Message();
        m.setType(type);
        m.setRequestId(requestId);
        m.setPayload(payload);
        m.setStatus("");
        m.setErrorCode("");
        return m;
    }
}

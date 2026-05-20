# ChriOnline — merged Java server

Single Maven module combining:

| Source | Contents |
|--------|----------|
| **Product_tests** | TCP server (`ServerMain`, `ClientHandler`, `RequestRouter`), products, commandes, `TestClient` |
| **Personne_1** | JDBC user layer (`chrionline.User`, `UserDAO`, `BaseDonnees`, `Authentification`, …) |
| **personne5 / ssi-project** | Payment simulation (`ecommerce.personne5.*`), `model.PromotionCampaign` |

### Entry points

- **Server:** `server.ServerMain` (port 6000)
- **Console client:** `client.TestClient`
- **JavaFX client (UI):** `ui.ChriOnlineClientApp` — **do not** use “Run As → Java Application” unless you configure the JavaFX module path (see below). Prefer `mvn javafx:run`, the `run-javafx.bat` script, or an Eclipse **Maven** run configuration.
- **Standalone payment demo:** `ecommerce.personne5.main.PaiementApp`

### Socket API — catalogue & auth

- **`PRODUCT_LIST`** — optional payload `{"category":"Electronique"}` (omit or `"Tous"` for all). Products come from MySQL `products` when JDBC works; otherwise three in-memory fallbacks.
- **`PRODUCT_CATEGORIES`** — returns a JSON string array of category names (includes `Tous`).
- **`LOGIN`** — payload `{"email":"...","password":"..."}` → JSON with `userId`, `username`, `email` on success (MySQL `user` via `UserDAO`).
- **`REGISTER`** — payload `{"username":"...","email":"...","password":"...","phone":"..."}` (phone digits; fits `INT`).
- **`DELETE_ACCOUNT`** — payload `{"userId":"…","currentPassword":"…"}` and, if e-mail is verified, `"securityOtp":"…"` (same 6-digit flow as `PROFILE_OTP_SEND`). Removes the user row and related orders, payment history, and saved payment methods (`AccountDeletionService`).

### Socket API — payment

- **Type:** `SIMULATE_PAYMENT`
- **Payload (JSON):** inclut `commandeId`, `userId`, `typePaiement`, `coupon`, champs carte (`holderName`, `lastFour`, `brand`, `expMonth`, `expYear`), `paypalCode` (référence de simulation — pas d’e-mail), `walletAlias`, `saveTemplate`.  
  `typePaiement` = enum `ecommerce.personne5.model.TypePaiement` (`CARTE_BANCAIRE`, `A_LA_LIVRAISON`, `PAYPAL`, …). Coupon optionnel.
- **Bridge:** `services.SocketPaymentService` → `ecommerce.personne5.service.PaiementService`.  
  On success, server `models.Commande` status becomes `PAYEE`; on failure `ANNULEE`.

### JavaFX — “runtime components are missing”

JavaFX is **not** bundled in the JDK. Starting `ChriOnlineClientApp` with the plain `java` launcher (Eclipse “Java Application”) does not load the JavaFX modules, which triggers that error.

**Use one of these:**

1. **Windows (no Maven install):** double‑click `run-javafx.bat` — it uses **Maven Wrapper** (`mvnw.cmd`), which downloads Maven automatically; you do **not** need `mvn` on your PATH.
2. **Command line:** from `chrionline-server`, run `mvnw.cmd javafx:run` (Windows) or `./mvnw javafx:run` (Linux/macOS). If you already have Maven installed globally, `mvn javafx:run` also works.
3. **Eclipse:** **Run → Run Configurations… → Maven Build → New** → Goals: `javafx:run` → Base directory: this project → Run. (Or import `chrionline-javafx.launch` if your project name matches.)

Set **JAVA_HOME** to a **JDK 17+** (the project targets Java 17; JavaFX 21 needs a recent JDK). If `mvnw -version` shows Java 8, point `JAVA_HOME` at JDK 17 before running the UI.

### Hosting the server on your PC (LAN)

- Run **`server.ServerMain`** on the machine that has **MySQL + the `chrionline` database** (XAMPP on your PC). The server listens on **`0.0.0.0:6000`**, so other computers on the same Wi‑Fi/LAN can connect.
- **Clients do not connect to MySQL directly.** They open a TCP socket to **your PC’s IPv4 address** and port **6000**. Only the server process uses JDBC to `localhost` MySQL.
- **Same PC (server + JavaFX client):** the UI defaults to **`127.0.0.1`** and allows localhost. Optional overrides live under **`~/.chrionline/`** (see `ClientConfigLoader` / packaged `chrionline-client.properties`) — no paths tied to a specific machine in the repo.
- **Automatic host:** the JavaFX client resolves the server via **LAN multicast** (UDP **47474**, group `239.255.42.73`) when `chrionline-client.properties` leaves `server.host` empty. Override with `-Dchrionline.server.host=...` or edit that file for Internet / VPN setups.
- **Windows firewall:** step‑by‑step rules are in **[FIREWALL-SETUP.md](FIREWALL-SETUP.md)** (TCP **6000** inbound on the server PC).
- The server console still prints **LAN IPv4 addresses** if you need to connect manually.
- **Product images** are loaded from **HTTPS URLs** stored in the DB (often **WebP**). The JavaFX client uses **ImageIO + WebP** (`imageio-webp`) so thumbnails display correctly.

### E-mail verification (no Java source edits required)

- Run the SQL migration **`../sql/alter_user_email_verified.sql`** on your `chrionline` database (adds `email_verified`).
- **Shoppers** receive codes at the **e-mail they registered**; **sending** uses **one** application-wide credential (Resend API key or SMTP). You do **not** collect users’ personal mailbox passwords in the client.
- **Configure the host that runs `ServerMain`** using any of:
  - **Environment variables:** `CHRIONLINE_RESEND_API_KEY` + `CHRIONLINE_MAIL_FROM`, or `CHRIONLINE_SMTP_HOST`, `CHRIONLINE_SMTP_USER`, `CHRIONLINE_SMTP_PASSWORD`, … (see `MailConfigLoader`).
  - **User file (no repo access):** `%USERPROFILE%\.chrionline\email-config.properties` (Windows) or `~/.chrionline/email-config.properties` — copy from `email-config.properties.example`.
  - Optional packaged defaults in `src/main/resources/email-config.properties` (usually left without secrets).
- Restart **`ServerMain`** after changing config. If mail is not configured, codes still appear on the **server console** for development.

### Build

```bash
mvn package
```

**Run the socket server (recommended for sharing with others)** — use the **fat JAR** so every dependency is inside one file:

```bash
java -jar target/chrionline-server-1.0-SNAPSHOT-jar-with-dependencies.jar
```

Or on Windows, from this folder: `run-server.bat` (uses Maven Wrapper + the JAR above).

**Why `Could not find or load main class server.ServerMain` happens**

- Running `java -jar` on a **wrong or empty** file (not built with `mvn package`, or not the JAR from `target/`).
- Running `java server.ServerMain` **without** `-cp` pointing at compiled classes and all libraries — use `-jar` with the fat JAR instead.
- The **thin** JAR `chrionline-server-1.0-SNAPSHOT.jar` contains `ServerMain` but **not** MySQL/mail libraries; `java -jar` on it will usually fail later with `NoClassDefFoundError`. Prefer **`*-jar-with-dependencies.jar`** for a standalone server.

The fat JAR **excludes JavaFX** (server does not need it). For the desktop UI, keep using `mvn javafx:run` or `run-javafx.bat`.

(Ensure MySQL JDBC settings in `chrionline` classes match your DB when you wire auth.)

### Related folders (repo root)

- `../sql/` — generated `INSERT`s and schema notes  
- `../tools/` — Excel → SQL helper  
- `../docs/` — cahier des charges PDF  

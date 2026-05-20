# ChriOnline - Interview Questions And Answers

This file contains possible interview questions about the whole ChriOnline application.

The questions are separated by project responsibility:

- Personne 1: users, authentication and account security
- Personne 2: catalogue, products, stock and moderation
- Personne 3: cart
- Personne 4: orders
- Personne 5: payment
- RSA/AES socket encryption
- RSA admin login
- JavaFX
- Maven

## Personne 1 - Users, Authentication And Account Security

### 1. What is the role of `chrionline.User`?

`User` is the Java model of a row in the `user` table. It stores the account ID, username, hashed password, e-mail, phone number, role, e-mail verification status and admin RSA public key.

Important methods include `get_email()`, `get_role()`, `isEmailVerified()` and `getAdminPublicKeyPem()`.

### 2. Why does the project store `hash_password` instead of the real password?

The real password must never be stored. ChriOnline uses `PasswordHasher` with BCrypt to store a one-way hash. During login, the entered password is checked with `PasswordHasher.verify(...)`.

### 3. What does `PasswordHasher.hash(String plainPassword)` do?

It creates a BCrypt hash from the plain password. BCrypt automatically includes a random salt and a cost factor, which makes brute-force attacks harder.

Library involved:

- `org.mindrot.jbcrypt.BCrypt`: password hashing library.

### 4. What does `PasswordHasher.verify(String plainPassword, String stored)` do?

It checks whether the plain password matches the stored value. If the stored value is BCrypt, it uses `BCrypt.checkpw`. If the stored value is an old legacy plain password, it compares directly so the old database can still work during migration.

### 5. Why does `PasswordHasher.needsRehash(...)` exist?

It detects old passwords that are not stored as BCrypt. After a successful legacy login, the app can replace the old value with a secure BCrypt hash.

### 6. What is the role of `UserDAO`?

`UserDAO` is the Data Access Object for the `user` table. It contains SQL operations such as create, find, update and delete.

Important methods:

- `createUser(User user)`
- `findById(int id_user)`
- `findByEmail(String email)`
- `findByPhoneNumber(Integer phone_number)`
- `emailExists(String email)`
- `updatePassword(...)`
- `updateEmailVerified(...)`

### 7. Why does `UserDAO` use `PreparedStatement`?

`PreparedStatement` separates SQL code from user input. This prevents SQL injection and handles escaping safely.

Library involved:

- `java.sql.PreparedStatement`: JDBC prepared SQL statement.

### 8. What is the role of `BaseDonnees`?

`BaseDonnees` centralizes the MySQL connection. It provides `getConnection()`, `verifyConnection()`, `jdbcUrl()`, `currentDatabaseName()` and `describeProductsTable()`.

It is useful for debugging because it prints the exact database used by the running server.

### 9. Which library connects Java to MySQL?

The project uses MySQL Connector/J:

```xml
<dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
</dependency>
```

It allows Java JDBC code to connect to MySQL.

### 10. What does `Authentification.loginByEmailOrPhone(...)` do?

It accepts either an e-mail or a phone number. If the input contains `@`, it searches by e-mail. Otherwise it normalizes the phone number and searches by phone. Then it verifies the password with `PasswordHasher.verify(...)`.

### 11. Why is `PhoneNumberLookup` needed?

The database stores phone numbers as integers. `PhoneNumberLookup` extracts digits and converts phone input into a stored integer format.

Important methods:

- `digitsOnly(String s)`
- `parseStoredPhoneInt(String digitsOnly)`

### 12. What is `AuthService` responsible for?

`AuthService` receives socket messages for login and registration. It parses the payload, validates input, uses `Authentification` and `UserDAO`, then returns a `Message`.

Important methods:

- `login(Message request)`
- `register(Message request)`

### 13. What is `SessionRegistry`?

`SessionRegistry` stores active sessions in memory. After login, it creates a token with `issueToken(int userId)`. Later, services call `resolveUser(String token)` to know which user is connected.

### 14. Why use a session token instead of sending the password again?

The password should only be used during login. After login, the client uses a session token. This avoids repeatedly sending credentials and makes it possible to revoke a session.

### 15. What is `EmailVerificationService`?

It handles e-mail verification codes. It can send a code with `send(Message request)` and verify it with `confirm(Message request)`.

It uses `MailService` to send e-mails.

### 16. What is `PasswordResetService`?

It handles forgotten passwords. It generates a temporary code, sends it to the user, and allows password reset if the code is valid.

Important methods:

- `forgotPassword(Message request)`
- `resetPassword(Message request)`

### 17. What is `ProfileSecurityService`?

It sends and verifies OTP codes for sensitive profile changes, such as changing the password, e-mail or phone number.

### 18. What is `AccountDeletionService`?

It handles account deletion. It verifies the request, checks password/OTP rules, then uses `AccountDeletionDAO` to delete the account and related data.

### 19. Why is `AccountDeletionDAO.deleteAllForUser(...)` important?

Deleting only the user row could leave orphaned orders, payment history or saved methods. `deleteAllForUser(...)` deletes related data in a controlled way.

### 20. Which mail library is used?

The project uses Angus Mail:

```xml
<dependency>
    <groupId>org.eclipse.angus</groupId>
    <artifactId>angus-mail</artifactId>
</dependency>
```

It sends SMTP e-mails for verification codes, reset codes and security OTPs.

## Personne 2 - Catalogue, Products, Stock And Moderation

### 1. What is the role of `product.Product`?

`Product` is the Java model of a product shown in the catalogue. It contains ID, name, description, price, stock, image URL, category, brand and rating.

### 2. Why does `Product` implement `Serializable`?

Some product lists are sent through the socket as serialized Java objects encoded in Base64. `Serializable` allows Java to convert the object into bytes.

### 3. What is `ProductCatalogDAO`?

`ProductCatalogDAO` is the SQL access class for the `products` table. It loads products, filters categories, inserts seller listings, approves/rejects listings, updates stock and creates admin products.

### 4. What are the most important read methods in `ProductCatalogDAO`?

Important methods include:

- `loadAll()`
- `loadByCategory(String category)`
- `loadPage(int offset, int limit)`
- `loadPageByCategory(String category, int offset, int limit)`
- `findByProductId(int productId)`
- `distinctCategoriesApproved()`

### 5. What are the admin methods in `ProductCatalogDAO`?

Important admin methods include:

- `insertAdminDirectApproved(...)`
- `deleteProductById(int productId)`
- `updateStock(int productId, int newStock)`
- `listAllForAdmin(String search)`
- `distinctCategoriesForAdmin()`
- `isKnownAdminCategory(String category)`

### 6. What problem does `insertAdminDirectApproved(...)` solve?

It allows an admin to create a product that is immediately approved and visible. It also handles optional SKU generation and supports databases with moderation columns.

### 7. Why must SKU be optional?

The admin UI says SKU is optional. Therefore the server must support an empty SKU by generating a unique one. Otherwise a valid admin form could fail.

### 8. Why validate categories with `isKnownAdminCategory(...)`?

Categories should come from a controlled list. This avoids duplicates such as `Vetements`, `Vêtements`, `vetement`, or incorrect categories typed manually.

### 9. What is `TextUiNormalizer` used for?

`TextUiNormalizer` normalizes French category labels for display and matching. For example, it displays `Vêtements & mode` correctly while still accepting older stored variants.

Important methods:

- `normalizeFrenchUi(String s)`
- `categoryMatchVariants(String category)`

### 10. What is `ProductRepository`?

`ProductRepository` is a facade between services and the DAO. It tries to use MySQL through `ProductCatalogDAO`, but can fall back to in-memory products if the database is unavailable.

### 11. What is `ProductService`?

`ProductService` handles public catalogue socket commands.

Important methods:

- `list(Message request)`
- `details(Message request)`
- `updateStock(Message request)`
- `categories(Message request)`

### 12. What socket command does `ProductService.list(...)` handle?

It handles `PRODUCT_LIST`. It can return all products, a category filter or a paginated list.

### 13. What is `ProductListingService`?

It handles seller product submissions and admin moderation.

Important methods:

- `submit(Message request)`
- `listPending(Message request)`
- `listMine(Message request)`
- `approve(Message request)`
- `reject(Message request)`

### 14. What is the difference between `Product` and `ProductListingInfo`?

`Product` is for catalogue display. `ProductListingInfo` is for moderation and seller dashboards, where status, seller ID, submission date and rejection reason matter.

### 15. What is `AdminProductService`?

It handles admin catalogue management commands:

- list/search products
- create product
- update stock
- delete product
- list allowed categories

### 16. What validations should `AdminProductService.create(...)` perform?

It should validate:

- name is not empty
- price > 0
- stock >= 0
- category is known
- SKU is valid only if provided
- image URL is empty or starts with `http://` or `https://`

### 17. Why is `ProductImageLoader` separate from the main UI?

Image loading can be slow. `ProductImageLoader.loadAsync(...)` loads images in the background so the JavaFX UI does not freeze.

### 18. Which library supports WebP product images?

The project uses TwelveMonkeys ImageIO WebP:

```xml
<dependency>
    <groupId>com.twelvemonkeys.imageio</groupId>
    <artifactId>imageio-webp</artifactId>
</dependency>
```

It helps Java decode `.webp` images.

## Personne 3 - Cart

### 1. Is there a `Panier` table in MySQL?

No. The cart is temporary client-side state inside `ChriOnlineClientApp`. It becomes a real order only when the user creates a command.

### 2. Where is the cart stored?

It is stored in a map in `ChriOnlineClientApp`, usually as `productId -> quantity`.

### 3. Why is the cart client-side?

The cart is temporary and only matters during the shopping session. The database stores real orders, not unfinished carts.

### 4. What does `addProductDetailToCart()` do?

It adds the currently opened product detail to the cart using the selected quantity. If the product already exists in the cart, it increments the quantity.

### 5. What does `updateCartSummary()` do?

It recalculates the number of items, the total price and the visible cart label in the UI.

### 6. What does `findProduct(String id)` do?

It finds a `Product` from the cached catalogue data using the product ID.

### 7. What does `buildProduitsPayload()` do?

It converts the cart into a compact string for the server, such as:

```text
83:2;84:1
```

This means product `83` quantity `2`, and product `84` quantity `1`.

### 8. What does `createCommandeFromCart()` do?

It checks the session, builds the payload, sends `CREATE_COMMANDE` to the server and clears the cart if the order is created successfully.

### 9. Why does the server recalculate product prices?

The client cannot be trusted for final prices. The server uses `ProductRepository.findById(...)` to get the current product price and create reliable order lines.

### 10. Which classes are involved when a cart becomes an order?

Main classes:

- `ChriOnlineClientApp`
- `SocketApiClient`
- `Message`
- `RequestRouter`
- `CommandeService`
- `ProductRepository`
- `CommandeDAO`

## Personne 4 - Orders

### 1. What is `models.Commande`?

`Commande` is the server-side model of an order. It contains order ID, user ID, order lines, status and date.

### 2. What is `models.LigneCommande`?

`LigneCommande` is one product line inside an order. It stores product ID, product name, quantity and unit price.

### 3. Why store product name and price in `LigneCommande`?

The order should preserve what the user bought at that moment. If the product name or price changes later, the old order should still remain correct.

### 4. What does `Commande.calculerTotal()` do?

It sums the subtotal of every `LigneCommande`. Each subtotal is `quantite * prixUnitaire`.

### 5. What does `LigneCommande.calculerSousTotal()` do?

It calculates the total for one order line.

### 6. What is `CommandeDAO`?

`CommandeDAO` is the SQL access class for `orders` and `order_lines`.

Important methods:

- `insert(Connection, Commande)`
- `findById(int)`
- `findByUserId(int)`
- `updateStatus(int, String)`
- `valider(int)`
- `annuler(int)`

### 7. Why should order insertion use a transaction?

An order has an `orders` row and multiple `order_lines`. If one insert fails, the whole operation should roll back so the database does not contain incomplete orders.

### 8. What does `CommandeService.createCommandeAvecProduits(...)` do?

It parses the cart payload, loads products, builds order lines, calculates the total and saves the command with `CommandeDAO`.

### 9. What does `CommandeService.annulerCommande(...)` do?

It attempts to cancel an order if the status allows it. Paid or already processed orders should not be cancelled freely.

### 10. What does `CommandeService.updateCommandeStatus(...)` do?

It changes the order status. The payment service uses it to set an order to `PAYEE` or `ANNULEE`.

### 11. Which socket commands are related to orders?

Common order commands:

- `CREATE_COMMANDE`
- `GET_COMMANDES`
- `VALIDER_COMMANDE`
- `ANNULER_COMMANDE`

### 12. How does the JavaFX client display orders?

`SocketApiClient` parses order JSON into records such as `CommandeSummary`, `OrderLineSnapshot` and `CommandeFull`.

## Personne 5 - Payment

### 1. What is the purpose of the Personne 5 payment module?

It simulates payment behavior: payment type, coupon, delivery fees, fraud score, wallet, cashback, receipt, history and saved payment methods.

### 2. Is it connected to a real bank?

No. It is a simulation module. It models payment logic but does not contact a real bank or payment gateway.

### 3. What is `ecommerce.personne5.model.Paiement`?

It is the central payment result object. It stores payment ID, initial amount, final amount, delivery fees, coupon reduction, status, payment type, command ID, message, token and fraud score.

### 4. What is `PaiementService`?

`PaiementService` is the main payment engine. It validates coupons, calculates fees, simulates payment, updates stats, confirms payment and can refund.

Important methods:

- `verifierCoupon(String code)`
- `calculerFraisLivraison(double montantApresReduction)`
- `simulerPaiement(...)`
- `confirmerPaiement(...)`
- `rembourserPaiement(...)`

### 5. What is `SocketPaymentService`?

It connects the socket API to the Personne 5 module. It handles `SIMULATE_PAYMENT`, loads the command, calls `PaiementService`, updates the command status and saves payment history.

### 6. Why are there two `Commande` classes?

There is:

- `models.Commande`: main server order saved in MySQL.
- `ecommerce.personne5.model.Commande`: payment module order used for simulation.

The socket payment service converts between them.

### 7. What is `FraudDetectionService`?

It calculates a fraud score for a transaction and decides whether the transaction is allowed based on a threshold.

### 8. What is `WalletService`?

It provides operations on a `Wallet`, such as recharging and paying with wallet balance.

### 9. What is `PromotionService`?

It applies promotions or verifies coupon codes.

### 10. What is `PaiementQueueService`?

It stores pending payments in a queue and can process the next payment.

### 11. What is `PaymentHistoryDAO`?

It saves a payment result in the payment history table.

### 12. What is `SavedPaymentMethodDAO`?

It saves, lists and deletes masked payment method templates for a user.

Important methods:

- `insert(...)`
- `listByUser(int userId)`
- `deleteForUser(int methodId, int userId)`
- `toJsonArray(...)`

### 13. Why must saved payment methods be masked?

Sensitive payment details should not be shown or stored as raw full card details. The app stores display labels/templates, and can optionally encrypt stored data with `StorageCryptoService`.

### 14. What is `StorageCryptoService`?

It optionally encrypts sensitive stored templates with AES-GCM.

Important methods:

- `sealIfEnabled(String plaintext)`
- `openIfNeeded(String stored)`
- `reloadConfig()`

### 15. Which socket commands are related to payment?

Main commands:

- `SIMULATE_PAYMENT`
- `LIST_SAVED_PAYMENT_METHODS`
- `DELETE_SAVED_PAYMENT_METHOD`

## RSA/AES Socket Encryption

### 1. Is RSA/AES optional now?

No. RSA/AES is mandatory. The JavaFX checkbox was removed, and every socket connection must use the secure handshake.

### 2. What is RSA/AES protecting?

It protects socket traffic between the JavaFX client and the server. JSON messages are encrypted before they cross the network.

### 3. What is the handshake flow?

1. Client sends `CLIENT_HELLO` with `crypto=true`.
2. Server sends its RSA public key.
3. Client generates an AES-256 key.
4. Client wraps the AES key using RSA-OAEP.
5. Server unwraps it with its RSA private key.
6. Both sides use AES-GCM for all following messages.

### 4. What happens if a client sends plain JSON?

The server rejects it with `CRYPTO_REQUIRED`.

### 5. What class performs encryption on the client?

`SocketApiClient` performs the mandatory handshake and uses `AesGcmLineCipher` to encrypt and decrypt messages.

### 6. What class enforces encryption on the server?

`ClientHandler` enforces RSA/AES. It rejects plain clients, unwraps the AES key and decrypts incoming requests.

### 7. What does `AesGcmLineCipher.encryptLine(...)` do?

It encrypts one plaintext JSON line with AES-GCM and returns Base64 text suitable for socket transmission.

### 8. What does `AesGcmLineCipher.decryptLine(...)` do?

It decodes Base64, verifies the GCM authentication tag and returns the original JSON string.

### 9. Why use AES-GCM instead of plain AES-CBC?

AES-GCM provides both confidentiality and integrity. It detects if ciphertext was modified. AES-CBC alone does not provide authentication.

### 10. What does `RsaOaepAesKeyWrap.wrapAesKey(...)` do?

It encrypts the AES session key using the server's RSA public key and OAEP padding.

### 11. Why use RSA-OAEP?

OAEP is a modern RSA padding scheme designed for safer encryption. It is preferred over older PKCS#1 v1.5 encryption padding.

### 12. What does `ApplicationSessionRsaKeys` do?

It loads the server RSA key pair from a PKCS12 keystore, or generates an ephemeral key pair for development if no keystore is configured.

### 13. What is a PKCS12 keystore?

It is a password-protected file that stores private keys and certificates. In this project, it stores the server RSA private key and public certificate used during the socket handshake.

### 14. What logs prove RSA/AES is working?

Useful logs include:

```text
RSA/AES is mandatory
Socket handshake success: RSA/AES session established.
Request received (AES-GCM wire)
```

## RSA Admin Login

### 1. Is RSA admin login the same as RSA/AES socket encryption?

No. RSA/AES encrypts socket traffic. RSA admin login proves the admin owns a private RSA key.

### 2. What is challenge-response authentication?

The server sends a random challenge. The client signs it with a private key. The server verifies the signature with the public key.

### 3. What class creates challenges?

`ChallengeGenerator` creates random one-time challenges.

### 4. What class verifies the admin signature?

`RsaSignatureUtil` verifies the signature using `verifyChallenge(...)`.

### 5. What algorithm is used for RSA admin signatures?

The project uses:

```text
SHA256withRSA
```

### 6. Where is the admin public key stored?

It is stored in MySQL in the admin user's `admin_public_key_pem` column.

### 7. Where is the admin private key stored?

The private key is stored locally by the admin, usually as a PEM file. It must never be stored in MySQL or sent to the server.

### 8. What does `AdminAuthService.requestChallenge(...)` do?

It checks that the user exists, has role `ADMIN`, has a public key, then creates and stores a short-lived challenge.

### 9. What does `AdminAuthService.verifyChallenge(...)` do?

It reads the signature, loads the admin public key, verifies the signature and issues a normal session token if valid.

### 10. What does `PemKeyUtil` do?

It parses PEM text into Java key objects.

Important methods:

- `parsePkcs8PrivateKeyPem(String pem)`
- `parseX509PublicKeyPem(String pem)`

### 11. What does `GenerateAdminRsaKeys` do?

It generates an RSA key pair for admin login and writes PEM files.

### 12. Why is the challenge short-lived?

To prevent replay attacks. If someone captures an old signature, it should not work later.

## JavaFX

### 1. What is JavaFX used for in ChriOnline?

JavaFX is used for the desktop client UI: catalogue, login, cart, profile, payment, seller page and admin tools.

### 2. What is the main JavaFX class?

`ui.ChriOnlineClientApp` is the main JavaFX application. Its `start(Stage stage)` method builds the UI.

### 3. Why should JavaFX network calls avoid blocking the UI thread?

The JavaFX Application Thread renders the interface. Long network or image loading operations can freeze the UI if executed directly on it.

### 4. Which JavaFX classes are commonly used?

Common classes include:

- `Stage`: application window.
- `Scene`: content container.
- `Button`: clickable action.
- `Label`: text display.
- `TextField` / `PasswordField`: input fields.
- `TableView`: table display.
- `ComboBox`: dropdown.
- `VBox` / `HBox`: layouts.
- `ScrollPane`: scrollable content.

### 5. What is `SocketApiClient`'s role in JavaFX?

It is the UI's gateway to the server. The UI does not call services directly; it sends socket `Message` objects through `SocketApiClient`.

### 6. What does `UiMessages` do?

It converts server error codes into readable French user messages.

### 7. Why was the RSA/AES checkbox removed?

Because RSA/AES is now the standard mandatory security measure. The user no longer chooses whether traffic is encrypted.

### 8. What does `LanDiscoveryClient` do?

It tries to discover the server automatically on the local network using UDP multicast.

### 9. What does `StageResizeBehavior` do?

It adds resize behavior for the custom JavaFX window.

### 10. What does `BrandIconUtil` do?

It creates application icons for the window, title area and UI branding.

## Maven

### 1. What is Maven used for?

Maven manages dependencies, compilation, JavaFX execution and packaging.

### 2. What file defines the Maven project?

`pom.xml`.

### 3. What Java version does the project target?

The project targets Java 17.

Important properties:

```xml
<maven.compiler.source>17</maven.compiler.source>
<maven.compiler.target>17</maven.compiler.target>
```

### 4. Why is `project.build.sourceEncoding` important?

It ensures source files are compiled as UTF-8. This prevents French text from becoming garbled.

### 5. What does the JavaFX Maven plugin do?

It runs the JavaFX client with the correct JavaFX module setup.

Command:

```powershell
.\mvnw.cmd javafx:run
```

### 6. Why not run JavaFX as a normal Java application in some IDEs?

JavaFX is not bundled with the JDK. Running without the JavaFX module path can cause runtime errors such as missing JavaFX components.

### 7. What does the Maven Shade plugin do?

It builds a fat JAR that includes server dependencies such as MySQL and mail libraries.

### 8. Which command compiles the project?

```powershell
.\mvnw.cmd compile
```

### 9. Which command packages the project?

```powershell
.\mvnw.cmd package
```

### 10. Which dependency connects to MySQL?

```xml
<dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
</dependency>
```

### 11. Which dependency provides JavaFX controls?

```xml
<dependency>
    <groupId>org.openjfx</groupId>
    <artifactId>javafx-controls</artifactId>
</dependency>
```

### 12. Which dependency sends SMTP mail?

```xml
<dependency>
    <groupId>org.eclipse.angus</groupId>
    <artifactId>angus-mail</artifactId>
</dependency>
```

### 13. Which dependency helps decode WebP images?

```xml
<dependency>
    <groupId>com.twelvemonkeys.imageio</groupId>
    <artifactId>imageio-webp</artifactId>
</dependency>
```

### 14. What is Maven Wrapper?

Maven Wrapper is `mvnw.cmd` on Windows and `mvnw` on Linux/macOS. It lets the project run Maven commands without requiring Maven to be installed globally.

### 15. What is the difference between the server JAR and JavaFX launch?

The server can run as a packaged JAR. The JavaFX client is normally launched with `mvnw.cmd javafx:run` because it needs JavaFX modules.

## General Architecture

### 1. What is the main architecture of ChriOnline?

ChriOnline is a client/server application:

- JavaFX client for UI.
- Socket server for business operations.
- MySQL database behind the server.
- SMTP mail service for verification.
- RSA/AES encryption for socket traffic.

### 2. Why should the client not connect directly to MySQL?

Direct database access from clients is insecure and hard to control. The server validates requests, checks sessions, applies business rules and protects the database.

### 3. What is `RequestRouter`?

`RequestRouter` is the central dispatcher. It reads `Message.type` and calls the correct service.

### 4. What is `Message.payload`?

It is the body of the request or response. It may contain JSON text, Base64 serialized objects or simple text depending on the command.

### 5. What is the role of `errorCode`?

`errorCode` is a machine-readable error such as `DB_ERROR`, `NOT_ADMIN`, `INVALID_PRICE` or `CRYPTO_REQUIRED`. The UI translates it with `UiMessages`.

### 6. How does the project separate responsibilities?

The project separates:

- UI in `ui`
- server protocol in `server`
- shared protocol utilities in `common`
- SQL access in DAO classes
- business logic in `services`
- models in `models`, `product` and `ecommerce.personne5.model`


# ChriOnline - Server Package Documentation

This document explains the classes in:

```text
src/main/java/server
```

The `server` package is the infrastructure layer of ChriOnline. It starts the socket server, accepts clients, enforces RSA/AES encryption, routes requests to services, manages sessions, and helps clients discover the server on the local network.

## Global Role Of The Server Package

The server package is responsible for:

- starting the TCP socket server on port `6000`
- verifying MySQL and mail configuration at startup
- loading RSA/AES security configuration
- accepting client connections
- creating one `ClientHandler` per connected client
- enforcing mandatory RSA/AES socket encryption
- converting socket lines into `Message` objects
- routing requests to the correct service
- returning encrypted responses
- issuing and resolving session tokens
- announcing the server on the LAN
- printing useful network diagnostics

## Server Request Flow

The normal request flow is:

1. `ServerMain` starts the server.
2. A JavaFX client connects to port `6000`.
3. `ServerMain` creates a new `ClientHandler`.
4. `ClientHandler` performs the mandatory RSA/AES handshake.
5. Client sends encrypted AES-GCM socket lines.
6. `ClientHandler` decrypts each line.
7. The decrypted JSON becomes a `Message`.
8. `RequestRouter.route(...)` calls the correct service.
9. The service returns a `Message`.
10. `ClientHandler` encrypts the response and sends it back.

## `ServerMain`

### Role

`ServerMain` is the main entry point of the socket server.

It is the class you run when starting the backend:

```text
server.ServerMain
```

### Main Responsibilities

- Start the ChriOnline socket server.
- Load mail configuration.
- Verify MySQL connection.
- Print the JDBC URL used by the running server.
- Print the active database name.
- Print the `products` table structure.
- Load RSA/AES security configuration.
- Warm up the server RSA key pair.
- Start optional TLS if configured.
- Listen for clients on port `6000`.
- Start LAN discovery announcements.
- Create a `ClientHandler` thread for each connected client.

### Important Method: `main(String[] args)`

This is the startup method.

It performs the full server boot process:

1. Prints server startup information.
2. Reloads mail configuration with `MailService.reload()`.
3. Logs mail diagnostics.
4. Tests MySQL with `BaseDonnees.verifyConnection()`.
5. Prints the actual JDBC URL with `BaseDonnees.jdbcUrl()`.
6. Prints the database name with `BaseDonnees.currentDatabaseName()`.
7. Prints `DESCRIBE products` through `BaseDonnees.describeProductsTable()`.
8. Loads `ssl-config.properties` using `SslConfigLoader.load()`.
9. Reloads storage encryption with `StorageCryptoService.reloadConfig()`.
10. Calls `ApplicationSessionRsaKeys.warmup(...)`.
11. Logs that RSA/AES is mandatory.
12. Creates the server socket.
13. Accepts clients in a loop.
14. Starts a new thread with `new ClientHandler(socket)`.

### Important Method: `startLanDiscoveryAnnouncer(int serverPort)`

Starts a background announcer that broadcasts the server address on the LAN.

Purpose:

- Allows JavaFX clients to find the server automatically.
- Uses UDP multicast/broadcast.
- Announces the server host and port.

### Important Method: `sendSubnetDirectedBroadcasts(DatagramSocket socket, byte[] data)`

Sends discovery packets to subnet broadcast addresses.

Purpose:

- Improves discovery on networks where multicast is unreliable.
- Helps clients on the same LAN find the server.

### Libraries Used

- `java.net.ServerSocket`: listens for TCP clients.
- `java.net.Socket`: accepted client connection.
- `java.net.DatagramSocket`: UDP discovery announcements.
- `java.net.DatagramPacket`: UDP packet.
- `javax.net.ssl.SSLServerSocketFactory`: optional TLS server socket.
- `java.util.Properties`: security and TLS configuration.

## `ClientHandler`

### Role

`ClientHandler` handles one connected client.

Each client connection gets its own `ClientHandler` running in a separate thread.

### Main Responsibilities

- Read socket lines from one client.
- Enforce mandatory RSA/AES.
- Send the server RSA public key.
- Receive the RSA-wrapped AES session key.
- Unwrap the AES key with the server private key.
- Decrypt incoming AES-GCM messages.
- Route requests through `RequestRouter`.
- Encrypt responses with AES-GCM.
- Close the socket when the client disconnects.

### Important Method: `ClientHandler(Socket socket)`

Constructor.

It stores the accepted client socket.

### Important Method: `run()`

Thread entry point.

It:

1. Loads security config with `SslConfigLoader.load()`.
2. Creates UTF-8 input and output streams.
3. Logs the client address.
4. Calls `runNegotiatedProtocol(...)`.
5. Closes the socket in `finally`.

### Important Method: `runNegotiatedProtocol(Properties sec, BufferedReader input, PrintWriter output)`

This is the core protocol method.

It enforces the secure handshake:

1. Reads the first socket line.
2. Requires the first message to be `CLIENT_HELLO`.
3. Requires `crypto=true`.
4. Rejects plain clients with `CRYPTO_REQUIRED`.
5. Loads server RSA keys with `ApplicationSessionRsaKeys`.
6. Sends `SERVER_HELLO` with `publicKeySpkiB64`.
7. Reads `SECURE_KEY_EXCHANGE`.
8. Extracts the wrapped AES key.
9. Unwraps it with `RsaOaepAesKeyWrap.unwrapAesKey(...)`.
10. Sends `SECURE_SESSION_OK`.
11. Loops over encrypted request lines.
12. Decrypts with `AesGcmLineCipher.decryptLine(...)`.
13. Routes with `RequestRouter.route(...)`.
14. Encrypts the response with `AesGcmLineCipher.encryptLine(...)`.

### Important Method: `logRouterError(Message message, Message response)`

Logs service-level errors.

Example:

```text
Router ERROR type=ADMIN_PRODUCT_CREATE code=DB_ERROR
```

This helps debugging without exposing raw exceptions to the client.

### Libraries Used

- `BufferedReader`: reads socket lines.
- `PrintWriter`: sends socket lines.
- `StandardCharsets.UTF_8`: ensures UTF-8 protocol text.
- `SecretKey`: AES session key.
- `PrivateKey`: server RSA private key.
- `PublicKey`: server RSA public key.
- `Base64`: encodes/decodes public key and wrapped AES key.

### Security Classes Used

- `ApplicationSessionRsaKeys`
- `RsaOaepAesKeyWrap`
- `AesGcmLineCipher`

## `RequestRouter`

### Role

`RequestRouter` is the central dispatcher of the socket API.

It receives a `Message`, checks `message.getType()`, and calls the matching service method.

### Important Method: `route(Message message)`

This method contains the command switch.

Examples:

- `LOGIN` goes to `AuthService.login(...)`
- `REGISTER` goes to `AuthService.register(...)`
- `PRODUCT_LIST` goes to `ProductService.list(...)`
- `PRODUCT_CATEGORIES` goes to `ProductService.categories(...)`
- `CREATE_COMMANDE` goes to `CommandeService`
- `SIMULATE_PAYMENT` goes to `SocketPaymentService.simulatePayment(...)`
- `ADMIN_CHALLENGE_REQUEST` goes to `AdminAuthService.requestChallenge(...)`
- `ADMIN_CHALLENGE_VERIFY` goes to `AdminAuthService.verifyChallenge(...)`
- `ADMIN_PRODUCT_CREATE` goes to `AdminProductService.create(...)`
- `ADMIN_PRODUCT_LIST` goes to `AdminProductService.list(...)`
- `ADMIN_PRODUCT_UPDATE_STOCK` goes to `AdminProductService.updateStock(...)`
- `ADMIN_PRODUCT_DELETE` goes to `AdminProductService.delete(...)`

### Important Method: `error(String type, Message msg, String code)`

Builds a standard error response.

It keeps the same request ID and returns a machine-readable `errorCode`.

Example error codes:

- `AUTH_REQUIRED`
- `NOT_ADMIN`
- `INVALID_PAYLOAD`
- `DB_ERROR`
- `CRYPTO_REQUIRED`

### Important Method: `success(String type, Message msg, String payload)`

Builds a standard success response.

It returns:

- same request ID
- status `SUCCESS`
- response payload
- empty error code

### Important Method: `extractSessionToken(String payload)`

Extracts `sessionToken` from a JSON payload.

This is used by commands that require the user to be logged in.

### Why `RequestRouter` Is Shared

`RequestRouter` belongs to the server infrastructure, but it touches every project responsibility:

- Personne 1: auth, profile, account security
- Personne 2: catalogue and products
- Personne 3: cart request conversion
- Personne 4: commands/orders
- Personne 5: payments
- RSA Admin: admin challenge verification
- RSA/AES: secure socket commands pass through it after decryption

## `SessionRegistry`

### Role

`SessionRegistry` manages logged-in sessions in memory.

It maps:

```text
sessionToken -> userId
```

### Important Field: `TOKEN_TO_USER`

```java
ConcurrentHashMap<String, Integer>
```

Thread-safe map used by many client handler threads.

### Important Method: `issueToken(int userId)`

Creates a new session token for a user.

Usually called after:

- normal login
- registration
- RSA admin login success

### Important Method: `resolveUser(String token)`

Returns the user ID linked to a session token.

If the token is invalid or missing, it returns `null`.

Services use this to check authentication.

### Important Method: `revoke(String token)`

Removes a token from the registry.

Used during logout or account deletion.

### Libraries Used

- `ConcurrentHashMap`: thread-safe session storage.
- `UUID`: token generation.

## `LanDiscoveryProtocol`

### Role

`LanDiscoveryProtocol` defines how the server announces itself on the local network.

It is shared by:

- server LAN announcer
- JavaFX LAN discovery client

### Important Method: `encode(String host, int port)`

Creates a byte message containing server host and port.

The result is sent through UDP multicast/broadcast.

### Important Method: `parse(byte[] data, int len)`

Reads a discovery packet and returns an `InetSocketAddress` if the packet is valid.

### Why It Exists

Without this, the user would need to manually type the server IP every time.

With discovery, the client can find the server automatically on the same LAN.

### Libraries Used

- `InetSocketAddress`: host + port result.
- `Optional`: parse may fail.
- `StandardCharsets.UTF_8`: discovery text encoding.

## `NetworkInfo`

### Role

`NetworkInfo` helps the server print useful network addresses.

It prevents confusing addresses such as virtual adapters, loopback-only addresses, or non-routable addresses from being presented as good client targets.

### Important Method: `localIPv4Addresses()`

Returns local IPv4 addresses that are likely useful for clients.

Example:

```text
192.168.1.100
```

### Important Method: `isLikelyReachableFromOtherMachines(String ipv4)`

Checks whether an IPv4 address looks reachable from another device on the same LAN.

### Important Method: `isNotRoutableFromOtherNetworks(String host)`

Detects private/local addresses that will not work over the public Internet.

Examples:

- `192.168.x.x`
- `10.x.x.x`
- `127.0.0.1`

### Internal Method: `isVirtualOrHostOnlyInterface(NetworkInterface ni)`

Filters virtual adapters such as:

- VirtualBox
- VMware
- Hyper-V
- loopback
- host-only adapters

### Internal Method: `isVirtualOnlyOrNonRoutableSubnet(Inet4Address a)`

Filters addresses that are technically IPv4 but not useful for real clients.

### Libraries Used

- `NetworkInterface`: list machine network adapters.
- `Inet4Address`: IPv4 addresses.
- `Enumeration`: Java network interface iteration.
- `List`: returned address list.

## `PublicIpHint`

### Role

`PublicIpHint` tries to detect the public IPv4 address used by the server machine.

This is only a hint for external/WAN connection.

### Important Method: `fetchPublicIpv4()`

Attempts to contact an external service and read the public IPv4.

Returns:

- `Optional<String>` with public IP if successful.
- empty optional if unavailable.

### Why It Is Only A Hint

Even if a public IP is detected, remote clients may still fail because of:

- Windows firewall
- router firewall
- missing port forwarding
- CGNAT
- ISP restrictions

### Libraries Used

- `HttpURLConnection`: simple HTTP request.
- `Pattern`: validates IPv4 format.
- `Optional`: result may be absent.

## Relationship With RSA/AES Security

The server package now treats RSA/AES as mandatory.

Key classes:

- `ServerMain`: loads and warms up RSA key material.
- `ClientHandler`: enforces the secure handshake.
- `RequestRouter`: only receives messages after they are decrypted.

Plain socket traffic is rejected.

If an old client sends unencrypted JSON, the server responds with:

```text
CRYPTO_REQUIRED
```

## Relationship With Services

The server package does not implement most business logic directly.

Instead:

- `RequestRouter` receives a `Message`.
- It calls the right service.
- Services handle validation and database access.

Examples:

- `AuthService` handles login/register.
- `ProductService` handles catalogue.
- `AdminProductService` handles admin product management.
- `CommandeService` handles orders.
- `SocketPaymentService` handles payment.
- `AdminAuthService` handles RSA admin login.

## Ownership / Responsibility

The server package is shared infrastructure.

Suggested responsibility split:

- `ServerMain`: shared server startup, RSA/AES part by Louay Hammouchi.
- `ClientHandler`: mainly Louay Hammouchi because it enforces RSA/AES.
- `RequestRouter`: shared, because every project feature adds routes here.
- `SessionRegistry`: Personne 1 because it belongs to login/session auth.
- `LanDiscoveryProtocol`: shared networking utility.
- `NetworkInfo`: shared networking utility.
- `PublicIpHint`: shared deployment/networking utility.

## Interview Questions

### What is the most important server class?

`ServerMain`, because it starts the backend and accepts clients.

### What class handles one connected client?

`ClientHandler`.

### Where is RSA/AES enforced?

In `ClientHandler.runNegotiatedProtocol(...)`.

### Where are socket commands routed?

In `RequestRouter.route(...)`.

### Where are session tokens stored?

In `SessionRegistry`.

### Why is `ConcurrentHashMap` used in `SessionRegistry`?

Because many client threads may read or write session tokens at the same time.

### Why does the server print network addresses?

So JavaFX clients know which host/IP to connect to.

### Why should clients not connect directly to MySQL?

Because the server must validate requests, enforce permissions, protect credentials, and centralize database access.


# Tutorial - Mandatory RSA/AES Socket Encryption

This tutorial explains the ChriOnline socket security protocol.

RSA/AES is now mandatory. There is no JavaFX checkbox and no plain socket mode for normal app traffic. Every client connection must perform the secure handshake before sending commands like `LOGIN`, `PRODUCT_LIST`, `CREATE_COMMANDE`, `SIMULATE_PAYMENT` or admin commands.

## 1. Simple Idea

ChriOnline still uses socket messages, but JSON commands are no longer sent directly on the wire.

The command is still logically a normal `Message`:

```json
{"type":"LOGIN","requestId":"1","status":"","payload":"...","errorCode":""}
```

Before it crosses the socket, it is encrypted with AES-GCM. The server decrypts it, routes it normally, then encrypts the response before sending it back.

## 2. Why RSA And AES Are Used Together

RSA is asymmetric:

- The public key can be shared with the client.
- The private key stays on the server.
- It is good for protecting a small secret.

AES is symmetric:

- The same key encrypts and decrypts.
- It is fast.
- It is better for encrypting many messages.

So ChriOnline uses RSA-OAEP once to protect the AES session key, then AES-GCM for all socket lines after that.

## 3. Mandatory Handshake Flow

Every connection follows this flow:

1. Client opens the socket.
2. Client sends `CLIENT_HELLO` with `crypto=true`.
3. Server rejects the connection if the client does not request crypto.
4. Server sends `SERVER_HELLO` with its RSA public key.
5. Client generates a random AES-256 session key.
6. Client wraps the AES key with the server RSA public key using RSA-OAEP.
7. Client sends `SECURE_KEY_EXCHANGE`.
8. Server unwraps the AES key with its RSA private key.
9. Server sends `SECURE_SESSION_OK`.
10. Every next request and response is encrypted with AES-GCM.

Protected commands include `LOGIN`, `REGISTER`, `PRODUCT_LIST`, `CREATE_COMMANDE`, `GET_COMMANDES`, `SIMULATE_PAYMENT`, `ADMIN_CHALLENGE_REQUEST`, `ADMIN_CHALLENGE_VERIFY`, `ADMIN_PRODUCT_CREATE`, `ADMIN_PRODUCT_LIST`, `ADMIN_PRODUCT_UPDATE_STOCK` and `ADMIN_PRODUCT_DELETE`.

## 4. What Happens To Plain Clients

Plain mode has been removed from the normal protocol.

If a client sends normal JSON without `CLIENT_HELLO`, or sends `CLIENT_HELLO` with `crypto=false`, the server returns:

```text
CRYPTO_REQUIRED
```

This is intentional. RSA/AES is the standard security measure now.

## 5. How To Know It Is Working

Server logs should show:

```text
[AppCrypto] RSA/AES is mandatory: supported=true, required=true.
[AppCrypto] Every socket session uses RSA-OAEP key exchange + AES-GCM lines.
Socket handshake: clientRequestedCrypto=true, serverSupported=true, serverRequired=true
Secure handshake: SECURE_KEY_EXCHANGE (...)
Socket handshake success: RSA/AES session established.
Request received (AES-GCM wire), len=...
```

The key proof is:

```text
Request received (AES-GCM wire)
```

That means the server received encrypted socket data, decrypted it, and routed the original JSON internally.

## 6. Configuration Files

Default project config:

```text
src/main/resources/ssl-config.properties
```

User override config:

```text
C:\Users\Legion\.chrionline\ssl-config.properties
```

The user override still controls the RSA keystore path and password. It no longer needs `application.crypto.enabled` or `application.crypto.required`.

Important properties:

```properties
server.crypto.session.rsa.keystore.type=PKCS12
server.crypto.session.rsa.keystore.path=C:/Users/Legion/.chrionline/chrionline-session.p12
server.crypto.session.rsa.keystore.password=...
server.crypto.session.rsa.key.alias=chrionline-session
```

Meaning:

- `server.crypto.session.rsa.keystore.type=PKCS12`: the keystore format.
- `server.crypto.session.rsa.keystore.path`: file containing the server RSA private key and certificate.
- `server.crypto.session.rsa.keystore.password`: password protecting the keystore.
- `server.crypto.session.rsa.key.alias`: key name inside the keystore.

If no session keystore is configured, the current code can generate an ephemeral RSA key pair for the server run. That keeps encryption functional for development, but a PKCS12 keystore is better for a stable setup.

## 7. Class Documentation

### `ui.SocketApiClient`

Client-side socket API used by JavaFX.

Purpose:

- Opens the TCP socket.
- Performs the mandatory RSA/AES handshake.
- Generates the AES session key.
- Wraps the AES key with the server RSA public key.
- Encrypts every outgoing `Message`.
- Decrypts every incoming response.

Important functions:

- `SocketApiClient(String host, int port)`: stores the server host and port.
- `setApplicationCryptoEnabled(boolean enabled)`: kept only for older call sites; passing `false` cannot disable RSA/AES.
- `isApplicationCryptoEnabled()`: always returns `true`.
- `isApplicationCryptoSessionActive()`: returns `true` only when the AES session key has been negotiated.
- `closeQuietly()`: closes the socket and clears the AES session key.
- `ensureConnectedLocked()`: opens the socket and always calls `performHandshakeLocked()`.
- `performHandshakeLocked()`: sends `CLIENT_HELLO`, receives the RSA public key, generates AES-256, wraps it with RSA-OAEP and waits for `SECURE_SESSION_OK`.
- `send(Message request)`: encrypts the JSON request, sends it, decrypts the response and returns a `Message`.

Important libraries:

- `java.net.Socket`: TCP connection.
- `javax.net.SocketFactory`: creates sockets.
- `javax.net.ssl.SSLSocket`: optional TLS socket if TLS is enabled.
- `javax.crypto.KeyGenerator`: creates AES keys.
- `javax.crypto.SecretKey`: represents the AES key.
- `java.security.KeyFactory`: rebuilds the RSA public key from bytes.
- `java.security.PublicKey`: server RSA public key.
- `java.security.spec.X509EncodedKeySpec`: public key encoding format.
- `java.util.Base64`: transports binary key material as text.

### `server.ClientHandler`

Server-side handler for one client socket.

Purpose:

- Reads the first socket line.
- Requires `CLIENT_HELLO` with `crypto=true`.
- Sends the RSA public key.
- Receives the RSA-wrapped AES key.
- Unwraps the AES key with the server private key.
- Decrypts all following requests.
- Encrypts all responses.

Important functions:

- `run()`: loads security config, creates UTF-8 socket reader/writer and calls the negotiated protocol.
- `runNegotiatedProtocol(...)`: enforces RSA/AES, rejects plain clients with `CRYPTO_REQUIRED`, sends `SERVER_HELLO`, reads `SECURE_KEY_EXCHANGE`, unwraps AES and enters the encrypted request loop.
- `logRouterError(...)`: logs command errors returned by `RequestRouter`.

Important libraries:

- `java.io.BufferedReader`: reads socket lines.
- `java.io.PrintWriter`: writes socket lines.
- `javax.crypto.SecretKey`: AES session key.
- `java.security.PrivateKey`: server RSA private key.
- `java.security.PublicKey`: server RSA public key.
- `java.util.Base64`: decodes wrapped AES key and encodes public key.

### `server.ServerMain`

Server entry point.

Purpose:

- Starts the socket server.
- Loads MySQL, mail and security configuration.
- Warms up the RSA session key pair.
- Logs that RSA/AES is mandatory.

Important function:

- `main(String[] args)`: loads config, calls `ApplicationSessionRsaKeys.warmup(...)` and starts listening on port `6000`.

### `common.crypto.ApplicationSessionRsaKeys`

Loads the RSA key pair used by the socket handshake.

Important functions:

- `warmup(Properties securityProps)`: loads the key early at server startup.
- `ensureLoaded(Properties securityProps)`: loads from `server.crypto.session.rsa.keystore.*`, falls back to `server.ssl.key-store.*`, or generates an ephemeral development key pair.
- `publicKey(Properties securityProps)`: returns the public key sent to the client.
- `privateKey(Properties securityProps)`: returns the private key used to unwrap the AES key.

Important libraries:

- `java.security.KeyStore`: reads PKCS12.
- `java.security.KeyPair`: public/private key pair.
- `java.security.PrivateKey`: RSA private key.
- `java.security.PublicKey`: RSA public key.
- `java.security.cert.Certificate`: certificate carrying the public key.
- `java.security.KeyPairGenerator`: creates an ephemeral fallback key.

### `common.crypto.RsaOaepAesKeyWrap`

Wraps and unwraps the AES session key.

Important functions:

- `wrapAesKey(PublicKey publicKey, SecretKey aesKey)`: encrypts the AES key bytes with RSA-OAEP.
- `unwrapAesKey(PrivateKey privateKey, byte[] wrapped)`: decrypts the AES key bytes and rebuilds a `SecretKey`.

Important libraries:

- `javax.crypto.Cipher`: encryption/decryption engine.
- `javax.crypto.SecretKey`: AES key interface.
- `javax.crypto.spec.SecretKeySpec`: rebuilds AES key from raw bytes.
- `java.security.PublicKey` / `PrivateKey`: RSA keys.

### `common.crypto.AesGcmLineCipher`

Encrypts and decrypts one socket line at a time.

Important functions:

- `encryptLine(String plaintext, SecretKey aesKey)`: generates a random IV, encrypts with AES-GCM and returns Base64 text.
- `decryptLine(String base64Line, SecretKey aesKey)`: decodes Base64, verifies the GCM authentication tag and returns plaintext JSON.

Important libraries:

- `javax.crypto.Cipher`: AES-GCM engine.
- `javax.crypto.SecretKey`: AES key.
- `javax.crypto.spec.GCMParameterSpec`: GCM tag size and IV.
- `java.security.SecureRandom`: random IV.
- `java.util.Base64`: binary-to-text encoding.

### `common.ssl.SslConfigLoader`

Loads security properties.

Important functions:

- `userConfigFilePath()`: returns the user config path.
- `load()`: merges classpath config, user config and environment variables.

Important note:

- RSA/AES is mandatory in code.
- The config is still used for RSA keystore settings and optional TLS.

### `common.ssl.SslContextUtil`

Optional TLS helper. This is separate from mandatory RSA/AES application encryption.

Important functions:

- `isServerTlsEnabled(Properties p)`
- `isClientTlsEnabled(Properties p)`
- `buildServerContext(Properties p)`
- `buildClientContext(Properties p)`

## 8. PKCS12 Keystore

A PKCS12 keystore is a password-protected file that stores cryptographic keys and certificates.

In this project it stores the server RSA key pair:

- Private key: kept by the server.
- Public certificate/key: sent to clients during handshake.
- Alias: name of the key entry, usually `chrionline-session`.
- Password: protects the file.

Example path:

```text
C:\Users\Legion\.chrionline\chrionline-session.p12
```

## 9. Troubleshooting

### Server says `CRYPTO_REQUIRED`

Cause:

- A client sent plain JSON.
- A client sent `CLIENT_HELLO` with `crypto=false`.
- An old client build is still running.

Fix:

- Rebuild and restart the JavaFX client.
- Make sure `SocketApiClient` is updated and always performs `performHandshakeLocked()`.

### Keystore missing

Server log may show:

```text
[AppCrypto] No RSA session keystore configured; generated an ephemeral RSA keypair
```

Meaning:

- Encryption still works for this run.
- The key changes after server restart.
- Configure PKCS12 for a stable production-like setup.

### AES-GCM decrypt error

Common causes:

- Client and server are not using the same AES session key.
- A stale old client is connected.
- The encrypted line was corrupted.

Fix:

- Restart both server and client.
- Confirm the log shows `Socket handshake success`.

## 10. Test Checklist

1. Start MySQL/XAMPP.
2. Run `server.ServerMain`.
3. Confirm logs show RSA/AES mandatory.
4. Run JavaFX client.
5. Connect to `127.0.0.1:6000`.
6. Confirm the UI shows `Sécurité socket : RSA→AES obligatoire`.
7. Login.
8. Load product list.
9. Create an order.
10. Pay an order.
11. Use admin product commands.
12. Confirm server logs show `Request received (AES-GCM wire)`.

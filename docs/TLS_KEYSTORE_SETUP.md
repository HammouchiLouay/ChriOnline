# TLS socket setup (Keystore/Truststore) for ChriOnline

This project uses a custom TCP socket protocol (JSON lines). TLS secures the transport without changing the protocol.

## 1) Generate a server keystore (PKCS12)

Run on the machine that runs `server.ServerMain`.

```bash
keytool -genkeypair ^
  -alias chrionline ^
  -keyalg RSA ^
  -keysize 2048 ^
  -keystore server-keystore.p12 ^
  -storetype PKCS12 ^
  -validity 365
```

## 2) Export the server certificate

```bash
keytool -exportcert ^
  -alias chrionline ^
  -keystore server-keystore.p12 ^
  -file server-cert.cer
```

## 3) Create a client truststore and import the server cert

Run on each client machine (or distribute the truststore).

```bash
keytool -importcert ^
  -alias chrionline-server ^
  -file server-cert.cer ^
  -keystore truststore.p12 ^
  -storetype PKCS12
```

## 4) Configure the app

### Server

Create `~/.chrionline/ssl-config.properties` (Windows: `%USERPROFILE%\.chrionline\ssl-config.properties`):

```properties
server.ssl.enabled=true
server.ssl.key-store.type=PKCS12
server.ssl.key-store.path=C:/path/to/server-keystore.p12
server.ssl.key-store.password=changeit
```

### Client

In `~/.chrionline/ssl-config.properties` on the client machine:

```properties
client.ssl.enabled=true
client.ssl.trust-store.type=PKCS12
client.ssl.trust-store.path=C:/path/to/truststore.p12
client.ssl.trust-store.password=changeit
```

## Notes

- Server and client must either both use TLS or both use plain TCP.
- For self-signed certs, clients must trust the exported server certificate (truststore).


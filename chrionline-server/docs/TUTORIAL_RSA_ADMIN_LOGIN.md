# Tutorial - RSA Admin Login

This tutorial explains the RSA admin login feature in ChriOnline.

It is different from mandatory RSA/AES socket encryption.

- RSA/AES socket encryption is always active and protects messages while they travel between client and server.
- RSA admin login proves that the admin owns a private RSA key.

## 1. Simple Idea

Normal login uses email/password.

RSA admin login uses challenge-response:

1. Server creates a random challenge.
2. Client signs the challenge with the admin private key.
3. Server verifies the signature using the admin public key stored in MySQL.
4. If the signature is valid, the server creates a normal session token.

The private key never goes to the server.

## 2. Why This Is Secure

The server stores only the public key.

The client keeps the private key locally.

When the server sends a challenge, only the real private key can produce a valid signature. The server can verify the signature with the public key, but it cannot create signatures itself.

The challenge is random and short-lived, so an old signature cannot be reused later.

## 3. Full Login Flow

### Step 1 - Admin requests challenge

Client sends:

```json
{
  "type": "ADMIN_CHALLENGE_REQUEST",
  "payload": "{\"email\":\"admin@example.com\"}"
}
```

Server checks:

- User exists.
- User role is `ADMIN`.
- User has `admin_public_key_pem` in MySQL.

Server replies:

```json
{
  "status": "SUCCESS",
  "payload": "{\"challengeId\":\"...\",\"challenge\":\"...\",\"userId\":1}"
}
```

### Step 2 - Client signs challenge

The JavaFX client reads the selected private key PEM file.

Then it signs the challenge with:

```text
SHA256withRSA
```

The signature is sent as Base64.

### Step 3 - Admin verifies challenge

Client sends:

```json
{
  "type": "ADMIN_CHALLENGE_VERIFY",
  "payload": "{\"challengeId\":\"...\",\"signatureB64\":\"...\"}"
}
```

Server:

- Finds the pending challenge.
- Checks that it is not expired.
- Loads admin public key from MySQL.
- Verifies the signature.
- Issues a normal session token.

Server success payload is similar to normal `LOGIN`:

```json
{
  "userId": 1,
  "username": "admin",
  "email": "admin@example.com",
  "phone": 123456789,
  "emailVerified": true,
  "role": "ADMIN",
  "sessionToken": "..."
}
```

## 4. Key Files

Admin key files are generated here by default:

```text
C:\Users\Legion\.chrionline\admin-rsa-keys
```

Generated files:

```text
admin_rsa_private.pem
admin_rsa_public.pem
```

Use:

- Keep `admin_rsa_private.pem` secret.
- Paste `admin_rsa_public.pem` into MySQL column `user.admin_public_key_pem`.

## 5. Database Requirement

The admin user row needs:

```sql
role = 'ADMIN'
admin_public_key_pem = '-----BEGIN PUBLIC KEY----- ...'
```

Example:

```sql
UPDATE `user`
SET admin_public_key_pem = 'PASTE_PUBLIC_KEY_PEM_HERE'
WHERE id_user = 1 AND role = 'ADMIN';
```

The private key is never stored in MySQL.

## 6. Classes Used

### `services.AdminAuthService`

Server-side service for RSA admin login.

Purpose:

- Creates challenges.
- Stores pending challenges temporarily.
- Verifies RSA signatures.
- Issues session tokens for valid admins.

Important functions:

- `requestChallenge(Message request)`
  - Handles `ADMIN_CHALLENGE_REQUEST`.
  - Reads admin email from payload.
  - Checks that the user exists and has role `ADMIN`.
  - Checks that the admin public key exists.
  - Generates a random challenge.
  - Stores it in memory with a 30-second TTL.
  - Returns `challengeId`, `challenge`, and `userId`.

- `verifyChallenge(Message request)`
  - Handles `ADMIN_CHALLENGE_VERIFY`.
  - Reads `challengeId` and `signatureB64`.
  - Removes the pending challenge so it cannot be reused.
  - Checks expiration.
  - Loads the admin public key from MySQL.
  - Verifies the signature.
  - Issues a session token through `SessionRegistry`.

- `cleanupExpired()`
  - Removes expired challenges from memory.

Important libraries:

- `java.util.concurrent.ConcurrentHashMap`
  - Stores pending challenges safely between socket handler threads.

- `java.util.UUID`
  - Creates unique challenge IDs.

- `java.util.Base64`
  - Decodes client signature text into bytes.

- `java.security.PublicKey`
  - Represents the admin public key loaded from MySQL.

- `java.sql.Connection`
  - Reads the admin user row from MySQL.

### `common.crypto.ChallengeGenerator`

Creates random one-time challenges.

Purpose:

- Generates unpredictable challenge strings.
- Prevents replay attacks.

Important function:

- `generateChallenge()`
  - Creates 32 random bytes.
  - Encodes them as URL-safe Base64 without padding.

Important libraries:

- `java.security.SecureRandom`
  - Cryptographically secure random number generator.

- `java.util.Base64`
  - Converts random bytes into text.

### `common.crypto.RsaSignatureUtil`

Signs and verifies admin challenges.

Purpose:

- Client signs the challenge.
- Server verifies the signature.

Important functions:

- `signChallenge(String challenge, PrivateKey privateKey)`
  - Used by the JavaFX client.
  - Signs the challenge using the selected private key.
  - Returns raw signature bytes.

- `verifyChallenge(String challenge, byte[] signatureBytes, PublicKey publicKey)`
  - Used by the server.
  - Returns true if the signature is valid.

Important libraries:

- `java.security.Signature`
  - Java API for digital signatures.

- `java.security.PrivateKey`
  - Admin private key used for signing.

- `java.security.PublicKey`
  - Admin public key used for verification.

- `java.nio.charset.StandardCharsets`
  - Converts the challenge string to UTF-8 bytes consistently.

Algorithm used:

```text
SHA256withRSA
```

### `common.crypto.PemKeyUtil`

Reads RSA keys from PEM text.

Purpose:

- Converts PEM files into Java key objects.
- Supports private keys for the client and public keys for the server.

Important functions:

- `parsePkcs8PrivateKeyPem(String pem)`
  - Reads `-----BEGIN PRIVATE KEY-----`.
  - Returns a `PrivateKey`.

- `parseX509PublicKeyPem(String pem)`
  - Reads `-----BEGIN PUBLIC KEY-----`.
  - Returns a `PublicKey`.

- `stripPem(String pem)`
  - Internal helper.
  - Removes PEM headers, footers, and whitespace.

Important libraries:

- `java.security.KeyFactory`
  - Builds RSA key objects from encoded bytes.

- `java.security.spec.PKCS8EncodedKeySpec`
  - Format used for private keys.

- `java.security.spec.X509EncodedKeySpec`
  - Format used for public keys.

- `java.util.Base64`
  - Decodes PEM body text into DER bytes.

### `common.crypto.RsaKeyPairGenerator`

Generates RSA key pairs and exports them as PEM text.

Purpose:

- Creates an admin RSA key pair.
- Formats keys for file storage and database provisioning.

Important functions:

- `generateKeyPair()`
  - Generates a 2048-bit RSA key pair.

- `toPublicKeyPem(KeyPair kp)`
  - Converts public key to PEM text.

- `toPrivateKeyPem(KeyPair kp)`
  - Converts private key to PEM text.

Important libraries:

- `java.security.KeyPair`
  - Holds public/private key pair.

- `java.security.KeyPairGenerator`
  - Generates RSA keys.

- `java.util.Base64`
  - Encodes DER key bytes as PEM text.

### `common.crypto.GenerateAdminRsaKeys`

Small command-line utility to create admin key files.

Purpose:

- Writes `admin_rsa_private.pem`.
- Writes `admin_rsa_public.pem`.
- Prints the next SQL provisioning instruction.

Important function:

- `main(String[] args)`
  - Creates output directory.
  - Generates RSA pair.
  - Writes the two PEM files.

Important libraries:

- `java.nio.file.Files`
  - Creates directories and writes files.

- `java.nio.file.Path`
  - Represents output paths.

- `java.nio.charset.StandardCharsets`
  - Writes PEM text as UTF-8.

### `ui.ChriOnlineClientApp`

JavaFX application class.

Purpose in RSA admin login:

- Opens the admin RSA login dialog.
- Lets the user select the private key file.
- Requests the server challenge.
- Signs the challenge.
- Sends verification request.
- Applies the returned admin session.

Important function:

- `openAdminRsaLoginDialog()`
  - Builds the RSA login UI.
  - Reads the selected private key file.
  - Calls `ADMIN_CHALLENGE_REQUEST`.
  - Uses `PemKeyUtil.parsePkcs8PrivateKeyPem(...)`.
  - Uses `RsaSignatureUtil.signChallenge(...)`.
  - Calls `ADMIN_CHALLENGE_VERIFY`.
  - Calls the normal login success handler when verification succeeds.

Important libraries:

- `javafx.stage.FileChooser`
  - Lets the user select the private key PEM file.

- `javafx.scene.control.Dialog`
  - Displays the RSA login form.

- `java.nio.file.Files`
  - Reads the private key file.

- `java.util.Base64`
  - Encodes signature bytes for JSON payload.

### `server.RequestRouter`

Routes socket requests to services.

Purpose in RSA admin login:

- Receives message type.
- Calls the correct service method.

Important cases:

```java
case "ADMIN_CHALLENGE_REQUEST":
    return AdminAuthService.requestChallenge(message);

case "ADMIN_CHALLENGE_VERIFY":
    return AdminAuthService.verifyChallenge(message);
```

### `server.SessionRegistry`

Creates and validates session tokens.

Purpose in RSA admin login:

- Issues a normal session token after successful RSA verification.
- Lets later admin requests prove they are authenticated.

Important function:

- `issueToken(int userId)`
  - Creates a token connected to the authenticated admin user.

## 7. Main Libraries Summary

### `java.security`

Used for RSA keys and signatures.

Important classes:

- `PrivateKey`
- `PublicKey`
- `Signature`
- `KeyPair`
- `KeyPairGenerator`
- `KeyFactory`

### `java.security.spec`

Used to rebuild RSA keys from encoded bytes.

Important classes:

- `PKCS8EncodedKeySpec`
- `X509EncodedKeySpec`

### `java.security.SecureRandom`

Used to generate secure random challenges.

### `java.util.Base64`

Used to encode and decode binary data as text for JSON and PEM.

### `java.sql`

Used by the server to load admin user data and public key from MySQL.

### JavaFX

Used by the client to show the RSA login dialog and file picker.

Important classes:

- `Dialog`
- `FileChooser`
- `TextField`
- `PasswordField`
- `Button`

## 8. Setup Steps

### Step 1 - Generate keys

Run:

```powershell
cd C:\Users\Legion\Downloads\ChriOnline\chrionline-server
.\mvnw.cmd exec:java -Dexec.mainClass=common.crypto.GenerateAdminRsaKeys
```

Output:

```text
C:\Users\Legion\.chrionline\admin-rsa-keys\admin_rsa_private.pem
C:\Users\Legion\.chrionline\admin-rsa-keys\admin_rsa_public.pem
```

### Step 2 - Store public key in MySQL

Copy the full content of:

```text
admin_rsa_public.pem
```

Paste it into:

```sql
user.admin_public_key_pem
```

for an admin user.

### Step 3 - Keep private key local

The private key stays on the client machine:

```text
admin_rsa_private.pem
```

Do not paste it into MySQL.

### Step 4 - Login from JavaFX

1. Start `ServerMain`.
2. Open JavaFX client.
3. Open admin RSA login dialog.
4. Enter admin email.
5. Select `admin_rsa_private.pem`.
6. Submit.

## 9. Common Errors

### `ADMIN_KEY_MISSING`

The admin user does not have a public key in MySQL.

Fix:

```sql
UPDATE `user`
SET admin_public_key_pem = '...'
WHERE id_user = 1 AND role = 'ADMIN';
```

### `CHALLENGE_EXPIRED`

The challenge was older than 30 seconds.

Fix:

Request a new challenge and sign again.

### `BAD_SIGNATURE`

The selected private key does not match the public key stored in MySQL.

Fix:

Use the matching `admin_rsa_private.pem`, or update MySQL with the matching public key.

### `FORBIDDEN`

The user exists but is not an admin.

Fix:

```sql
UPDATE `user`
SET role = 'ADMIN'
WHERE id_user = 1;
```

## 10. Difference From RSA/AES Socket Encryption

RSA admin login:

- Proves admin identity.
- Uses `SHA256withRSA`.
- Does not encrypt all messages.
- Uses admin PEM key pair.

RSA/AES socket encryption:

- Is mandatory for every socket connection.
- Encrypts socket traffic.
- Uses RSA-OAEP to exchange an AES key.
- Uses AES-GCM for messages.
- Uses server PKCS12 RSA key pair.

They are both RSA features, but they solve different problems.

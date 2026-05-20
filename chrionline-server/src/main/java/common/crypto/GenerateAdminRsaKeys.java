package common.crypto;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;

/**
 * Writes an RSA admin key pair to PEM files for provisioning and JavaFX RSA login.
 *
 * <p>Eclipse: open this class → Run As → Java Application.
 *
 * <p>Default output directory: {@code ~/.chrionline/admin-rsa-keys} (Windows: {@code %USERPROFILE%\.chrionline\admin-rsa-keys}).
 * Optional first program argument: a different output directory.
 *
 * <p>Files: {@code admin_rsa_private.pem}, {@code admin_rsa_public.pem}. Import the public PEM into MySQL
 * {@code user.admin_public_key_pem} for an ADMIN user.
 */
public final class GenerateAdminRsaKeys {

    public static void main(String[] args) throws Exception {
        Path dir =
                args.length > 0 && args[0] != null && !args[0].isBlank()
                        ? Path.of(args[0].trim()).toAbsolutePath().normalize()
                        : Path.of(System.getProperty("user.home"), ".chrionline", "admin-rsa-keys");
        Files.createDirectories(dir);

        KeyPair kp = RsaKeyPairGenerator.generateKeyPair();
        Path priv = dir.resolve("admin_rsa_private.pem");
        Path pub = dir.resolve("admin_rsa_public.pem");
        Files.writeString(priv, RsaKeyPairGenerator.toPrivateKeyPem(kp), StandardCharsets.UTF_8);
        Files.writeString(pub, RsaKeyPairGenerator.toPublicKeyPem(kp), StandardCharsets.UTF_8);

        System.out.println("Wrote private: " + priv);
        System.out.println("Wrote public:  " + pub);
        System.out.println();
        System.out.println("Next: UPDATE `user` SET admin_public_key_pem = <paste admin_rsa_public.pem> WHERE id_user = <admin> AND role = 'ADMIN';");
        System.out.println("Keep admin_rsa_private.pem secret; select it in the app RSA admin login dialog.");
    }

    private GenerateAdminRsaKeys() {}
}

package ecommerce.personne5.utils;

import java.util.UUID;

/** Jeton opaque pour annoter une transaction simulée. */
public class TokenUtil {
    private TokenUtil() {
    }

    public static String genererToken() {
        return UUID.randomUUID().toString();
    }
}
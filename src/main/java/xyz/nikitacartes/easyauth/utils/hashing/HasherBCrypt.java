package xyz.nikitacartes.easyauth.utils.hashing;

import at.favre.lib.crypto.bcrypt.BCrypt;

import java.util.concurrent.ConcurrentHashMap;

import static xyz.nikitacartes.easyauth.utils.EasyLogger.LogError;

public class HasherBCrypt {
    // Verification cache: stores verified hashes with expiry timestamp
    // TTL: 5 minutes (300000ms) to avoid re-verification in batch operations
    private static final long CACHE_TTL_MS = 300_000L;
    private static final ConcurrentHashMap<String, Long> VERIFICATION_CACHE = new ConcurrentHashMap<>();

    /**
     * Cleans up expired cache entries.
     */
    public static void cleanupCache() {
        long now = System.currentTimeMillis();
        VERIFICATION_CACHE.entrySet().removeIf(entry -> entry.getValue() < now);
    }

    /**
     * Verifies password
     *
     * @param password character array of password string
     * @param hashed   hashed password
     * @return true if password was correct
     */
    public static boolean verify(char[] password, String hashed) {
        long now = System.currentTimeMillis();

        // Check cache first
        Long expiry = VERIFICATION_CACHE.get(hashed);
        if (expiry != null && expiry > now) {
            return true;
        }

        try {
            boolean result = BCrypt.verifyer().verify(password, hashed).verified;
            if (result) {
                // Cache successful verification with TTL
                VERIFICATION_CACHE.put(hashed, now + CACHE_TTL_MS);
            }
            return result;
        } catch (Error e) {
            LogError("password verification error", e);
        }
        return false;
    }

    /**
     * Hashes the password
     *
     * @param password character array of password string that needs to be hashed
     * @return string
     */
    public static String hash(char[] password) {
        try {
            return BCrypt.withDefaults().hashToString(12, password);
        } catch (Error e) {
            LogError("password hashing error", e);
        }
        return null;
    }
}

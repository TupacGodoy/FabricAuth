package xyz.nikitacartes.easyauth.integrations;

import javax.net.ssl.HttpsURLConnection;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static xyz.nikitacartes.easyauth.EasyAuth.extendedConfig;
import static xyz.nikitacartes.easyauth.utils.EasyLogger.LogDebug;

public class MojangApi {
    // Cache for premium check results with 24h TTL (in milliseconds)
    private static final Map<String, CachedPremiumResult> PREMIUM_CACHE = new ConcurrentHashMap<>();
    private static final long PREMIUM_CACHE_TTL_MS = 24 * 60 * 60 * 1000L; // 24 hours

    // Permanent cache for UUIDs (they never change)
    private static final Map<String, UUID> UUID_CACHE = new ConcurrentHashMap<>();

    private static class CachedPremiumResult {
        final boolean isPremium;
        final long expiryTime;

        CachedPremiumResult(boolean isPremium, long ttlMs) {
            this.isPremium = isPremium;
            this.expiryTime = System.currentTimeMillis() + ttlMs;
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expiryTime;
        }
    }

    public static boolean isValidUsername(String username) throws IOException {
        String usernameLower = username.toLowerCase();

        // Check cache first
        CachedPremiumResult cached = PREMIUM_CACHE.get(usernameLower);
        if (cached != null && !cached.isExpired()) {
            LogDebug("Player " + username + " premium status (cached): " + cached.isPremium);
            return cached.isPremium;
        }

        LogDebug("Checking player " + username + " for premium status");
        HttpsURLConnection httpsURLConnection = (HttpsURLConnection) URI.create(extendedConfig.mojangApiSettings.url + username).toURL().openConnection();
        httpsURLConnection.setRequestMethod("GET");
        httpsURLConnection.setConnectTimeout(extendedConfig.mojangApiSettings.connectionTimeout);
        httpsURLConnection.setReadTimeout(extendedConfig.mojangApiSettings.readTimeout);

        int response = httpsURLConnection.getResponseCode();
        boolean result;

        if (response == HttpURLConnection.HTTP_OK) {
            // Player has a Mojang account
            httpsURLConnection.disconnect();
            LogDebug("Player " + username + " has a Mojang account");
            result = true;
        } else if (response == HttpURLConnection.HTTP_NO_CONTENT || response == HttpURLConnection.HTTP_NOT_FOUND) {
            // Player doesn't have a Mojang account
            httpsURLConnection.disconnect();
            LogDebug("Player " + username + " doesn't have a Mojang account");
            result = false;
        } else {
            LogDebug("Unexpected response code " + response + " for player " + username);
            throw new IOException("Unexpected response code " + response + " for player " + username);
        }

        // Cache the result
        PREMIUM_CACHE.put(usernameLower, new CachedPremiumResult(result, PREMIUM_CACHE_TTL_MS));
        return result;
    }

    public static UUID getUuid(String username) throws IOException {
        String usernameLower = username.toLowerCase();

        // Check permanent cache first
        UUID cachedUuid = UUID_CACHE.get(usernameLower);
        if (cachedUuid != null) {
            LogDebug("Player " + username + " UUID (cached): " + cachedUuid);
            return cachedUuid;
        }

        HttpsURLConnection httpsURLConnection = (HttpsURLConnection) URI.create(extendedConfig.mojangApiSettings.url + username).toURL().openConnection();
        httpsURLConnection.setRequestMethod("GET");
        httpsURLConnection.setConnectTimeout(extendedConfig.mojangApiSettings.connectionTimeout);
        httpsURLConnection.setReadTimeout(extendedConfig.mojangApiSettings.readTimeout);

        int response = httpsURLConnection.getResponseCode();
        if (response == HttpURLConnection.HTTP_OK) {
            String responseBody = new String(httpsURLConnection.getInputStream().readAllBytes());
            httpsURLConnection.disconnect();

            // Extract UUID from the response body
            String uuidString = responseBody.split("\"id\" : \"")[1].split("\"")[0];
            UUID uuid = UUID.fromString(uuidString.replaceFirst("(.{8})(.{4})(.{4})(.{4})(.{12})", "$1-$2-$3-$4-$5"));
            LogDebug("Player " + username + " has UUID: " + uuid);

            // Cache permanently (UUIDs never change)
            UUID_CACHE.put(usernameLower, uuid);
            return uuid;
        } else if (response == HttpURLConnection.HTTP_NO_CONTENT || response == HttpURLConnection.HTTP_NOT_FOUND) {
            httpsURLConnection.disconnect();
            LogDebug("Player " + username + " not found");
            return null;
        }
        LogDebug("Unexpected response code " + response + " for player " + username);
        throw new IOException("Unexpected response code " + response + " for player " + username);
    }
}

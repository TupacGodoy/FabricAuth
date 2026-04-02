package xyz.nikitacartes.easyauth.utils;

import xyz.nikitacartes.easyauth.storage.PlayerEntryV1;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static xyz.nikitacartes.easyauth.EasyAuth.DB;
import static xyz.nikitacartes.easyauth.EasyAuth.config;

/**
 * Optimized player data cache with LRU eviction and TTL support.
 * Prevents memory leaks on servers with many unique players.
 */
public class PlayersCache {
    // Default cache size - configurable via system property
    private static final int MAX_CACHE_SIZE = Integer.getInteger("easyauth.cache.maxSize", 10000);
    // TTL for cache entries in milliseconds (30 minutes default)
    private static final long CACHE_ENTRY_TTL_MS = Long.getLong("easyauth.cache.ttlMinutes", 30) * 60 * 1000;
    // Cleanup interval in milliseconds (5 minutes default)
    private static final long CLEANUP_INTERVAL_MS = Long.getLong("easyauth.cache.cleanupIntervalMinutes", 5) * 60 * 1000;

    private static final class CacheEntry {
        final PlayerEntryV1 data;
        volatile long lastAccessTime;

        CacheEntry(PlayerEntryV1 data) {
            this.data = data;
            this.lastAccessTime = System.currentTimeMillis();
        }

        void touch() {
            this.lastAccessTime = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - lastAccessTime > CACHE_ENTRY_TTL_MS;
        }
    }

    // LRU cache with access-order for automatic eviction
    private static final LinkedHashMap<String, CacheEntry> playerDataCache = new LinkedHashMap<>(
            MAX_CACHE_SIZE, 0.75f, true
    ) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, CacheEntry> eldest) {
            boolean shouldRemove = size() > MAX_CACHE_SIZE;
            if (shouldRemove && config.debug) {
                // Eldest entry will be removed automatically by LinkedHashMap
            }
            return shouldRemove;
        }
    };

    private static volatile long lastCleanupTime = System.currentTimeMillis();

    /**
     * Performs periodic cleanup of expired entries.
     * Called on cache operations to avoid dedicated cleanup thread.
     */
    private static void maybeCleanup() {
        long now = System.currentTimeMillis();
        if (now - lastCleanupTime < CLEANUP_INTERVAL_MS) {
            return;
        }

        synchronized (playerDataCache) {
            // Double-check after acquiring lock
            if (now - lastCleanupTime < CLEANUP_INTERVAL_MS) {
                return;
            }
            lastCleanupTime = now;

            playerDataCache.entrySet().removeIf(entry -> entry.getValue().isExpired());
        }
    }

    public static void put(String username, PlayerEntryV1 data) {
        if (username == null || data == null) return;

        maybeCleanup();
        synchronized (playerDataCache) {
            playerDataCache.put(username.toLowerCase(), new CacheEntry(data));
        }
    }

    public static PlayerEntryV1 get(String username) {
        if (username == null) return null;

        maybeCleanup();
        synchronized (playerDataCache) {
            CacheEntry entry = playerDataCache.get(username.toLowerCase());
            if (entry != null) {
                entry.touch();
                return entry.data;
            }
            return null;
        }
    }

    public static PlayerEntryV1 getOrLoadOrRegister(String username) {
        PlayerEntryV1 playerEntry = get(username);
        // Cache should contain the player's data, but Floodgate players are not cached for some reason
        if (playerEntry == null) {
            playerEntry = loadOrRegister(username);
        }
        return playerEntry;
    }

    public static PlayerEntryV1 loadOrRegister(String username) {
        PlayerEntryV1 playerEntry = DB.getUserData(username);
        if (playerEntry == null) {
            playerEntry = new PlayerEntryV1(username);
            if (config.offlineByDefault) {
                playerEntry.onlineAccount = PlayerEntryV1.OnlineAccount.FALSE;
            }
            DB.registerUser(playerEntry);
        }
        put(username, playerEntry);
        return playerEntry;
    }

    public static PlayerEntryV1 getOrCreate(String username) {
        PlayerEntryV1 data = get(username);

        if (data == null) {
            data = new PlayerEntryV1(username);
            put(username, data);
        }
        return data;
    }

    /**
     * Invalidates a specific entry from the cache.
     * Useful when player data is modified externally.
     */
    public static void invalidate(String username) {
        if (username == null) return;

        synchronized (playerDataCache) {
            playerDataCache.remove(username.toLowerCase());
        }
    }

    /**
     * Clears the entire cache.
     * Useful for reload operations or memory pressure situations.
     */
    public static void clear() {
        synchronized (playerDataCache) {
            playerDataCache.clear();
        }
    }

    /**
     * Returns current cache size for debugging/monitoring.
     */
    public static int size() {
        synchronized (playerDataCache) {
            return playerDataCache.size();
        }
    }
}

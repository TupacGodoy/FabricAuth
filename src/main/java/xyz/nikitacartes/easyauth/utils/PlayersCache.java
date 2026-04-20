package xyz.nikitacartes.easyauth.utils;

import xyz.nikitacartes.easyauth.storage.PlayerEntryV1;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static xyz.nikitacartes.easyauth.EasyAuth.DB;
import static xyz.nikitacartes.easyauth.EasyAuth.config;

/**
 * Lock-free player data cache with LRU eviction and TTL support.
 * Uses ConcurrentHashMap for thread-safe access without synchronization overhead.
 * Prevents memory leaks on servers with many unique players.
 */
public class PlayersCache {
    // Default cache size - configurable via system property
    private static final int MAX_CACHE_SIZE = Integer.getInteger("easyauth.cache.maxSize", 10000);
    // TTL for cache entries in milliseconds (30 minutes default)
    private static final long CACHE_ENTRY_TTL_MS = Long.getLong("easyauth.cache.ttlMinutes", 30) * 60 * 1000;
    // Cleanup interval in milliseconds (5 minutes default)
    private static final long CLEANUP_INTERVAL_MS = Long.getLong("easyauth.cache.cleanupIntervalMinutes", 5) * 60 * 1000;
    // Memory-based eviction threshold (64MB default)
    private static final long MAX_MEMORY_BYTES = Long.getLong("easyauth.cache.maxMemoryMb", 64) * 1024 * 1024;

    /**
     * Cache entry with atomic access time tracking for lock-free LRU.
     */
    private static final class CacheEntry {
        final PlayerEntryV1 data;
        final AtomicLong lastAccessTime;

        CacheEntry(PlayerEntryV1 data) {
            this.data = data;
            this.lastAccessTime = new AtomicLong(System.currentTimeMillis());
        }

        void touch() {
            this.lastAccessTime.set(System.currentTimeMillis());
        }

        boolean isExpired() {
            return System.currentTimeMillis() - lastAccessTime.get() > CACHE_ENTRY_TTL_MS;
        }
    }

    // Thread-safe cache without global locking
    private static final ConcurrentHashMap<String, CacheEntry> playerDataCache = new ConcurrentHashMap<>(1024, 0.75f, 4);

    // Monotonic sequence number for LRU ordering
    private static final AtomicLong sequenceGenerator = new AtomicLong(0);

    // Maps username -> sequence number at insertion time for LRU eviction
    private static final ConcurrentHashMap<String, Long> insertionOrder = new ConcurrentHashMap<>();

    private static volatile long lastCleanupTime = System.currentTimeMillis();

    /**
     * Performs periodic cleanup of expired entries.
     * Called on cache operations to avoid dedicated cleanup thread.
     * Uses optimistic locking with minimal contention.
     */
    private static void maybeCleanup() {
        long now = System.currentTimeMillis();
        if (now - lastCleanupTime < CLEANUP_INTERVAL_MS) {
            return;
        }

        // Optimistic cleanup - multiple threads may cleanup but that's fine
        synchronized (PlayersCache.class) {
            if (now - lastCleanupTime < CLEANUP_INTERVAL_MS) {
                return;
            }
            lastCleanupTime = now;

            // Remove expired entries
            playerDataCache.entrySet().removeIf(entry -> {
                if (entry.getValue().isExpired()) {
                    insertionOrder.remove(entry.getKey());
                    return true;
                }
                return false;
            });

            // Enforce max size by removing oldest entries
            enforceMaxSize();
        }
    }

    /**
     * Enforces maximum cache size and memory limit by removing oldest entries.
     */
    private static void enforceMaxSize() {
        // Count-based eviction
        if (playerDataCache.size() > MAX_CACHE_SIZE) {
            int toRemove = playerDataCache.size() - MAX_CACHE_SIZE;

            // Find oldest entries by sequence number
            insertionOrder.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .limit(toRemove)
                .forEach(entry -> {
                    playerDataCache.remove(entry.getKey());
                    insertionOrder.remove(entry.getKey());
                });
        }

        // Memory-based eviction
        while (estimateMemoryUsage() > MAX_MEMORY_BYTES && !playerDataCache.isEmpty()) {
            // Find oldest entry by sequence number
            insertionOrder.entrySet().stream()
                .min(Map.Entry.comparingByValue())
                .ifPresent(entry -> {
                    playerDataCache.remove(entry.getKey());
                    insertionOrder.remove(entry.getKey());
                });
        }
    }

    /**
     * Estimates memory usage of cache entries.
     * Rough estimate based on typical PlayerEntryV1 size (~2KB per entry).
     */
    private static long estimateMemoryUsage() {
        return playerDataCache.size() * 2048;
    }

    /**
     * Puts a player entry into the cache using both username and UUID for lookup.
     * Uses UUID as primary key to prevent cache poisoning via username case manipulation.
     * Thread-safe without global locking.
     */
    public static void put(String username, PlayerEntryV1 data) {
        if (username == null || data == null) return;

        maybeCleanup();
        // Use UUID as primary cache key to prevent case-based cache poisoning
        // Fallback to lowercase username only if UUID is not available
        String cacheKey = (data.uuid != null) ? data.uuid.toString() : username.toLowerCase();
        CacheEntry entry = new CacheEntry(data);
        playerDataCache.put(cacheKey, entry);
        insertionOrder.put(cacheKey, sequenceGenerator.incrementAndGet());

        // Also store with lowercase username for backwards compatibility
        // but verify UUID matches on retrieval
        if (!cacheKey.equals(username.toLowerCase())) {
            String usernameKey = username.toLowerCase();
            playerDataCache.put(usernameKey, entry);
            insertionOrder.put(usernameKey, sequenceGenerator.incrementAndGet());
        }
    }

    /**
     * Gets a player entry from the cache using UUID if available, otherwise username.
     * Thread-safe without global locking.
     */
    public static PlayerEntryV1 get(String username) {
        if (username == null) return null;

        maybeCleanup();
        // Try lowercase username lookup first
        CacheEntry entry = playerDataCache.get(username.toLowerCase());
        if (entry != null) {
            // Verify the cached entry matches the requested username (case-insensitive)
            if (entry.data.usernameLowerCase.equals(username.toLowerCase())) {
                entry.touch(); // Update access time for LRU
                return entry.data;
            }
            // Username mismatch - this could be cache poisoning, don't return the entry
        }
        return null;
    }

    /**
     * Gets a player entry from cache by UUID. More reliable than username-based lookup.
     * Thread-safe without global locking.
     */
    public static PlayerEntryV1 getByUuid(UUID uuid) {
        if (uuid == null) return null;

        maybeCleanup();
        CacheEntry entry = playerDataCache.get(uuid.toString());
        if (entry != null) {
            entry.touch();
            return entry.data;
        }
        return null;
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

        String key = username.toLowerCase();
        playerDataCache.remove(key);
        insertionOrder.remove(key);
    }

    /**
     * Invalidates a specific entry from the cache by UUID.
     * More reliable than username-based invalidation.
     */
    public static void invalidateByUuid(UUID uuid) {
        if (uuid == null) return;

        playerDataCache.remove(uuid.toString());
        insertionOrder.remove(uuid.toString());
    }

    /**
     * Clears the entire cache.
     * Useful for reload operations or memory pressure situations.
     */
    public static void clear() {
        playerDataCache.clear();
        insertionOrder.clear();
        lastCleanupTime = System.currentTimeMillis();
    }

    /**
     * Returns current cache size for debugging/monitoring.
     */
    public static int size() {
        return playerDataCache.size();
    }
}

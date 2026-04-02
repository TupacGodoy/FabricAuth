package xyz.nikitacartes.easyauth.utils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A thread-safe cache with automatic expiration based on TTL.
 * Optimized for frequent reads with periodic cleanup.
 *
 * @param <K> Key type
 * @param <V> Value type
 */
public class TemporalCache<K, V> {
    private final long ttlMillis;
    private final int maxSize;
    private final ConcurrentHashMap<K, CacheEntry<V>> map;
    private volatile long lastCleanupTime;
    private static final long CLEANUP_INTERVAL_MS = 60_000; // Cleanup every minute

    private static final class CacheEntry<V> {
        final V value;
        final long expiryTime;

        CacheEntry(V value, long ttlMillis) {
            this.value = value;
            this.expiryTime = System.currentTimeMillis() + ttlMillis;
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expiryTime;
        }
    }

    /**
     * Creates a new TemporalCache.
     *
     * @param ttlMillis Time-to-live in milliseconds
     * @param maxSize Maximum number of entries before forced cleanup
     */
    public TemporalCache(long ttlMillis, int maxSize) {
        this.ttlMillis = ttlMillis;
        this.maxSize = maxSize;
        this.map = new ConcurrentHashMap<>(16, 0.75f);
        this.lastCleanupTime = System.currentTimeMillis();
    }

    /**
     * Gets a value from the cache, or null if not present or expired.
     */
    public V get(K key) {
        maybeCleanup();
        CacheEntry<V> entry = map.get(key);
        if (entry == null) {
            return null;
        }
        if (entry.isExpired()) {
            map.remove(key);
            return null;
        }
        return entry.value;
    }

    /**
     * Gets a value or returns default if not present/expired.
     */
    public V getOrDefault(K key, V defaultValue) {
        V value = get(key);
        return value != null ? value : defaultValue;
    }

    /**
     * Puts a value into the cache with the configured TTL.
     */
    public void put(K key, V value) {
        maybeCleanup();

        // Force cleanup if we're at max capacity
        if (map.size() >= maxSize) {
            forceCleanup();
        }

        map.put(key, new CacheEntry<>(value, ttlMillis));
    }

    /**
     * Gets existing value or computes and stores if absent.
     * Atomic operation.
     */
    public V computeIfAbsent(K key, java.util.function.Function<K, V> mappingFunction) {
        maybeCleanup();

        V existing = get(key);
        if (existing != null) {
            return existing;
        }

        // Compute outside of lock for better concurrency
        V newValue = mappingFunction.apply(key);
        if (newValue == null) {
            return null;
        }

        // Use putIfAbsent for thread safety
        CacheEntry<V> newEntry = new CacheEntry<>(newValue, ttlMillis);
        CacheEntry<V> existingEntry = map.putIfAbsent(key, newEntry);

        return existingEntry != null ? existingEntry.value : newValue;
    }

    /**
     * Removes a specific entry.
     */
    public void remove(K key) {
        map.remove(key);
    }

    /**
     * Clears all entries.
     */
    public void clear() {
        map.clear();
        lastCleanupTime = System.currentTimeMillis();
    }

    /**
     * Returns current size (including potentially expired entries).
     */
    public int size() {
        return map.size();
    }

    /**
     * Performs cleanup if interval has passed.
     * Uses timestamp check to avoid locking overhead.
     */
    private void maybeCleanup() {
        long now = System.currentTimeMillis();
        if (now - lastCleanupTime < CLEANUP_INTERVAL_MS) {
            return;
        }
        forceCleanup();
    }

    /**
     * Forces immediate cleanup of expired entries.
     */
    private synchronized void forceCleanup() {
        long now = System.currentTimeMillis();
        lastCleanupTime = now;

        // Remove expired entries
        map.entrySet().removeIf(entry -> entry.getValue().isExpired());

        // If still over max size, remove oldest entries
        if (map.size() > maxSize) {
            int entriesToRemove = map.size() - maxSize + (maxSize / 10); // Remove 10% extra

            // Find oldest entries (simple approximation by iterating)
            // For large caches, this could be optimized with an access-order LinkedHashMap
            var iterator = map.entrySet().iterator();
            int removed = 0;
            while (iterator.hasNext() && removed < entriesToRemove) {
                iterator.next();
                iterator.remove();
                removed++;
            }
        }
    }
}

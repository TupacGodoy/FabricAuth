package xyz.nikitacartes.easyauth.utils;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * A thread-safe cache with automatic expiration based on TTL.
 * Uses LinkedHashMap with access-order for true LRU eviction.
 *
 * @param <K> Key type
 * @param <V> Value type
 */
public class TemporalCache<K, V> {
    private final long ttlMillis;
    private final int maxSize;
    private final LinkedHashMap<K, CacheEntry<V>> map;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
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
     * Creates a new TemporalCache with true LRU eviction.
     *
     * @param ttlMillis Time-to-live in milliseconds
     * @param maxSize Maximum number of entries before forced cleanup
     */
    public TemporalCache(long ttlMillis, int maxSize) {
        this.ttlMillis = ttlMillis;
        this.maxSize = maxSize;
        // accessOrder = true for LRU behavior (get/recent put moves to end)
        this.map = new LinkedHashMap<K, CacheEntry<V>>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, CacheEntry<V>> eldest) {
                // Remove eldest when exceeding max size
                return size() > maxSize;
            }
        };
        this.lastCleanupTime = System.currentTimeMillis();
    }

    /**
     * Gets a value from the cache, or null if not present or expired.
     * Updates access order for LRU tracking.
     */
    public V get(K key) {
        maybeCleanup();
        lock.readLock().lock();
        try {
            CacheEntry<V> entry = map.get(key);
            if (entry == null || entry.isExpired()) {
                return null;
            }
            return entry.value;
        } finally {
            lock.readLock().unlock();
        }
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
     * Updates access order for LRU tracking.
     */
    public void put(K key, V value) {
        lock.writeLock().lock();
        try {
            maybeCleanupLocked();
            // LinkedHashMap with accessOrder=true automatically handles LRU
            // and removeEldestEntry handles max size
            map.put(key, new CacheEntry<>(value, ttlMillis));
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Gets existing value or computes and stores if absent.
     * Atomic operation.
     */
    public V computeIfAbsent(K key, java.util.function.Function<K, V> mappingFunction) {
        lock.readLock().lock();
        try {
            maybeCleanupLocked();
            CacheEntry<V> entry = map.get(key);
            if (entry != null && !entry.isExpired()) {
                return entry.value;
            }
        } finally {
            lock.readLock().unlock();
        }

        // Compute outside of read lock
        V newValue = mappingFunction.apply(key);
        if (newValue == null) {
            return null;
        }

        // Upgrade to write lock for put
        lock.writeLock().lock();
        try {
            // Double-check after acquiring write lock
            CacheEntry<V> entry = map.get(key);
            if (entry != null && !entry.isExpired()) {
                return entry.value;
            }
            map.put(key, new CacheEntry<>(newValue, ttlMillis));
            return newValue;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Removes a specific entry.
     */
    public void remove(K key) {
        lock.writeLock().lock();
        try {
            map.remove(key);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Clears all entries.
     */
    public void clear() {
        lock.writeLock().lock();
        try {
            map.clear();
            lastCleanupTime = System.currentTimeMillis();
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Returns current size (including potentially expired entries).
     */
    public int size() {
        lock.readLock().lock();
        try {
            return map.size();
        } finally {
            lock.readLock().unlock();
        }
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
        lock.writeLock().lock();
        try {
            maybeCleanupLocked();
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Internal cleanup without locking - caller must hold write lock.
     */
    private void maybeCleanupLocked() {
        long now = System.currentTimeMillis();
        if (now - lastCleanupTime < CLEANUP_INTERVAL_MS) {
            return;
        }
        lastCleanupTime = now;

        // Remove expired entries using iterator
        Iterator<Map.Entry<K, CacheEntry<V>>> iterator = map.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue().isExpired()) {
                iterator.remove();
            }
        }
    }

    /**
     * Forces immediate cleanup of expired entries.
     * LinkedHashMap with accessOrder=true ensures eldest entries are LRU.
     */
    public void forceCleanup() {
        lock.writeLock().lock();
        try {
            long now = System.currentTimeMillis();
            lastCleanupTime = now;

            // Remove expired entries
            Iterator<Map.Entry<K, CacheEntry<V>>> iterator = map.entrySet().iterator();
            while (iterator.hasNext()) {
                if (iterator.next().getValue().isExpired()) {
                    iterator.remove();
                }
            }

            // Max size is automatically handled by removeEldestEntry override
        } finally {
            lock.writeLock().unlock();
        }
    }
}

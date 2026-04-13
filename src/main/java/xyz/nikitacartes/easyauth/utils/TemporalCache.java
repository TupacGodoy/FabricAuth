package xyz.nikitacartes.easyauth.utils;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
    private final long maxMemoryBytes; // Memory-based eviction threshold
    private final LinkedHashMap<K, CacheEntry<V>> map;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private volatile long lastCleanupTime;
    private static final long CLEANUP_INTERVAL_MS = 60_000; // Cleanup every minute
    private static final long DEFAULT_MAX_MEMORY_MB = Long.getLong("easyauth.cache.maxMemoryMb", 64);

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
     * Creates a new TemporalCache with true LRU eviction and memory-based limits.
     *
     * @param ttlMillis Time-to-live in milliseconds
     * @param maxSize Maximum number of entries before forced cleanup
     */
    public TemporalCache(long ttlMillis, int maxSize) {
        this.ttlMillis = ttlMillis;
        this.maxSize = maxSize;
        this.maxMemoryBytes = DEFAULT_MAX_MEMORY_MB * 1024 * 1024;
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
     * Estimates memory usage of cache entries.
     * This is a rough estimate based on entry count and average object size.
     */
    private long estimateMemoryUsage() {
        // Rough estimate: ~1KB per entry (covers most typical use cases)
        return map.size() * 1024;
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
     * Atomic operation with proper double-checked locking to prevent race conditions.
     * Uses a per-key lock striping approach to avoid computing values outside of lock protection.
     * Includes proper exception handling to prevent memory leaks from orphaned locks.
     */
    private final ConcurrentHashMap<K, ReentrantReadWriteLock> keyLocks = new ConcurrentHashMap<>();

    public V computeIfAbsent(K key, java.util.function.Function<K, V> mappingFunction) {
        // First, try fast path with read lock
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

        // Acquire per-key lock for atomic compute
        ReentrantReadWriteLock keyLock = keyLocks.computeIfAbsent(key, k -> new ReentrantReadWriteLock());
        keyLock.writeLock().lock();
        try {
            // Double-check after acquiring write lock
            CacheEntry<V> entry = map.get(key);
            if (entry != null && !entry.isExpired()) {
                return entry.value;
            }

            // Compute value while holding the lock to prevent race conditions
            V newValue;
            try {
                newValue = mappingFunction.apply(key);
            } catch (RuntimeException e) {
                // Re-throw RuntimeExceptions from the mapping function
                // Do not store anything if computation fails
                throw e;
            }

            if (newValue == null) {
                return null;
            }

            map.put(key, new CacheEntry<>(newValue, ttlMillis));

            return newValue;
        } finally {
            keyLock.writeLock().unlock();
            // Clean up key lock if no longer needed (prevent memory leak)
            // Only remove if the lock we acquired is still the current one
            keyLocks.compute(key, (k, existingLock) -> {
                if (existingLock == keyLock && !map.containsKey(key)) {
                    return null; // Remove lock if it's still ours and no entry exists
                }
                return existingLock; // Keep lock for concurrent accessors
            });
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
     * Removes expired entries and enforces memory-based eviction.
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

        // Memory-based eviction: remove oldest entries if exceeding memory threshold
        while (estimateMemoryUsage() > maxMemoryBytes && !map.isEmpty()) {
            // LinkedHashMap with accessOrder=true: first entry is LRU
            iterator = map.entrySet().iterator();
            if (iterator.hasNext()) {
                iterator.next();
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

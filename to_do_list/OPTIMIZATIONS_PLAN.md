# Server Optimizations - Implementation Plan

List of optimizations to implement (highest to lowest priority):

---

## 1. [HIGH] PlayersCache - Replace synchronized with ConcurrentHashMap

**File:** `src/main/java/xyz/nikitacartes/easyauth/utils/PlayersCache.java`

**Problem:** Uses `synchronized` on every `get`/`put` operation, creating contention on servers with many players.

**Solution:** Replace `LinkedHashMap` + `synchronized` with `ConcurrentHashMap` and `AtomicReference` for better lock-free concurrency.

**Impact:** High - Reduces thread contention during player authentication

---

## 2. [HIGH] PlayerEntryV1 - Write-back batching for DB updates

**File:** `src/main/java/xyz/nikitacartes/easyauth/storage/PlayerEntryV1.java`

**Problem:** `update()` serializes the entire object and executes in THREADPOOL without batching or rate-limiting.

**Solution:** 
- Cache JSON serialization and only re-serialize when fields change
- Implement write-back with debounce (~1s wait to batch multiple updates)

**Impact:** High - Reduces DB writes and redundant serialization

---

## 3. [MEDIUM] MojangApi - HTTP response cache

**File:** `src/main/java/xyz/nikitacartes/easyauth/integrations/MojangApi.java`

**Problem:** Every `isValidUsername` call makes a synchronous HTTP request without caching.

**Solution:** 
- Local cache with 24h TTL for premium check results
- UUIDs never change - permanent cache

**Impact:** Medium - Reduces Mojang HTTP calls and login latency

---

## 4. [MEDIUM] TemporalCache - Real LRU with LinkedHashMap

**File:** `src/main/java/xyz/nikitacartes/easyauth/utils/TemporalCache.java`

**Problem:** `forceCleanup` iterates all entries and removes the first N% without actual age-based criteria.

**Solution:** Use `LinkedHashMap` with access-order for real LRU eviction, or implement a priority heap based on `expiryTime`.

**Impact:** Medium - Reduces CPU in cleanup and improves eviction

---

## 5. [LOW] AuthEventHandler - Bitmask flags for configuration

**File:** `src/main/java/xyz/nikitacartes/easyauth/event/AuthEventHandler.java`

**Problem:** Configuration checks (`extendedConfig.allowChat`, etc.) are evaluated every time even though they never change at runtime.

**Solution:** Use pre-computed bitmask or flags that only update on config reload.

**Impact:** Low-Medium - Reduces conditional checks in packet filtering

---

## 6. [LOW] MySQL - Prepared Statements Cache

**File:** `src/main/java/xyz/nikitacartes/easyauth/storage/database/MySQL.java`

**Problem:** Prepared statements are created and destroyed on every operation.

**Solution:** Implement a cached pool of PreparedStatements per thread or use ThreadLocal.

**Impact:** Low - Reduces statement creation overhead

---

## 7. [LOW] HasherArgon2/BCrypt - Verification cache

**Files:** 
- `src/main/java/xyz/nikitacartes/easyauth/utils/hashing/HasherArgon2.java`
- `src/main/java/xyz/nikitacartes/easyauth/utils/hashing/HasherBCrypt.java`

**Problem:** Every login attempt verifies the hash without caching recent successful verifications.

**Solution:** Cache of last N successfully verified hashes with short TTL (5 min) to avoid re-verification in batch operations.

**Impact:** Low - Only relevant in edge cases of rapid multiple verifications

---

# Progress

- [x] 1. PlayersCache - ConcurrentHashMap (COMPLETED)
- [ ] 2. PlayerEntryV1 - Write-back batching
- [ ] 3. MojangApi - HTTP cache
- [ ] 4. TemporalCache - Real LRU
- [ ] 5. AuthEventHandler - Bitmask flags
- [ ] 6. MySQL - Prepared Statements cache
- [ ] 7. HasherArgon2/BCrypt - Verification cache

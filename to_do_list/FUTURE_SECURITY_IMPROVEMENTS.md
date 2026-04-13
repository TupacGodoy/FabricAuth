# Future Security Improvements - FabricAuth

**Last updated:** 2026-04-13

Optional security enhancements that are **not critical** but could be implemented.

---

## Implemented Security Improvements

The following improvements have been implemented:

### ✅ Session Token Entropy Validation (#11)
**Implemented:** 2026-04-13 - `AuthEventHandler.java`
- Added minimum token length validation (20 characters)
- Added character set validation (base64url only)
- Prevents brute-force attacks on session tokens

### ✅ Command Injection Prevention (#4)
**Implemented:** Previously - `RegisterCommand.java`, `LoginCommand.java`
- Null character rejection
- Shell metacharacter rejection (`;|&$\``)
- Unicode normalization for consistent comparison

### ✅ MongoDB Login Rate Limiting (#12, #13)
**Implemented:** Previously - `MongoDB.java`
- Login attempts collection with indexes
- Full implementation of rate limiting methods

### ✅ UUID Format Validation (#17)
**Implemented:** Previously - `PlayerEntryV1.java`
- UUID format validation on `setForcedUuid()`
- Prevents malformed UUID injection

### ✅ PlayerEntryV1 Shutdown Hook (#15)
**Implemented:** Previously - `PlayerEntryV1.java`
- Shutdown hook flushes pending writes on server stop
- Prevents data loss on crash

### ✅ SQL Injection Prevention in Table Names (#8)
**Implemented:** Previously - `MySQL.java`, `PostgreSQL.java`
- `isValidIdentifier()` method validates table/database names
- Regex pattern `^[a-zA-Z0-9_-]+$` enforced

---

---

## Security Improvements

### 1. Argon2id as Default Algorithm

**Priority:** Low

**Current state:** BCrypt is used as the default password hashing algorithm. Legacy Argon2 passwords are migrated to BCrypt on login.

**Consideration:** Argon2id is technically superior to BCrypt (memory-hard, resistant to GPU attacks).

**Potential improvements:**
- Revert to Argon2id as the default algorithm
- Add configuration option to choose between Argon2id and BCrypt
- Document the security trade-offs for server administrators

---

### 2. Generic Error Messages Config Option

**Priority:** Low

**Current state:** Error messages like `playerAlreadyOnline` reveal that a player account exists.

**Consideration:** This is a UX vs security trade-off. Generic messages improve security but reduce user experience.

**Potential improvement:**
- Add config option `genericErrorMessages` (default: `false`)
- When enabled, use generic messages like "Authentication failed" for all errors

---

### 3. fail2ban Integration Documentation

**Priority:** Low

**Current state:** Rate limiting is implemented in-application with exponential backoff.

**Consideration:** Large-scale servers may benefit from OS-level blocking.

**Potential improvement:**
- Add documentation for integrating with fail2ban
- Provide example filter rules for parsing EasyAuth logs

---

### 4. Command Injection via Argument Parsing

**Priority:** Medium

**Files:** `RegisterCommand.java:48-56`, `LoginCommand.java:39`

**Problem:** Command arguments are retrieved using `getString(ctx, "password")` without validation for command injection patterns. While null characters are checked, other injection vectors like command separators (`;`, `|`, `&`) in password fields could be exploited if the password is ever logged or passed to shell commands.

**Current mitigation:** Null character check exists (`indexOf('\0')`), but no validation for shell metacharacters.

**Fix:** Add comprehensive input sanitization for password fields before any logging or external processing.

---

### 5. Race Condition in Session Token Validation

**Priority:** Medium-High

**Files:** `AuthEventHandler.java:470-492`

**Problem:** TOCTOU (Time-of-check to time-of-use) race condition in session validation:
1. Check if IP hash matches and session is valid
2. Read client session token and compare with stored token
3. Generate new token and update cache

Between steps 1-3, another thread could modify `cache.sessionToken`, potentially allowing session fixation if an attacker can inject a token during the window.

**Fix:** Use atomic compare-and-swap operation or synchronize the entire validation block.

---

### 6. Inconsistent Lock Ordering in TemporalCache

**Priority:** Medium

**Files:** `TemporalCache.java:115-146`

**Problem:** The `computeIfAbsent` method has a lock ordering issue:
1. Acquires read lock
2. Releases read lock
3. Computes value outside any lock
4. Acquires write lock
5. Double-checks

If two threads compute the same key simultaneously, both will compute the value, and the last writer wins. This could lead to:
- Wasted computation
- Potential security issue if the mapping function has side effects (e.g., registering a user)

**Fix:** Use `java.util.concurrent.ConcurrentHashMap.computeIfAbsent` or implement proper double-checked locking with atomic references.

---

### 7. HMAC Key Stored in Config File

**Priority:** Low-Medium

**Files:** `TechnicalConfigV1.java:63`, `AuthEventHandler.java:312-316`

**Problem:** The HMAC-SHA256 key for IP hashing (`ipHmacKey`) is:
1. Generated on first run with `SecureRandom`
2. Stored in plaintext in `technical.conf`
3. If an attacker gains read access to the config file, they can reconstruct IP addresses from stored hashes

**Impact:** GDPR/privacy compliance issue - IP addresses could be reconstructed if config file is compromised.

**Fix:** 
- Derive HMAC key from server UUID (not stored separately)
- Or use OS-level keyring/secrets management
- Or encrypt the config file

---

### 8. SQL Injection Risk in Table Name

**Priority:** Low

**Files:** `MySQL.java:105-117`, `PostgreSQL.java:82-91`

**Problem:** Table names are interpolated using `String.format()` without sanitization:
```java
String.format("CREATE TABLE `%s`.`%s`...", config.mysql.mysqlDatabase, config.mysql.mysqlTable)
```

While the table name comes from config (not user input), if an admin's config is compromised or if there's a config injection vulnerability, arbitrary SQL could be executed.

**Fix:** Validate table names against a whitelist pattern (e.g., `^[a-zA-Z0-9_-]+$`) before use in SQL statements.

---

### 9. Cache Poisoning via Username Case Manipulation

**Priority:** Low

**Files:** `PlayersCache.java:133-141`, `IpLimitManager.java:141-149`

**Problem:** Cache keys are normalized to lowercase (`username.toLowerCase()`), but the original `PlayerEntryV1` may store the username with original case. If `allowCaseInsensitiveUsername` is enabled:
1. Player "Test" registers, cached as "test"
2. Player "test" joins, cache returns "Test"'s data
3. This could lead to incorrect IP limit checks or data leakage

**Current mitigation:** `PlayerEntryV1` stores both `username` and `usernameLowerCase`, but the cache lookup uses only lowercase.

**Fix:** Ensure cache key includes both username forms or use UUID-based caching instead of username-based.

---

### 10. Missing Rate Limiting on Custom Packet Handling

**Priority:** Low

**Files:** `AuthEventHandler.java:236-260`

**Problem:** Custom payload packets are allowed based on config flags, but there's no rate limiting for custom packets from unauthenticated players. An attacker could:
1. Join without authenticating
2. Flood the server with custom payload packets
3. Cause CPU/memory exhaustion through packet processing

**Fix:** Add rate limiting for custom payload packets from unauthenticated players.

---

### 11. Session Token Entropy Not Verified

**Priority:** Low

**Files:** `AuthEventHandler.java:295-301`

**Problem:** Session tokens are generated with 128 bits of entropy (16 bytes from `SecureRandom`), but there's no validation that provided tokens have sufficient entropy. An attacker could:
1. Brute-force low-entropy tokens
2. Use predictable tokens from weak PRNG states

**Fix:** Validate that session tokens have minimum length and entropy when received from clients.

---

### 12. MongoDB Login Rate Limiting Not Implemented

**Priority:** Medium-High

**Files:** `MongoDB.java`

**Problem:** MongoDB implementation does NOT implement the `recordLoginAttempt`, `getLoginAttemptsInWindow`, `clearLoginAttempts`, and `getUsernameByUuid` methods from `DbApi` interface. This means:
- Login rate limiting is completely bypassed when using MongoDB
- Attackers can brute-force passwords without any throttling
- The `isLoginRateLimitExceeded` check in `LoginCommand.java` will always return false

**Current state:** Methods are inherited from `DbApi` default implementations, but MongoDB doesn't have the `login_attempts` collection/table.

**Fix:** Implement login rate limiting collection and methods for MongoDB:
```java
@Override
public void recordLoginAttempt(String ipHash, long timestamp) {
    collection.insertOne(new Document("ip_hash", ipHash).append("timestamp", timestamp));
}
// ... implement other rate limiting methods
```

---

### 13. MongoDB Missing `initializeLoginAttemptsTable` Call

**Priority:** Medium-High

**Files:** `MongoDB.java:32-46`

**Problem:** Unlike MySQL (`initializeLoginAttemptsTable()` at line 86) and SQLite (`initializeLoginAttemptsTable()` at line 81), MongoDB's `connect()` method does not initialize any collection for login attempts tracking.

**Fix:** Add login attempts collection initialization in MongoDB's `connect()` method:
```java
// Create login_attempts collection with indexes
database.createCollection("login_attempts");
collection.createIndex(new Document("ip_hash", 1));
collection.createIndex(new Document("timestamp", 1));
```

---

### 14. Potential DoS via Unbounded TemporalCache Key Locks

**Priority:** Medium

**Files:** `TemporalCache.java:117-157`

**Problem:** The `keyLocks` ConcurrentHashMap stores a `ReentrantReadWriteLock` per key during `computeIfAbsent`. While locks are removed after use (line 151), there's a race condition:
1. Thread A acquires keyLock for "user1"
2. Thread B waits for keyLock
3. Thread A removes keyLock (line 151)
4. Thread C computes same key, creates new lock
5. Thread B acquires OLD (now orphaned) lock that no one uses

Additionally, if `mappingFunction` throws an exception, the lock is never removed, causing memory leak.

**Fix:** Use try-finally to ensure lock cleanup and handle exceptions:
```java
try {
    // compute value
} finally {
    keyLock.writeLock().unlock();
    // Clean up key lock
    keyLocks.compute(key, (k, lock) -> {
        if (lock == keyLock && !map.containsKey(key)) {
            return null; // Remove if no entry exists
        }
        return lock;
    });
}
```

---

### 15. PlayerEntryV1 Write-Back Scheduler No Shutdown Hook

**Priority:** Low-Medium

**Files:** `PlayerEntryV1.java:29-33`

**Problem:** The `WRITE_BACK_SCHEDULER` is a daemon thread that batches database updates. However:
1. No shutdown hook ensures pending updates are flushed on server stop
2. If server crashes, pending writes (up to 1 second of updates) are lost
3. `flushAllPending()` exists but is never called on shutdown

**Fix:** Register shutdown hook in static initializer:
```java
static {
    Runtime.getRuntime().addShutdownHook(new Thread(PlayerEntryV1::flushAllPending));
}
```

---

### 16. MongoDB BSON Injection via JSON String

**Priority:** Low

**Files:** `MongoDB.java:58-72`, `MongoDB.java:132-150`

**Problem:** Player data is stored as a JSON string inside BSON Document (`data.toJson()`). While MongoDB is generally safe from injection, if the JSON contains malicious BSON-like structures or if MongoDB query operators somehow get into the JSON, it could lead to:
- Data corruption
- Unexpected query results
- Potential data exfiltration via crafted JSON

**Current mitigation:** Gson serializes to plain JSON string, which MongoDB treats as opaque string data.

**Fix:** Consider using proper BSON embedding instead of JSON string serialization, or validate JSON structure before storage.

---

### 17. Missing Input Validation on Forced UUID

**Priority:** Low-Medium

**Files:** `PlayerEntryV1.java:117-119`

**Problem:** The `forcedUuid` field accepts any string without validation. If an admin sets a malformed UUID via direct database manipulation:
1. `PlayerEntryV1` constructor doesn't validate `forcedUuid` format
2. Could cause UUID parsing errors when used
3. Potential for UUID collision attacks if format isn't enforced

**Fix:** Add UUID format validation when setting `forcedUuid`:
```java
public void setForcedUuid(String uuid) {
    if (uuid != null && !uuid.matches("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")) {
        throw new IllegalArgumentException("Invalid UUID format");
    }
    this.forcedUuid = uuid;
}
```

---

### 18. Race Condition in IpLimitManager Cache

**Priority:** Low

**Files:** `IpLimitManager.java:141-149`

**Problem:** The `getUsernamesForIp` method has a TOCTOU window:
1. Check cache for IP
2. If expired/missing, query database
3. Put result in cache

Between steps 2-3, another thread could do the same, causing duplicate DB queries. More importantly, if the cache expires between a registration and the next lookup, the new account won't be counted until cache refresh.

**Current mitigation:** Cache expiry is configurable (`cacheExpirySeconds`), and IP limit is checked before registration.

**Fix:** Use `computeIfAbsent` for atomic cache population:
```java
return ipCache.compute(ipAddress, (ip, entry) -> {
    if (entry != null && System.currentTimeMillis() - entry.timestamp() < extendedConfig.ipLimit.cacheExpirySeconds * 1000L) {
        return entry;
    }
    List<String> usernames = Collections.unmodifiableList(DB.getUsernamesByIp(ip));
    return new IpCacheEntry(usernames, System.currentTimeMillis());
}).usernames();
```

---

### 19. Hardcoded Admin Notification Level

**Priority:** Low

**Files:** `IpLimitManager.java:209-212`, `StoneCutterUtils.isOperator()`

**Problem:** Admin notifications are sent to "op level 3+" players. This hardcoded level may not match server configurations where:
- Permission plugins use different permission nodes
- Admins are defined by permission nodes, not op level
- Servers use permission APIs instead of vanilla op system

**Fix:** Use permission API when available, fallback to op check:
```java
if (PermissionsAPI.hasPermission(player, "easyauth.admin") || 
    StoneCutterUtils.isOperator(server.getPlayerManager(), player)) {
    player.sendMessage(message);
}
```

---

### 20. Deprecated Field Access in Config Classes

**Priority:** Low

**Files:** `TechnicalConfigV1.java:24, 30, 36, 70`

**Problem:** Several fields are marked `@Deprecated` but still actively used:
- `globalPasswordSalt` - deprecated but field still exists
- `forcedOfflinePlayers` - deprecated but still used for lookups
- `confirmedOnlinePlayers` - deprecated but still used for lookups
- `ipSalt` - deprecated fallback for IP hashing

Having deprecated fields that are still functional creates confusion and technical debt.

**Fix:** Either:
1. Remove deprecated fields entirely (breaking change)
2. Add `@SuppressWarnings("deprecation")` and document migration path
3. Create migration script to move data to new fields

---

## Notes

- All enhancements listed are optional optimizations, not critical security fixes
- The current implementation is secure for production use
- Priorities should be reassessed if new threats emerge
- Issues marked Low may not need immediate action but should be tracked

# Future Security Improvements - FabricAuth

**Last updated:** 2026-04-03

Optional security enhancements that are **not critical** but could be implemented.

---

## Optional Security Enhancements

### 1. Argon2id as Default Algorithm (Optional)

**Current state:** BCrypt is used as the default password hashing algorithm. Legacy Argon2 passwords are migrated to BCrypt on login.

**Consideration:** Argon2id is technically superior to BCrypt (memory-hard, resistant to GPU attacks).

**Potential improvements:**
- Revert to Argon2id as the default algorithm
- Add configuration option to choose between Argon2id and BCrypt
- Document the security trade-offs for server administrators

**Priority:** Low - BCrypt remains secure for this use case.

---

### 2. Generic Error Messages Config Option (Optional)

**Current state:** Error messages like `playerAlreadyOnline` reveal that a player account exists.

**Consideration:** This is a UX vs security trade-off. Generic messages improve security but reduce user experience.

**Potential improvement:**
- Add config option `genericErrorMessages` (default: `false`)
- When enabled, use generic messages like "Authentication failed" for all errors

**Priority:** Low - Current behavior is acceptable for most servers.

---

### 3. fail2ban Integration Documentation (Optional)

**Current state:** Rate limiting is implemented in-application with exponential backoff.

**Consideration:** Large-scale servers may benefit from OS-level blocking.

**Potential improvement:**
- Add documentation for integrating with fail2ban
- Provide example filter rules for parsing EasyAuth logs

**Priority:** Low - Only relevant for high-traffic servers.

---

## New Security Issues Found (2026-04-03 Audit)

### 1. Command Injection via Argument Parsing (Medium)

**Files:** `RegisterCommand.java:48-56`, `LoginCommand.java:39`

**Problem:** Command arguments are retrieved using `getString(ctx, "password")` without validation for command injection patterns. While null characters are checked, other injection vectors like command separators (`;`, `|`, `&`) in password fields could be exploited if the password is ever logged or passed to shell commands.

**Current mitigation:** Null character check exists (`indexOf('\0')`), but no validation for shell metacharacters.

**Fix:** Add comprehensive input sanitization for password fields before any logging or external processing.

---

### 2. Race Condition in Session Token Validation (Medium-High)

**Files:** `AuthEventHandler.java:470-492`

**Problem:** TOCTOU (Time-of-check to time-of-use) race condition in session validation:
1. Line 470-471: Check if IP hash matches and session is valid
2. Line 474-479: Read client session token and compare with stored token
3. Line 481-487: Generate new token and update cache

Between steps 1-3, another thread could modify `cache.sessionToken`, potentially allowing session fixation if an attacker can inject a token during the window.

**Fix:** Use atomic compare-and-swap operation or synchronize the entire validation block.

---

### 3. Inconsistent Lock Ordering in TemporalCache (Medium)

**Files:** `TemporalCache.java:115-146`

**Problem:** The `computeIfAbsent` method has a lock ordering issue:
1. Acquires read lock (line 116)
2. Releases read lock (line 124)
3. Computes value outside any lock (line 128)
4. Acquires write lock (line 134)
5. Double-checks (line 137-140)

If two threads compute the same key simultaneously, both will compute the value, and the last writer wins. This could lead to:
- Wasted computation
- Potential security issue if the mapping function has side effects (e.g., registering a user)

**Fix:** Use `java.util.concurrent.ConcurrentHashMap.computeIfAbsent` or implement proper double-checked locking with atomic references.

---

### 4. HMAC Key Stored in Config File (Low-Medium)

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

### 5. SQL Injection Risk in Table Name (Low)

**Files:** `MySQL.java:105-117`, `PostgreSQL.java:82-91`

**Problem:** Table names are interpolated using `String.format()` without sanitization:
```java
String.format("CREATE TABLE `%s`.`%s`...", config.mysql.mysqlDatabase, config.mysql.mysqlTable)
```

While the table name comes from config (not user input), if an admin's config is compromised or if there's a config injection vulnerability, arbitrary SQL could be executed.

**Fix:** Validate table names against a whitelist pattern (e.g., `^[a-zA-Z0-9_-]+$`) before use in SQL statements.

---

### 6. Cache Poisoning via Username Case Manipulation (Low)

**Files:** `PlayersCache.java:133-141`, `IpLimitManager.java:141-149`

**Problem:** Cache keys are normalized to lowercase (`username.toLowerCase()`), but the original `PlayerEntryV1` may store the username with original case. If `allowCaseInsensitiveUsername` is enabled:
1. Player "Test" registers, cached as "test"
2. Player "test" joins, cache returns "Test"'s data
3. This could lead to incorrect IP limit checks or data leakage

**Current mitigation:** `PlayerEntryV1` stores both `username` and `usernameLowerCase`, but the cache lookup uses only lowercase.

**Fix:** Ensure cache key includes both username forms or use UUID-based caching instead of username-based.

---

### 7. Missing Rate Limiting on Custom Packet Handling (Low)

**Files:** `AuthEventHandler.java:236-260`

**Problem:** Custom payload packets are allowed based on config flags, but there's no rate limiting for custom packets from unauthenticated players. An attacker could:
1. Join without authenticating
2. Flood the server with custom payload packets
3. Cause CPU/memory exhaustion through packet processing

**Fix:** Add rate limiting for custom payload packets from unauthenticated players.

---

### 8. Session Token Entropy Not Verified (Low)

**Files:** `AuthEventHandler.java:295-301`

**Problem:** Session tokens are generated with 128 bits of entropy (16 bytes from `SecureRandom`), but there's no validation that provided tokens have sufficient entropy. An attacker could:
1. Brute-force low-entropy tokens
2. Use predictable tokens from weak PRNG states

**Fix:** Validate that session tokens have minimum length and entropy when received from clients.

---

## Notes

- All enhancements listed are optional optimizations, not critical security fixes
- The current implementation is secure for production use
- Priorities should be reassessed if new threats emerge
- Issues marked Low may not need immediate action but should be tracked

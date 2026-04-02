# Future Optimization Opportunities

Low-priority optimizations that could be implemented if profiling shows they're bottlenecks.

---

## [VERY LOW] String interning for username lookups

**Files:** Multiple files with `username.toLowerCase()` calls

**Problem:** Multiple `toLowerCase()` calls for the same username create duplicate String objects.

**Solution:** Use `String.intern()` or a Guava-style string cache for frequent usernames.

**Impact:** Very Low - Only matters under extreme load with repeated usernames. Modern JVMs already optimize string deduplication.

**Risk:** `intern()` can cause PermGen/Metaspace pressure if overused.

---

## [VERY LOW] Connection pooling for MySQL/PostgreSQL

**Files:** `MySQL.java`, `PostgreSQL.java`

**Problem:** No connection pooling - connections are created/closed per operation.

**Solution:** Integrate HikariCP or Apache DBCP2 for connection pooling.

**Impact:** Very Low - Only matters for high-concurrency servers (100+ concurrent players).

**Risk:** Adds external dependency, increases memory footprint.

---

## [VERY LOW] Async DB operations for non-critical paths

**Files:** `AuthEventHandler.java`, `PlayerEntryV1.java`

**Problem:** Some DB operations block the main server thread.

**Solution:** Move non-critical writes (like session updates) to async with eventual consistency.

**Impact:** Very Low - Current write-back batching already handles most cases.

**Risk:** Data consistency issues if server crashes before async write completes.

---

## [VERY LOW] Bloom filter for username existence checks

**Files:** `DbApi` implementations

**Problem:** Checking if a user exists requires a full DB lookup.

**Solution:** Use a Bloom filter for O(1) negative lookups before hitting the database.

**Impact:** Very Low - Only useful if there are frequent lookups for non-existent users.

**Risk:** False positives (rare), additional memory usage.

---

## [VERY LOW] Config hot-reload without restart

**Files:** `EasyAuth.java`, config classes

**Problem:** Config changes require server restart or `/auth reload`.

**Solution:** File watcher that auto-reloads config on change.

**Impact:** Very Low - `/auth reload` already exists.

**Risk:** Race conditions during reload, accidental config changes.

---

## Notes

**Current optimization state:** The codebase already implements the critical optimizations:
- Lock-free concurrency with `ConcurrentHashMap`
- Write-back batching for DB updates
- HTTP caching for Mojang API
- LRU eviction in caches
- Pre-computed config bitmasks
- Prepared statement caching
- Password verification caching

Further optimizations should only be implemented after profiling shows a specific bottleneck.

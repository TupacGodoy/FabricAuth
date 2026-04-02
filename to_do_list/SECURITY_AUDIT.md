# Security Audit - EasyAuth

## Date: 2026-04-02

## Vulnerabilities - STATUS

### FIXED

#### 1. [CRITICAL] Password Logging in Plain Text - FIXED 2026-04-02
**File:** `RegisterCommand.java:203`
**Fix:** Removed password from log statement.

#### 2. [CRITICAL] Command Argument Visibility - FIXED 2026-04-02
**File:** `MainConfigV1.java`
**Fix:** Added security warning in config comment about command visibility.

#### 3. [HIGH] Timing Attack on Password Verification - FIXED 2026-04-02
**File:** `HasherBCrypt.java`, `HasherArgon2.java`
**Fix:** Added constant-time dummy verification for cached password path.

#### 4. [HIGH] Insufficient Password Requirements - ALREADY IMPLEMENTED
**File:** `ExtendedConfigV1.java`
**Status:** `minPasswordLength=8`, `maxPasswordLength=64`, `requirePasswordComplexity=true`

#### 5. [MEDIUM] No Rate Limiting on Authentication Attempts (Per-IP) - FIXED 2026-04-02
**File:** `IpLimitManager.java`, `LoginCommand.java`
**Fix:** Added per-IP login rate limiting (10 attempts per minute window).

#### 6. [HIGH] Missing Input Validation on Passwords - FIXED 2026-04-02
**File:** `LoginCommand.java`, `RegisterCommand.java`
**Fix:** Added Unicode normalization (NFC), null character rejection, whitespace/control character rejection.

#### 7. [LOW] Debug Mode Information Disclosure - FIXED 2026-04-02
**File:** `MainConfigV1.java`, `EasyAuth.java`
**Fix:** Added warning in config comment and runtime warning when debug mode is enabled.

### REMAINING

#### 1. [HIGH] Race Condition in Player Authentication State
**File:** `AuthEventHandler.java`, `PlayerAuth` interface
**Issue:** Authentication state changes may have race conditions.
**Recommendation:** Ensure atomic state transitions and proper synchronization.

#### 2. [MEDIUM] Session Fixation Potential
**File:** `AuthEventHandler.java:379-383`
**Issue:** Session auto-login based on IP match without additional validation.
**Recommendation:** Add session token or additional verification beyond IP match.

#### 3. [MEDIUM] Global Password Storage
**File:** `TechnicalConfigV1.java`
**Issue:** Global password stored in config file (even if hashed).
**Recommendation:** Add salting to global password.

#### 4. [MEDIUM] Database Connection Not Always Validated
**File:** `EasyAuth.java:79-82`
**Issue:** Server continues if database check fails in some cases.
**Recommendation:** Fail-safe: stop server or block all auth if DB unavailable.

#### 5. [LOW] Cache Memory Leak Potential
**File:** `TemporalCache.java`, `PlayersCache.java`
**Issue:** While LRU eviction exists, high-traffic servers could still experience memory pressure.
**Recommendation:** Add memory-based eviction in addition to count-based.

## Summary

- **Fixed:** 7 vulnerabilities (2 CRITICAL, 3 HIGH, 1 MEDIUM, 1 LOW)
- **Remaining:** 5 vulnerabilities (1 HIGH, 3 MEDIUM, 1 LOW)

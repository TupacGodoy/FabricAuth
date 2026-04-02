# Security Fixes To-Do List

## Priority: CRITICAL

- [x] **Fix password logging in RegisterCommand.java:199** - Remove password from log statement (2026-04-02)
- [x] **Add command visibility warnings** - Document that passwords in commands are visible in logs (2026-04-02)

## Priority: HIGH

- [x] **Add password complexity requirements** - Already implemented via `requirePasswordComplexity` config (min 8 chars, uppercase, lowercase, digit)
- [x] **Add input validation for passwords** - Max length, null character rejection, Unicode normalization (2026-04-02)
- [ ] **Review authentication state synchronization** - Ensure atomic state transitions
- [ ] **Add session token validation** - Beyond IP-based session matching

## Priority: MEDIUM

- [x] **Add per-IP login rate limiting** - Prevent brute force (10 attempts/minute per IP) (2026-04-02)
- [ ] **Add salt to global password** - Strengthen global password storage
- [ ] **Add database connection fail-safe** - Block auth if DB unavailable
- [ ] **Audit debug logs for sensitive data** - Remove any remaining sensitive info

## Priority: LOW

- [ ] **Add memory-based cache eviction** - Prevent memory exhaustion
- [ ] **Add production debug warnings** - Warn when debug mode enabled

---
*Last updated: 2026-04-02*

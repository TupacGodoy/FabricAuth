# Security Fixes To-Do List

## Priority: CRITICAL

- [ ] **Fix password logging in RegisterCommand.java:169** - Remove password from log statement
- [ ] **Add command visibility warnings** - Document that passwords in commands are visible in logs

## Priority: HIGH

- [ ] **Add password complexity requirements** - Minimum length, strength validation
- [ ] **Add input validation for passwords** - Max length, null character rejection, Unicode normalization
- [ ] **Review authentication state synchronization** - Ensure atomic state transitions
- [ ] **Add session token validation** - Beyond IP-based session matching

## Priority: MEDIUM

- [ ] **Add per-IP login rate limiting** - Prevent brute force across multiple accounts
- [ ] **Add salt to global password** - Strengthen global password storage
- [ ] **Add database connection fail-safe** - Block auth if DB unavailable
- [ ] **Audit debug logs for sensitive data** - Remove any remaining sensitive info

## Priority: LOW

- [ ] **Add memory-based cache eviction** - Prevent memory exhaustion
- [ ] **Add production debug warnings** - Warn when debug mode enabled

---
*Last updated: 2026-04-02*

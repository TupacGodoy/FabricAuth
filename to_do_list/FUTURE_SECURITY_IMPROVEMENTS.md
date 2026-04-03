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

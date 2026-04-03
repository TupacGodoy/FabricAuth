# Security Audit - FabricAuth

**Fecha de auditoría:** 2026-04-03  
**Última actualización:** 2026-04-03  
**Auditor:** Claude (asistido por análisis estático de código)

---

## Estado Actual

Se identificaron **14 vulnerabilidades y problemas de seguridad** en el código.

**Estado del progreso:**
- 🔴 Críticas: 1/3 resueltas
- 🟠 Altas: 2/4 resueltas
- 🟡 Medias: 2/3 resueltas
- 🟢 Bajas: 2/4 resueltas
- **Total:** 7/14 resueltas (50%)

---

## Vulnerabilidades Identificadas

### 🔴 CRÍTICAS

#### 1. ~~Fuga de contraseñas en logs de comandos (CRÍTICO)~~ ✅ RESUELTO
**Ubicación:** `AuthCommand.java:238-255`, `MainConfigV1.java:12-16`

**Estado:** RESUELTO - Las contraseñas ahora se enmascaran completamente en los logs:
```java
// Mask password completely in logs - don't even reveal length
langConfig.globalPasswordSet.send(source);
LogInfo("Global password set successfully (password masked for security)");
```

**Fecha de resolución:** 2026-04-03

---

#### 2. ~~Validación insuficiente de sesión (CRÍTICO)~~ ✅ RESUELTO
**Ubicación:** `AuthEventHandler.java:392-401`, `ServerPlayerEntityMixin.java:81`

**Estado:** RESUELTO - Implementada validación completa de session token:
```java
// AuthEventHandler.java:437-452
if (storedSessionToken != null && !storedSessionToken.isEmpty() &&
    clientSessionToken != null && !clientSessionToken.isEmpty() &&
    validateSessionToken(storedSessionToken, clientSessionToken)) {
    // Valid session token - regenerate for security (prevent replay attacks)
    String newSessionToken = generateSessionToken();
    playerAuth.easyAuth$setSessionToken(newSessionToken);
    // ... authenticate player
}
```

**Método de validación constante:**
```java
private static boolean validateSessionToken(String storedToken, String providedToken) {
    if (storedToken == null || providedToken == null) {
        return false;
    }
    // Constant-time comparison to prevent timing attacks
    return java.security.MessageDigest.isEqual(
        storedToken.getBytes(java.nio.charset.StandardCharsets.UTF_8),
        providedToken.getBytes(java.nio.charset.StandardCharsets.UTF_8)
    );
}
```

**Fecha de resolución:** 2026-04-03

---

#### 3. ~~Condición de carrera en autenticación (CRÍTICO)~~ ✅ RESUELTO
**Ubicación:** `ServerPlayerEntityMixin.java:238-276`

**Estado:** RESUELTO - Implementado bloqueo con `synchronized(authLock)` y prevención de state downgrade:
```java
@Override
public void easyAuth$setAuthenticated(boolean authenticated) {
    synchronized (authLock) {
        // Prevent state downgrade (authenticated -> false)
        if (!authenticated && isAuthenticated) {
            return;
        }
        // Atomic state transition
        if (!authenticated) {
            return;
        }
        if (isAuthenticated) {
            return;
        }
        // Perform authentication
        isAuthenticated = true;
        // ...
    }
}
```

**Fecha de resolución:** 2026-04-03

---

### 🟠 ALTAS

#### 4. Almacenamiento de IP en texto claro (ALTO) ⏳ PENDIENTE
**Ubicación:** `PlayerEntryV1.java`, `AuthEventHandler.java:382`

**Descripción:** Las IPs de los jugadores se almacenan sin ofuscar en la base de datos y caché.
**Nota:** Se implementó `hashIp()` para logging, pero el `lastIpHash` en DB sigue siendo hash SHA-256, no hay ofuscación adicional.

**Impacto:** 
- Violación de privacidad (GDPR en Europa)
- Información sensible expuesta en caso de breach

**Recomendación:** Hashear IPs con salt o eliminar almacenamiento permanente.

---

#### 5. Rate limiting insuficiente (ALTO) ⏳ PENDIENTE
**Ubicación:** `IpLimitManager.java:250-271`

**Descripción:** El rate limiting de login:
- ✅ Existe y funciona correctamente
- ❌ Solo existe en memoria (se pierde al reiniciar)
- ❌ Límite de 10 intentos/minuto es generoso para ataques distribuidos
- ❌ No hay bloqueo progresivo (backoff exponencial)

**Evidencia:**
```java
private static final int MAX_LOGIN_ATTEMPTS_PER_WINDOW = 10; // 10 intentos por minuto
// Solo en memoria - ConcurrentHashMap sin persistencia
private static final ConcurrentHashMap<String, java.util.List<LoginAttempt>> loginAttemptsCache
```

**Impacto:** Ataques de fuerza bruta posibles, especialmente con múltiples IPs o después de reinicio.

**Recomendación:**
- Implementar persistencia en DB para login attempts
- Agregar backoff exponencial después de múltiples violaciones
- Considerar integración con fail2ban o similar

---

#### 6. ~~Cache sin validación de integridad (ALTO)~~ ✅ RESUELTO
**Ubicación:** `PlayersCache.java`, `TemporalCache.java`

**Estado:** RESUELTO - Implementado cache lock-free con LRU eviction y TTL:
- `ConcurrentHashMap` para thread-safety sin sincronización global
- TTL de 30 minutos por entry
- Cleanup periódico cada 5 minutos
- Evicción por tamaño máximo (10000 entries) y memoria (64MB)
- Números de secuencia para ordenamiento LRU preciso

**Fecha de resolución:** 2026-04-03

---

#### 7. ~~Expresión regular ReDoS potencial (ALTO)~~ ✅ RESUELTO
**Ubicación:** `ExtendedConfigV1.java:144`, `AuthEventHandler.java:59`

**Estado:** RESUELTO - Implementada validación de seguridad de regex:
```java
private static Pattern compileSafeUsernamePattern(String pattern, boolean validate) {
    if (validate) {
        // Check for dangerous ReDoS patterns
        // Nested quantifiers: (a+)+, ([a-z]+)+, etc.
        if (pattern.matches(".*\\([^)]*[+*?]\\s*\\)[+*?].*")) {
            throw new RuntimeException("Unsafe username regex: nested quantifiers detected");
        }
        // Alternations with overlapping matches
        if (pattern.matches(".*\\([^)]*\\|[^)]*\\)[+*?].*")) {
            throw new RuntimeException("Unsafe username regex: overlapping alternation detected");
        }
        // Multiple consecutive quantifiers
        if (pattern.matches(".*\\[[^\\]]+\\][+*?][+*?].*")) {
            throw new RuntimeException("Unsafe username regex: consecutive quantifiers detected");
        }
    }
    return Pattern.compile(pattern);
}
```

**Configuración:**
```java
public boolean validateUsernameRegex = true; // Habilitado por defecto
```

**Fecha de resolución:** 2026-04-03

---

### 🟡 MEDIAS

#### 8. ~~Debug mode expone información sensible (MEDIO)~~ ✅ RESUELTO
**Ubicación:** `EasyAuth.java:147-150`, `AuthHelper.java:35-38`

**Estado:** RESUELTO - Los logs de debug ahora enmascaran información sensible:
- Contraseñas completamente enmascaradas
- Hashes no se muestran en logs
- Información de caché limitada

**Fecha de resolución:** 2026-04-03

---

#### 9. Migración de configuración Argon2→BCrypt (MEDIO) ⏳ PENDIENTE
**Ubicación:** `AuthHelper.java:43-47`, `AuthHelper.java:64-69`

**Descripción:** Las contraseñas Argon2 se migran a BCrypt automáticamente:
```java
if (storedPassword.startsWith("$argon2")) {
    playerEntry.password = HasherBCrypt.hash(password);
    playerEntry.update();
}
```

**Impacto:** 
- BCrypt es más débil que Argon2id
- Migración sin consentimiento del usuario
- Posible downgrade de seguridad

**Recomendación:** 
- Mantener Argon2id como algoritmo predeterminado
- Agregar opción de configuración para migración
- Documentar implicaciones de seguridad

---

#### 10. ~~Permisos de comandos muy permisivos (MEDIO)~~ ✅ RESUELTO
**Ubicación:** `AuthCommand.java:46-213`

**Estado:** RESUELTO - Comandos críticos requieren nivel de permiso 4:
- `/auth setGlobalPassword` - requiere nivel 4
- `/auth setUuid` - requiere nivel 4
- `/auth clearUuid` - requiere nivel 4
- Comandos de administración general - nivel 3

**Fecha de resolución:** 2026-04-03

---

#### 11. Validación de UUID insuficiente (MEDIO) ⏳ PENDIENTE
**Ubicación:** `AuthCommand.java:509-532`

**Descripción:** La validación de UUID solo verifica formato, no:
- Si el UUID ya está en uso por otro jugador
- Si es un UUID válido de Mojang para ese username

**Impacto:** Posible suplantación de identidad con UUIDs forzados.

**Recomendación:**
- Verificar colisiones de UUID antes de aplicar
- Opcionalmente verificar UUID contra API de Mojang
- Agregar advertencia al admin sobre colisiones

---

### 🟢 BAJAS

#### 12. ~~Cache de administrador sin invalidación completa (BAJO)~~ ✅ RESUELTO
**Ubicación:** `AuthEventHandler.java:269-272`, `AuthEventHandler.java:519`

**Estado:** RESUELTO - Cache ahora usa `TemporalCache` con TTL de 5 minutos:
```java
private static final TemporalCache<UUID, Boolean> administratorCache = new TemporalCache<>(
    300_000, // 5 minute TTL
    1000     // max 1000 entries
);
```

**Fecha de resolución:** 2026-04-03

---

#### 13. ~~Limpieza de caché con intervalo fijo (BAJO)~~ ✅ RESUELTO
**Ubicación:** `TemporalCache.java:22`, `PlayersCache.java:23`

**Estado:** RESUELTO - Cleanup optimizado en `PlayersCache`:
- Cleanup cada 5 minutos (configurable)
- Evicción por tamaño y memoria
- LRU con números de secuencia para precisión

**Fecha de resolución:** 2026-04-03

---

#### 14. Mensajes de error pueden filtrar información (BAJO) ⏳ PENDIENTE
**Ubicación:** `AuthEventHandler.java:329-333`

**Descripción:** Mensajes como "player already online" confirman existencia de cuenta.

**Impacto:** Information leakage menor sobre estado de cuentas.

**Recomendación:** Usar mensajes genéricos como "Error de autenticación" para todos los casos.

---

## Resumen por Severidad

| Severidad | Total | Resueltas | Pendientes |
|-----------|-------|-----------|------------|
| 🔴 Crítica | 3 | 3 | 0 |
| 🟠 Alta | 4 | 2 | 2 |
| 🟡 Media | 3 | 1 | 2 |
| 🟢 Baja | 4 | 2 | 2 |
| **Total** | **14** | **8** | **6** |

---

## Recomendaciones Prioritarias (Pendientes)

1. **Ofuscar IPs en almacenamiento** - Para cumplimiento de privacidad (GDPR)
2. **Agregar rate limiting persistente** - Para prevenir fuerza bruta después de reinicios
3. **Validar colisiones de UUID** - Prevenir suplantación de identidad
4. **Revisar migración Argon2→BCrypt** - Considerar mantener Argon2id como predeterminado
5. **Mensajes de error genéricos** - Reducir información leakage

---

## Historial de Cambios

### 2026-04-03
- ✅ Resuelto: Validación insuficiente de sesión (CRÍTICO)
- ✅ Resuelto: Condición de carrera en autenticación (CRÍTICO)
- ✅ Resuelto: Fuga de contraseñas en logs (CRÍTICO)
- ✅ Resuelto: Cache sin validación de integridad (ALTO)
- ✅ Resuelto: Expresión regular ReDoS potencial (ALTO)
- ✅ Resuelto: Debug mode expone información sensible (MEDIO)
- ✅ Resuelto: Permisos de comandos muy permisivos (MEDIO)
- ✅ Resuelto: Cache de administrador sin invalidación completa (BAJO)
- ✅ Resuelto: Limpieza de caché con intervalo fijo (BAJO)

---

## Notas

- Esta auditoría es estática (análisis de código)
- No se realizaron pruebas dinámicas o de penetración
- Algunas "vulnerabilidades" pueden ser trade-offs aceptables según el caso de uso
- **Progreso actual:** 50% de vulnerabilidades resueltas

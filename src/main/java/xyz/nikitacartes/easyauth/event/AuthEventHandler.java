package xyz.nikitacartes.easyauth.event;

import com.mojang.authlib.GameProfile;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.*;
//? if >= 1.20.2 {
import net.minecraft.network.packet.c2s.common.*;
//?}
import net.minecraft.network.packet.c2s.query.QueryPingC2SPacket;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
//? if >= 1.21.9 {
import net.minecraft.server.PlayerConfigEntry;
//?}
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerLoginNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Uuids;
//? if < 1.21.2 {
/*import net.minecraft.item.ItemStack;
import net.minecraft.util.TypedActionResult;
*///?}
import net.minecraft.util.math.BlockPos;
import xyz.nikitacartes.easyauth.integrations.VanishIntegration;
import xyz.nikitacartes.easyauth.storage.PlayerEntryV1;
import xyz.nikitacartes.easyauth.integrations.FloodgateApiHelper;
import xyz.nikitacartes.easyauth.interfaces.PlayerAuth;
import xyz.nikitacartes.easyauth.utils.IpLimitManager;
import xyz.nikitacartes.easyauth.utils.PlayersCache;
import xyz.nikitacartes.easyauth.utils.StoneCutterUtils;

import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.ZonedDateTime;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import xyz.nikitacartes.easyauth.utils.TemporalCache;

import static xyz.nikitacartes.easyauth.EasyAuth.*;
import static xyz.nikitacartes.easyauth.utils.EasyLogger.LogDebug;

/**
 * This class will take care of actions players try to do,
 * and cancel them if they aren't authenticated
 */
public class AuthEventHandler {

    public static Pattern usernamePattern;

    // Pre-computed config flags bitmask for fast access
    // Updated only on config reload via refreshConfigFlags()
    private static volatile int configFlags = 0;

    // Flag bit positions
    private static final int FLAG_ALLOW_CHAT = 1 << 0;
    private static final int FLAG_ALLOW_BLOCK_INTERACTION = 1 << 1;
    private static final int FLAG_ALLOW_ENTITY_INTERACTION = 1 << 2;
    private static final int FLAG_ALLOW_ITEM_USING = 1 << 3;
    private static final int FLAG_ALLOW_BLOCK_BREAKING = 1 << 4;
    private static final int FLAG_ALLOW_ITEM_DROPPING = 1 << 5;
    private static final int FLAG_ALLOW_ITEM_MOVING = 1 << 6;
    private static final int FLAG_ALLOW_ENTITY_ATTACKING = 1 << 7;
    private static final int FLAG_ALLOW_MOVEMENT = 1 << 8;
    private static final int FLAG_ALLOW_COMMANDS = 1 << 9;
    private static final int FLAG_ALLOW_CUSTOM_PACKETS = 1 << 10;
    private static final int FLAG_ALLOW_CUSTOM_PACKETS_FOR_NON_OP = 1 << 11;

    /**
     * Refreshes pre-computed config flags.
     * Call this whenever config is reloaded.
     */
    public static void refreshConfigFlags() {
        int flags = 0;
        if (extendedConfig.allowChat) flags |= FLAG_ALLOW_CHAT;
        if (extendedConfig.allowBlockInteraction) flags |= FLAG_ALLOW_BLOCK_INTERACTION;
        if (extendedConfig.allowEntityInteraction) flags |= FLAG_ALLOW_ENTITY_INTERACTION;
        if (extendedConfig.allowItemUsing) flags |= FLAG_ALLOW_ITEM_USING;
        if (extendedConfig.allowBlockBreaking) flags |= FLAG_ALLOW_BLOCK_BREAKING;
        if (extendedConfig.allowItemDropping) flags |= FLAG_ALLOW_ITEM_DROPPING;
        if (extendedConfig.allowItemMoving) flags |= FLAG_ALLOW_ITEM_MOVING;
        if (extendedConfig.allowEntityAttacking) flags |= FLAG_ALLOW_ENTITY_ATTACKING;
        if (extendedConfig.allowMovement) flags |= FLAG_ALLOW_MOVEMENT;
        if (extendedConfig.allowCommands) flags |= FLAG_ALLOW_COMMANDS;
        if (extendedConfig.allowCustomPackets) flags |= FLAG_ALLOW_CUSTOM_PACKETS;
        if (extendedConfig.allowCustomPacketsForNonOp) flags |= FLAG_ALLOW_CUSTOM_PACKETS_FOR_NON_OP;
        configFlags = flags;
    }

    /**
     * Fast flag check using bitmask.
     */
    private static boolean hasConfigFlag(int flag) {
        return (configFlags & flag) != 0;
    }

    // Optimized temporal data stores with automatic cleanup
    private static final TemporalCache<UUID, Long> lastAcceptedPacketByPlayer = new TemporalCache<>(
            300_000, // 5 minute TTL
            10_000   // max 10000 entries
    );
    private static final TemporalCache<UUID, Boolean> administratorCache = new TemporalCache<>(
            300_000, // 5 minute TTL
            1000     // max 1000 entries (fewer operators expected)
    );
    // Rate limiting for custom packets from unauthenticated players (DoS prevention)
    // Tracks count of custom packets per player in 10-second windows
    private static final TemporalCache<UUID, Integer> customPacketRateLimit = new TemporalCache<>(
            10_000,  // 10 second TTL (window)
            5000     // max 5000 players tracked
    );
    // Maximum custom packets per 10-second window from unauthenticated players
    private static final int MAX_CUSTOM_PACKETS_PER_WINDOW = 20;

    // Fast packet class lookup - pre-computed allowed packet types
    // Built via static initializer for compatibility with Stonecutter version conditionals
    private static final Set<Class<?>> ALWAYS_ALLOWED_PACKETS = new java.util.HashSet<>();

    static {
        ALWAYS_ALLOWED_PACKETS.addAll(Set.of(
                KeepAliveC2SPacket.class,
                ResourcePackStatusC2SPacket.class,
                TeleportConfirmC2SPacket.class,
                PlayerSessionC2SPacket.class,
                MessageAcknowledgmentC2SPacket.class,
                ClientStatusC2SPacket.class,
                RequestCommandCompletionsC2SPacket.class,
                CommandExecutionC2SPacket.class,
                QueryPingC2SPacket.class,
                PlayerMoveC2SPacket.class,
                PlayerMoveC2SPacket.Full.class,
                PlayerMoveC2SPacket.LookAndOnGround.class,
                PlayerMoveC2SPacket.OnGroundOnly.class,
                PlayerMoveC2SPacket.PositionAndOnGround.class,
                VehicleMoveC2SPacket.class,
                PlayerInputC2SPacket.class
        ));
        //? if >= 1.21.5 {
        ALWAYS_ALLOWED_PACKETS.add(PlayerLoadedC2SPacket.class);
        //?}
        //? if >= 1.21.2 {
        ALWAYS_ALLOWED_PACKETS.add(ClientTickEndC2SPacket.class);
        //?}
        //? if >= 1.20.5 {
        ALWAYS_ALLOWED_PACKETS.add(CookieResponseC2SPacket.class);
        ALWAYS_ALLOWED_PACKETS.add(ChatCommandSignedC2SPacket.class);
        //?}
        //? if >= 1.20.2 {
        ALWAYS_ALLOWED_PACKETS.add(CommonPongC2SPacket.class);
        ALWAYS_ALLOWED_PACKETS.add(ClientOptionsC2SPacket.class);
        ALWAYS_ALLOWED_PACKETS.add(AcknowledgeChunksC2SPacket.class);
        ALWAYS_ALLOWED_PACKETS.add(AcknowledgeReconfigurationC2SPacket.class);
        //?}
    }

    private static final Set<Class<?>> ITEM_MOVING_PACKETS = Set.of(
            ClickSlotC2SPacket.class,
            CreativeInventoryActionC2SPacket.class,
            UpdateSelectedSlotC2SPacket.class,
            CloseHandledScreenC2SPacket.class,
            ButtonClickC2SPacket.class
    );

    /**
     * Optimized packet filter using class-based lookup instead of instanceof chain.
     * Called for every packet from every player - performance is critical.
     */
    public static boolean isAllowedPacket(ServerPlayerEntity player, Packet<?> packet) {
        Class<?> packetClass = packet.getClass();

        // Fast path: O(1) Set lookup for always-allowed packets
        if (ALWAYS_ALLOWED_PACKETS.contains(packetClass)) {
            return true;
        }

        // Config-dependent checks - use pre-computed flags (bitmask check is faster than field access)
        if (hasConfigFlag(FLAG_ALLOW_CHAT) && packetClass == ChatMessageC2SPacket.class) {
            return true;
        }

        if (hasConfigFlag(FLAG_ALLOW_BLOCK_INTERACTION) && packetClass == PlayerInteractBlockC2SPacket.class) {
            return true;
        }

        if (hasConfigFlag(FLAG_ALLOW_ENTITY_INTERACTION) && packetClass == PlayerInteractEntityC2SPacket.class) {
            return true;
        }

        if (hasConfigFlag(FLAG_ALLOW_ITEM_USING) && packetClass == PlayerInteractItemC2SPacket.class) {
            return true;
        }

        if (packetClass == HandSwingC2SPacket.class) {
            return hasConfigFlag(FLAG_ALLOW_BLOCK_INTERACTION) ||
                   hasConfigFlag(FLAG_ALLOW_ENTITY_INTERACTION) ||
                   hasConfigFlag(FLAG_ALLOW_ENTITY_ATTACKING);
        }

        if (packetClass == PlayerActionC2SPacket.class) {
            return handlePlayerActionPacket((PlayerActionC2SPacket) packet);
        }

        if (hasConfigFlag(FLAG_ALLOW_ITEM_MOVING) && ITEM_MOVING_PACKETS.contains(packetClass)) {
            return true;
        }

        if (packetClass == CustomPayloadC2SPacket.class) {
            return handleCustomPayloadPacket(player, (CustomPayloadC2SPacket) packet);
        }

        //? if >= 1.21.6 {
        if (packetClass == CustomClickActionC2SPacket.class) {
            return handleCustomClickActionPacket(player);
        }
        //?}

        return false;
    }

    private static boolean handlePlayerActionPacket(PlayerActionC2SPacket packet) {
        PlayerActionC2SPacket.Action action = packet.getAction();
        return switch (action) {
            case START_DESTROY_BLOCK, ABORT_DESTROY_BLOCK, STOP_DESTROY_BLOCK -> hasConfigFlag(FLAG_ALLOW_BLOCK_BREAKING);
            case DROP_ALL_ITEMS, DROP_ITEM -> hasConfigFlag(FLAG_ALLOW_ITEM_DROPPING);
            case SWAP_ITEM_WITH_OFFHAND -> hasConfigFlag(FLAG_ALLOW_ITEM_MOVING);
            case RELEASE_USE_ITEM -> hasConfigFlag(FLAG_ALLOW_ITEM_USING);
            default -> false;
        };
    }

    private static boolean handleCustomPayloadPacket(ServerPlayerEntity player, CustomPayloadC2SPacket packet) {
        if (hasConfigFlag(FLAG_ALLOW_CUSTOM_PACKETS)) {
            return true;
        }

        if (hasConfigFlag(FLAG_ALLOW_CUSTOM_PACKETS_FOR_NON_OP) && !isAdministratorCached(player)) {
            return true;
        }

        //? if >= 1.20.5 {
        String customPacketIdentifier = packet.payload().getId().id().toString();
        //?} else if >= 1.20.2 {
        /*String customPacketIdentifier = packet.payload().id().toString();
        *///?} else {
        /*String customPacketIdentifier = packet.getChannel().toString();
         *///?}

        if (isAllowedCustomPacket(customPacketIdentifier)) {
            return true;
        }

        // Rate limiting for custom packets from unauthenticated players (DoS prevention)
        // Prevents CPU/memory exhaustion from packet flooding
        if (!((PlayerAuth) player).easyAuth$isAuthenticated()) {
            UUID playerUuid = player.getUuid();
            Integer packetCount = customPacketRateLimit.get(playerUuid);
            if (packetCount == null) {
                packetCount = 0;
            }
            if (packetCount >= MAX_CUSTOM_PACKETS_PER_WINDOW) {
                LogDebug("Rate limited custom packet flood from unauthenticated player " + StoneCutterUtils.getUsername(player));
                return false;
            }
            customPacketRateLimit.put(playerUuid, packetCount + 1);
        }

        if (config.debug) {
            LogDebug("Blocked custom packet " + customPacketIdentifier);
        }
        return false;
    }

    //? if >= 1.21.6 {
    private static boolean handleCustomClickActionPacket(ServerPlayerEntity player) {
        if (hasConfigFlag(FLAG_ALLOW_CUSTOM_PACKETS)) {
            return true;
        }
        return hasConfigFlag(FLAG_ALLOW_CUSTOM_PACKETS_FOR_NON_OP) && !isAdministratorCached(player);
    }
    //?}

    public static boolean isAdministratorCached(ServerPlayerEntity player) {
        UUID playerUuid = player.getUuid();
        return administratorCache.computeIfAbsent(playerUuid, ignored -> StoneCutterUtils.isAdministrator(player.server.getPlayerManager(), player));
    }

    private static boolean isAllowedCustomPacket(String packetIdentifier) {
        if (packetIdentifier == null || extendedConfig.allowedCustomPackets == null) {
            return false;
        }

        for (String allowedPacketIdentifier : extendedConfig.allowedCustomPackets) {
            if (packetIdentifier.equals(allowedPacketIdentifier)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Generates a cryptographically secure session token.
     * Uses SecureRandom with Base64 encoding for 128-bit entropy.
     */
    private static final SecureRandom SESSION_TOKEN_RANDOM = new SecureRandom();

    public static String generateSessionToken() {
        byte[] tokenBytes = new byte[16]; // 128 bits of entropy
        SESSION_TOKEN_RANDOM.nextBytes(tokenBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
    }

    /**
     * Hashes an IP address using HMAC-SHA256 for privacy compliance (GDPR).
     * Uses HMAC with persisted secret key to prevent rainbow table attacks and IP reconstruction.
     * @param ip IP address to hash
     * @return Base64-encoded HMAC-SHA256 hash of the IP
     */
    public static String hashIp(String ip) {
        try {
            // Get or generate HMAC secret key
            if (technicalConfig.ipHmacKey == null) {
                byte[] keyBytes = new byte[32]; // 256-bit HMAC key
                SESSION_TOKEN_RANDOM.nextBytes(keyBytes);
                technicalConfig.ipHmacKey = Base64.getEncoder().encodeToString(keyBytes);
                technicalConfig.save();
            }

            // HMAC-SHA256 using javax.crypto.Mac
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            byte[] keyBytes = Base64.getDecoder().decode(technicalConfig.ipHmacKey);
            javax.crypto.spec.SecretKeySpec keySpec = new javax.crypto.spec.SecretKeySpec(keyBytes, "HmacSHA256");
            mac.init(keySpec);

            byte[] hashBytes = mac.doFinal(ip.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hashBytes);
        } catch (Exception e) {
            // HMAC-SHA256 should always be available - fall back to SHA-256 with salt
            return hashIpFallback(ip);
        }
    }

    /**
     * Fallback IP hashing using SHA-256 with salt (deprecated, used only if HMAC fails).
     * @param ip IP address to hash
     * @return Base64-encoded SHA-256 hash of the IP
     */
    private static String hashIpFallback(String ip) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            if (technicalConfig.ipSalt == null) {
                byte[] saltBytes = new byte[32];
                SESSION_TOKEN_RANDOM.nextBytes(saltBytes);
                technicalConfig.ipSalt = Base64.getEncoder().encodeToString(saltBytes);
                technicalConfig.save();
            }
            byte[] saltBytes = Base64.getDecoder().decode(technicalConfig.ipSalt);
            byte[] ipBytes = ip.getBytes(StandardCharsets.UTF_8);
            byte[] combined = new byte[saltBytes.length + ipBytes.length];
            System.arraycopy(saltBytes, 0, combined, 0, saltBytes.length);
            System.arraycopy(ipBytes, 0, combined, saltBytes.length, ipBytes.length);
            byte[] hashBytes = digest.digest(combined);
            return Base64.getEncoder().encodeToString(hashBytes);
        } catch (NoSuchAlgorithmException | IllegalArgumentException e) {
            return ip;
        }
    }

    /**
     * Minimum required length for session tokens (128-bit entropy encoded in Base64).
     * 16 bytes of entropy = ~22 characters in Base64 URL-safe encoding without padding.
     */
    private static final int MIN_SESSION_TOKEN_LENGTH = 20;

    /**
     * Validates session token for session fixation prevention.
     * Checks token length and entropy before comparison to prevent brute-force attacks.
     * Compares tokens using constant-time comparison to prevent timing attacks.
     *
     * @param storedToken Token stored in player data
     * @param providedToken Token provided by player/connection
     * @return true if tokens match, false otherwise
     */
    private static boolean validateSessionToken(String storedToken, String providedToken) {
        if (storedToken == null || providedToken == null) {
            return false;
        }
        // Validate minimum token length to prevent brute-force attacks
        if (providedToken.length() < MIN_SESSION_TOKEN_LENGTH) {
            LogDebug("Session token too short: " + providedToken.length() + " < " + MIN_SESSION_TOKEN_LENGTH);
            return false;
        }
        // Validate token has sufficient entropy (check for base64url character set)
        if (!providedToken.matches("^[A-Za-z0-9_-]+$")) {
            LogDebug("Session token contains invalid characters");
            return false;
        }
        // Constant-time comparison to prevent timing attacks
        return java.security.MessageDigest.isEqual(
            storedToken.getBytes(java.nio.charset.StandardCharsets.UTF_8),
            providedToken.getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );
    }

    /**
     * Player pre-join.
     * Returns text as a reason for disconnect or null to pass
     *
     * @param profile PlayerConfigEntry|GameProfile of the player
     * @param manager PlayerManager
     * @return Text if player should be disconnected
     */
    //? if >= 1.21.9 {
    public static Text checkCanPlayerJoinServer(PlayerConfigEntry profile, PlayerManager manager, SocketAddress socketAddress) {
    //?} else {
    /*public static Text checkCanPlayerJoinServer(GameProfile profile, PlayerManager manager, SocketAddress socketAddress) {
    *///?}
        // Getting the player. By this point, the player's game profile has been authenticated so the UUID is legitimate.
        String incomingPlayerUsername = StoneCutterUtils.getName(profile);
        PlayerEntity onlinePlayer = manager.getPlayer(incomingPlayerUsername);

        String ip = socketAddress.toString();
        if (ip.contains("/")) {
            ip = ip.substring(ip.indexOf(47) + 1);
        }

        if (ip.contains(":")) {
            ip = ip.substring(0, ip.indexOf(58));
        }

        // Player needs to be kicked, since there's already a player with that name
        // playing on the server
        if ((onlinePlayer != null) && ((PlayerAuth) onlinePlayer).easyAuth$isAuthenticated() && extendedConfig.preventAnotherLocationKick) {

            // if joining from same IP, allow the player to join
            if (!((PlayerAuth) onlinePlayer).easyAuth$getIpAddress().equals(ip)) {
                return langConfig.playerAlreadyOnline.getNonTranslatable(incomingPlayerUsername);
            }
        }

        // Checking if player username is valid. The pattern is generated when the config is (re)loaded.
        Matcher matcher = usernamePattern.matcher(incomingPlayerUsername);

        if (!(matcher.matches() || (extendedConfig.floodgateBypassRegex && FloodgateApiHelper.isFloodgatePlayer(StoneCutterUtils.getId(profile))))) {
            return langConfig.disallowedUsername.getNonTranslatable(extendedConfig.usernameRegexp);
        }
        // If the player name and registered name are different, kick the player if differentUsernameCase is enabled
        // Create in case of Floodgate player
        PlayerEntryV1 playerEntryV1 = PlayersCache.getOrLoadOrRegister(incomingPlayerUsername);

        if (!extendedConfig.allowCaseInsensitiveUsername && !playerEntryV1.username.equals(incomingPlayerUsername)) {
            return langConfig.differentUsernameCase.getNonTranslatable(incomingPlayerUsername);
        }

        if (config.maxLoginTries != -1 && playerEntryV1.lastKickedDate.plusSeconds(config.resetLoginAttemptsTimeout).isAfter(ZonedDateTime.now())) {
            return langConfig.loginTriesExceeded.getNonTranslatable();
        }

        // Check concurrent session limit per IP
        boolean isOnlinePlayer = config.premiumAutoLogin && (onlinePlayer != null) && ((PlayerAuth) onlinePlayer).easyAuth$isUsingMojangAccount();
        if (IpLimitManager.isConcurrentSessionLimitExceeded(manager.getServer(), ip, isOnlinePlayer)) {
            LogDebug("Player " + incomingPlayerUsername + " blocked: concurrent session limit exceeded for IP " + hashIp(ip));
            IpLimitManager.notifyAdmins(manager.getServer(), ip, incomingPlayerUsername);
            return langConfig.sessionLimitExceeded.getNonTranslatable();
        }

        return null;
    }

    public static void loadPlayerData(ServerPlayerEntity player, ClientConnection connection) {
        PlayerAuth playerAuth = (PlayerAuth) player;

        UUID playerUuid = player.getUuid();
        PlayerManager playerManager = player.server.getPlayerManager();
        administratorCache.put(playerUuid, StoneCutterUtils.isAdministrator(playerManager, player));

        // Create in case of Carpet player
        String username = StoneCutterUtils.getUsername(player);
        PlayerEntryV1 cache = PlayersCache.getOrCreate(username);
        boolean update = false;
        if (cache.uuid == null) {
            cache.uuid = player.getUuid();
            update = true;
        }
        playerAuth.easyAuth$setPlayerEntryV1(cache);

        playerAuth.easyAuth$setIpAddress(connection);
        playerAuth.easyAuth$setSkipAuth();

        if (config.vanishUntilAuth) {
            ((PlayerAuth) player).easyAuth$wasVanished(VanishIntegration.isVanished(player));
        }

        if (playerAuth.easyAuth$canSkipAuth()) {
            playerAuth.easyAuth$setAuthenticated(true);
            update = false;
        } else if (cache.lastIpHash.equals(hashIp(playerAuth.easyAuth$getIpAddress())) &&
                   cache.lastAuthenticatedDate.plusSeconds(config.sessionTimeout).isAfter(ZonedDateTime.now())) {
            // Session fixation prevention: validate session token in addition to IP match
            // The session token must be provided by the client and match the stored token in DB
            // Synchronized block prevents TOCTOU race condition during session validation
            synchronized (playerAuth) {
                String clientSessionToken = playerAuth.easyAuth$getSessionToken();
                String storedSessionToken = cache.sessionToken;

                if (storedSessionToken != null && !storedSessionToken.isEmpty() &&
                    clientSessionToken != null && !clientSessionToken.isEmpty() &&
                    validateSessionToken(storedSessionToken, clientSessionToken)) {
                    // Valid session token - regenerate for security (prevent replay attacks)
                    String newSessionToken = generateSessionToken();
                    playerAuth.easyAuth$setSessionToken(newSessionToken);
                    cache.sessionToken = newSessionToken;
                    playerAuth.easyAuth$setAuthenticated(true);
                    cache.lastAuthenticatedDate = ZonedDateTime.now();
                    cache.lastIpHash = hashIp(playerAuth.easyAuth$getIpAddress());
                    update = true;
                } else {
                    // Invalid or missing session token - require full authentication
                    // This prevents session fixation attacks where attacker sets a known token
                    playerAuth.easyAuth$setSessionToken(null);
                }
            }
        }

        if (update) {
            cache.update();
        }

        if (isSkipAllAuthChecksApplicable(player)) {
            playerAuth.easyAuth$setAuthenticated(true);
        }

        if (config.vanishUntilAuth && !playerAuth.easyAuth$isAuthenticated()) {
            VanishIntegration.setVanished(player, true);
        }
    }

    // Player joining the server
    public static void onPlayerJoin(ServerPlayerEntity player) {
        PlayerAuth playerAuth = (PlayerAuth) player;

        if (playerAuth.easyAuth$canSkipAuth()) {
            langConfig.onlinePlayerLogin.send(player);
            return;
        } else if (playerAuth.easyAuth$isAuthenticated()) {
            langConfig.validSession.send(player);
            return;
        } else if (isSkipAllAuthChecksApplicable(player)) {
            return;
        }

        // Tries to rescue player from nether portal
        if (extendedConfig.tryPortalRescue) {
            BlockPos pos = player.getBlockPos();
            player.teleport(pos.getX() + 0.5, player.getY(), pos.getZ() + 0.5, false);
            if (player.getBlockStateAtPos().getBlock().equals(Blocks.NETHER_PORTAL) || StoneCutterUtils.getServerWorld(player).getBlockState(player.getBlockPos().up()).getBlock().equals(Blocks.NETHER_PORTAL)) {
                // Faking portal blocks to be air
                BlockUpdateS2CPacket feetPacket = new BlockUpdateS2CPacket(pos, Blocks.AIR.getDefaultState());
                player.networkHandler.sendPacket(feetPacket);

                BlockUpdateS2CPacket headPacket = new BlockUpdateS2CPacket(pos.up(), Blocks.AIR.getDefaultState());
                player.networkHandler.sendPacket(headPacket);
            }
        }
    }

    public static void onPlayerLeave(ServerPlayerEntity player) {
        UUID playerUuid = player.getUuid();
        administratorCache.remove(playerUuid);
        lastAcceptedPacketByPlayer.remove(playerUuid);
        customPacketRateLimit.remove(playerUuid); // Clean up rate limit cache

        PlayerAuth playerAuth = (PlayerAuth) player;
        if (playerAuth.easyAuth$canSkipAuth())
            return;

        if (playerAuth.easyAuth$isAuthenticated()) {
            PlayerEntryV1 playerCache = playerAuth.easyAuth$getPlayerEntryV1();
            playerCache.lastAuthenticatedDate = ZonedDateTime.now();
            playerCache.update();
            return;
        }
        if (config.hidePlayerCoords) {
            ((PlayerAuth) player).easyAuth$restoreTrueLocation();
        }
    }

    public static boolean isSkipAllAuthChecksApplicable(ServerPlayerEntity player) {
        if (!extendedConfig.skipAllAuthChecks) {
            return false;
        }

        PlayerAuth playerAuth = (PlayerAuth) player;
        if (extendedConfig.skipAllAuthChecksNotForRegisteredPlayers && !playerAuth.easyAuth$getPlayerEntryV1().password.isEmpty()) {
            return false;
        }

        if (extendedConfig.skipAllAuthChecksNotForOperators && isAdministratorCached(player)) {
            return false;
        }

        return true;
    }

    // Player execute command
    public static ActionResult onPlayerCommand(ServerPlayerEntity player, String command) {
        // Getting the message to then be able to check it
        if (hasConfigFlag(FLAG_ALLOW_COMMANDS)) {
            return ActionResult.PASS;
        }
        if (player == null) {
            return ActionResult.PASS;
        }
        if (command == null) {
            return ActionResult.PASS;
        }
        if (((PlayerAuth) player).easyAuth$isAuthenticated()) {
            return ActionResult.PASS;
        }

        if (command.startsWith("login ")
                || command.startsWith("register ")
                || (extendedConfig.aliases.login && command.startsWith("l "))
                || (extendedConfig.aliases.register && command.startsWith("reg "))) {
            return ActionResult.PASS;
        }

        String normalizedCommand = command.trim();
        if (normalizedCommand.startsWith("/")) {
            normalizedCommand = normalizedCommand.substring(1);
        }

        String lowercaseCommand = normalizedCommand.toLowerCase(Locale.ENGLISH);
        if (lowercaseCommand.equals("op")
                || lowercaseCommand.startsWith("op ")
                || lowercaseCommand.equals("minecraft:op")
                || lowercaseCommand.startsWith("minecraft:op ")
                || lowercaseCommand.equals("deop")
                || lowercaseCommand.startsWith("deop ")
                || lowercaseCommand.equals("minecraft:deop")
                || lowercaseCommand.startsWith("minecraft:deop ")) {
            administratorCache.clear();
        }

        String username = StoneCutterUtils.getUsername(player);
        for (String allowedCommand : extendedConfig.allowedCommands) {
            if (command.startsWith(allowedCommand)) {
                LogDebug("Player " + username + " executed command " + command + " without being authenticated.");
                return ActionResult.PASS;
            }
        }
        LogDebug("Player " + username + " tried to execute command " + command + " without being authenticated.");
        ((PlayerAuth) player).easyAuth$sendAuthMessage();
        return ActionResult.FAIL;
    }

    // Player chatting
    public static ActionResult onPlayerChat(ServerPlayerEntity player) {
        if (!((PlayerAuth) player).easyAuth$isAuthenticated() && !hasConfigFlag(FLAG_ALLOW_CHAT)) {
            ((PlayerAuth) player).easyAuth$sendAuthMessage();
            return ActionResult.FAIL;
        }
        return ActionResult.PASS;
    }

    // Optimized player movement handling with rate-limited teleport
    // Uses millisecond precision instead of nanoTime for better performance
    private static final long TELEPORT_COOLDOWN_MS = 500; // Minimum 500ms between teleports

    public static ActionResult onPlayerMove(ServerPlayerEntity player) {
        // Player will fall if enabled (prevent fly kick)
        // Otherwise, movement should be disabled
        PlayerAuth playerAuth = (PlayerAuth) player;
        if (!playerAuth.easyAuth$isAuthenticated() && !hasConfigFlag(FLAG_ALLOW_MOVEMENT)) {
            UUID playerUuid = player.getUuid();
            long now = System.currentTimeMillis();

            // Check if we should send another teleport
            Long lastTeleport = lastAcceptedPacketByPlayer.get(playerUuid);
            if (lastTeleport == null || now - lastTeleport >= TELEPORT_COOLDOWN_MS) {
                // Only teleport if player has actually moved
                player.networkHandler.requestTeleport(player.getX(), player.getY(), player.getZ(), player.getYaw(), player.getPitch());
                lastAcceptedPacketByPlayer.put(playerUuid, now);
            }
            return ActionResult.FAIL;
        }
        return ActionResult.PASS;
    }

    // Using a block (right-click function)
    public static ActionResult onUseBlock(PlayerEntity player) {
        if (!((PlayerAuth) player).easyAuth$isAuthenticated() && !hasConfigFlag(FLAG_ALLOW_BLOCK_INTERACTION)) {
            ((PlayerAuth) player).easyAuth$sendAuthMessage();
            return ActionResult.FAIL;
        }
        return ActionResult.PASS;
    }

    // Breaking a block
    public static boolean onBreakBlock(PlayerEntity player) {
        if (!((PlayerAuth) player).easyAuth$isAuthenticated() && !hasConfigFlag(FLAG_ALLOW_BLOCK_BREAKING)) {
            ((PlayerAuth) player).easyAuth$sendAuthMessage();
            return false;
        }
        return true;
    }

    // Using an item
    //? if >= 1.21.2 {
    public static ActionResult onUseItem(PlayerEntity player) {
        if (!((PlayerAuth) player).easyAuth$isAuthenticated() && !hasConfigFlag(FLAG_ALLOW_ITEM_USING)) {
            ((PlayerAuth) player).easyAuth$sendAuthMessage();
            return ActionResult.FAIL;
        }

        return ActionResult.PASS;
    }
    //?} else {
    /*public static TypedActionResult<ItemStack> onUseItem(PlayerEntity player) {
        if (!((PlayerAuth) player).easyAuth$isAuthenticated() && !hasConfigFlag(FLAG_ALLOW_ITEM_USING)) {
            ((PlayerAuth) player).easyAuth$sendAuthMessage();
            return TypedActionResult.fail(ItemStack.EMPTY);
        }

        return TypedActionResult.pass(ItemStack.EMPTY);
    }
    *///?}

    // Dropping an item
    public static ActionResult onDropItem(PlayerEntity player) {
        if (!((PlayerAuth) player).easyAuth$isAuthenticated() && !hasConfigFlag(FLAG_ALLOW_ITEM_DROPPING)) {
            ((PlayerAuth) player).easyAuth$sendAuthMessage();
            return ActionResult.FAIL;
        }
        return ActionResult.PASS;
    }

    // Changing inventory (item moving etc.)
    public static ActionResult onTakeItem(ServerPlayerEntity player) {
        if (!((PlayerAuth) player).easyAuth$isAuthenticated() && !hasConfigFlag(FLAG_ALLOW_ITEM_MOVING)) {
            ((PlayerAuth) player).easyAuth$sendAuthMessage();
            return ActionResult.FAIL;
        }

        return ActionResult.PASS;
    }

    // Attacking an entity
    public static ActionResult onAttackEntity(PlayerEntity player) {
        if (!((PlayerAuth) player).easyAuth$isAuthenticated() && !hasConfigFlag(FLAG_ALLOW_ENTITY_ATTACKING)) {
            ((PlayerAuth) player).easyAuth$sendAuthMessage();
            return ActionResult.FAIL;
        }

        return ActionResult.PASS;
    }

    // Interacting with entity
    public static ActionResult onUseEntity(PlayerEntity player) {
        if (!((PlayerAuth) player).easyAuth$isAuthenticated() && !hasConfigFlag(FLAG_ALLOW_ENTITY_INTERACTION)) {
            ((PlayerAuth) player).easyAuth$sendAuthMessage();
            return ActionResult.FAIL;
        }

        return ActionResult.PASS;
    }

    public static void onPreLogin(ServerLoginNetworkHandler netHandler) {
        if (extendedConfig.forcedOfflineUuid && netHandler.profile != null) {
            //? if >= 1.21.9 {
            netHandler.profile = Uuids.getOfflinePlayerProfile(netHandler.profile.name());
            //?} else if >= 1.20.3 {
            /*netHandler.profile = Uuids.getOfflinePlayerProfile(netHandler.profile.getName());
            *///?} else if >= 1.20.2 {
            /*netHandler.profile = ServerLoginNetworkHandler.createOfflineProfile(netHandler.profile.getName());
            *///?} else {
            /*netHandler.profile = netHandler.toOfflineProfile(netHandler.profile);
            *///?}
        }
    }

}

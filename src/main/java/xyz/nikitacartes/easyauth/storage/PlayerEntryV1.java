package xyz.nikitacartes.easyauth.storage;

import com.google.gson.*;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;
import xyz.nikitacartes.easyauth.event.AuthEventHandler;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static xyz.nikitacartes.easyauth.EasyAuth.*;

public class PlayerEntryV1 {

    public static final Gson gson = new GsonBuilder()
            .excludeFieldsWithoutExposeAnnotation()
            .registerTypeAdapter(ZonedDateTime.class, new ZonedDateTimeAdapter())
            .create();

    // Write-back cache for batched database updates
    private static final ScheduledExecutorService WRITE_BACK_SCHEDULER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "PlayerEntryV1-WriteBack");
        t.setDaemon(true);
        return t;
    });

    // Tracks players with pending updates
    private static final ConcurrentHashMap<String, PlayerEntryV1> PENDING_UPDATES = new ConcurrentHashMap<>();

    // Debounce delay in milliseconds
    private static final long WRITE_BACK_DELAY_MS = 1000;

    // Register shutdown hook to flush pending updates on server stop
    static {
        Runtime.getRuntime().addShutdownHook(new Thread(PlayerEntryV1::flushAllPending, "PlayerEntryV1-ShutdownHook"));
    }

    public String username;
    public String usernameLowerCase;
    public UUID uuid = null;

    /**
     * Hashed password of player.
     */
    @Expose
    public String password = "";

    /**
     * Last recorded IP hash of player (HMAC-SHA256).
     * Used for {@link AuthEventHandler#onPlayerJoin(ServerPlayerEntity) sessions}.
     * Storing HMAC-SHA256 hash instead of plain text for privacy compliance (GDPR).
     */
    @Expose
    @SerializedName("last_ip_hash")
    public String lastIpHash = "";

    /**
     * Session token for session fixation prevention.
     * Generated on successful login and validated on session resume.
     * Stored in database to persist across server restarts.
     */
    @Expose
    @SerializedName("session_token")
    public String sessionToken = null;

    /**
     * Stores the last time a player was successfully authenticated (unix ms).
     */
    @Expose
    @SerializedName("last_authenticated_date")
    public ZonedDateTime lastAuthenticatedDate = getUnixZero();

    /**
     * Stores how many times the player has tried to log in.
     * Cleared on every successful login and every time the player is kicked for too many incorrect logins.
     */
    @Expose
    @SerializedName("login_tries")
    public long loginTries = 0;

    /**
     * Stores the last time a player was kicked for too many logins (unix ms).
     */
    @Expose
    @SerializedName("last_kicked_date")
    public ZonedDateTime lastKickedDate = getUnixZero();

    /**
     * Does the player have an online account?
     */
    @Expose
    @SerializedName("online_account")
    public OnlineAccount onlineAccount = OnlineAccount.UNKNOWN;

    /**
     * Registration date of the player.
     */
    @Expose
    @SerializedName("registration_date")
    public ZonedDateTime registrationDate = getUnixZero();

    /**
     * Stores version of the player data.
     */
    @Expose
    @SerializedName("data_version")
    public int dataVersion = 1;

    /**
     * Forced UUID for the player.
     * When set, this UUID will be used instead of the default offline/online UUID.
     * Useful for preserving player data when switching between online/offline modes.
     */
    @Expose
    @SerializedName("forced_uuid")
    private String forcedUuid = null;

    /**
     * Gets the forced UUID for this player.
     * @return forced UUID or null if not set
     */
    @Nullable
    public String getForcedUuid() {
        return forcedUuid;
    }

    /**
     * Sets the forced UUID for this player with format validation.
     * @param uuid the UUID to set, or null to clear
     * @throws IllegalArgumentException if UUID format is invalid
     */
    public void setForcedUuid(@Nullable String uuid) {
        if (uuid != null && !uuid.matches("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")) {
            throw new IllegalArgumentException("Invalid UUID format: " + uuid);
        }
        this.forcedUuid = uuid;
        // Mark as dirty for write-back
        if (usernameLowerCase != null) {
            update();
        }
    }

    // Cached JSON to avoid redundant serialization
    private transient volatile String cachedJson = null;
    // Dirty flag to track if data has changed
    private transient AtomicBoolean dirty = new AtomicBoolean(false);

    public PlayerEntryV1(String username, String usernameLowerCase, String uuid, String json) {
        PlayerEntryV1 entry = gson.fromJson(json, PlayerEntryV1.class);
        ZonedDateTime startOfTime = getUnixZero();

        this.username = username;
        this.usernameLowerCase = usernameLowerCase;
        this.uuid = uuid == null ? null : UUID.fromString(uuid);

        this.password = entry.password == null ? "" : entry.password;
        // Migrate from deprecated last_ip to last_ip_hash if needed
        this.lastIpHash = entry.lastIpHash == null ? "" : entry.lastIpHash;
        this.loginTries = entry.loginTries;
        this.onlineAccount = entry.onlineAccount == null ? OnlineAccount.UNKNOWN : entry.onlineAccount;
        this.lastAuthenticatedDate = entry.lastAuthenticatedDate == null ? startOfTime : entry.lastAuthenticatedDate;
        this.lastKickedDate = entry.lastKickedDate == null ? startOfTime : entry.lastKickedDate;
        this.registrationDate = entry.registrationDate == null ? startOfTime : entry.registrationDate;
        this.dataVersion = entry.dataVersion;
        this.forcedUuid = entry.forcedUuid;
    }

    public PlayerEntryV1(String username) {
        this.username = username;
        this.usernameLowerCase = username.toLowerCase(Locale.ENGLISH);
    }

    public PlayerEntryV1(String username, UUID uuid) {
        this(username);
        this.uuid = uuid;
    }

    public String toJson() {
        return getCachedJson();
    }

    /**
     * Marks this entry as dirty for write-back batching.
     * Multiple calls within the debounce window will be batched into a single DB write.
     */
    public void update() {
        dirty.set(true);
        cachedJson = null; // Invalidate cache

        // Only schedule if not already pending
        if (!PENDING_UPDATES.containsKey(usernameLowerCase)) {
            PENDING_UPDATES.put(usernameLowerCase, this);
            WRITE_BACK_SCHEDULER.schedule(
                () -> flushUpdate(usernameLowerCase),
                WRITE_BACK_DELAY_MS,
                TimeUnit.MILLISECONDS
            );
        }
    }

    /**
     * Flushes pending update to database.
     * Called by the write-back scheduler after debounce period.
     */
    private void flushUpdate(String usernameLower) {
        PlayerEntryV1 pending = PENDING_UPDATES.remove(usernameLower);
        if (pending != null && pending.dirty.compareAndSet(true, false)) {
            try {
                DB.updateUserData(pending);
            } catch (Exception e) {
                // Re-mark as dirty on failure for retry
                pending.dirty.set(true);
                PENDING_UPDATES.put(usernameLower, pending);
            }
        }
    }

    /**
     * Forces immediate flush of all pending updates.
     * Useful for server shutdown or critical data consistency.
     */
    public static void flushAllPending() {
        for (String username : PENDING_UPDATES.keySet()) {
            PlayerEntryV1 entry = PENDING_UPDATES.remove(username);
            if (entry != null && entry.dirty.compareAndSet(true, false)) {
                DB.updateUserData(entry);
            }
        }
    }

    /**
     * Gets cached JSON representation, computing if necessary.
     */
    public String getCachedJson() {
        String cached = cachedJson;
        if (cached == null || dirty.get()) {
            cached = gson.toJson(this);
            cachedJson = cached;
        }
        return cached;
    }

    public enum OnlineAccount {
        TRUE,
        FALSE,
        UNKNOWN
    }

    private static class ZonedDateTimeAdapter implements JsonSerializer<ZonedDateTime>, JsonDeserializer<ZonedDateTime> {
        private static final DateTimeFormatter formatter = DateTimeFormatter.ISO_ZONED_DATE_TIME;

        @Override
        public JsonElement serialize(ZonedDateTime src, java.lang.reflect.Type typeOfSrc, com.google.gson.JsonSerializationContext context) {
            return new JsonPrimitive(src.format(formatter));
        }

        @Override
        public ZonedDateTime deserialize(JsonElement json, java.lang.reflect.Type typeOfT, com.google.gson.JsonDeserializationContext context) throws JsonParseException {
            return ZonedDateTime.parse(json.getAsString(), formatter);
        }
    }
}



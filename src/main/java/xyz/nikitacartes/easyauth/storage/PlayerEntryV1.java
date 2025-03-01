package xyz.nikitacartes.easyauth.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import net.minecraft.server.network.ServerPlayerEntity;
import xyz.nikitacartes.easyauth.event.AuthEventHandler;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.UUID;

import static xyz.nikitacartes.easyauth.EasyAuth.DB;
import static xyz.nikitacartes.easyauth.EasyAuth.THREADPOOL;
import static xyz.nikitacartes.easyauth.utils.EasyLogger.LogDebug;

public class PlayerEntryV1 {

    public static final Gson gson = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create();

    public String username;
    public String usernameLowerCase;
    public UUID uuid = null;

    /**
     * Hashed password of player.
     */
    @Expose
    public String password = "";

    /**
     * Last recorded IP of player.
     * Used for {@link AuthEventHandler#onPlayerJoin(ServerPlayerEntity) sessions}.
     */
    @Expose
    @SerializedName("last_ip")
    public String lastIp = "";

    /**
     * Stores the last time a player was successfully authenticated (unix ms).
     */
    @Expose
    @SerializedName("last_authenticated_date")
    public LocalDateTime lastAuthenticatedDate = LocalDateTime.ofEpochSecond(0, 0, ZoneOffset.UTC);

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
    public LocalDateTime lastKickedDate = LocalDateTime.ofEpochSecond(0, 0, ZoneOffset.UTC);

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
    public LocalDateTime registrationDate = LocalDateTime.ofEpochSecond(0, 0, ZoneOffset.UTC);

    /**
     * Stores version of the player data.
     */
    @Expose
    @SerializedName("data_version")
    public int dataVersion = 1;


    public PlayerEntryV1(String username, String usernameLowerCase, String uuid, String json) {
        PlayerEntryV1 entry = gson.fromJson(json, PlayerEntryV1.class);
        LocalDateTime startOfTime = LocalDateTime.ofEpochSecond(0, 0, ZoneOffset.UTC);

        this.username = username;
        this.usernameLowerCase = usernameLowerCase;
        this.uuid = uuid == null ? null : UUID.fromString(uuid);

        this.password = entry.password == null ? "" : entry.password;
        this.lastIp = entry.lastIp == null ? "" : entry.lastIp;
        this.loginTries = entry.loginTries;
        this.onlineAccount = entry.onlineAccount == null ? OnlineAccount.UNKNOWN : entry.onlineAccount;
        this.lastAuthenticatedDate = entry.lastAuthenticatedDate == null ? startOfTime : entry.lastAuthenticatedDate;
        this.lastKickedDate = entry.lastKickedDate == null ? startOfTime : entry.lastKickedDate;
        this.registrationDate = entry.registrationDate == null ? startOfTime : entry.registrationDate;
        this.dataVersion = entry.dataVersion;
    }

    public PlayerEntryV1(String username) {
        this.username = username;
        this.usernameLowerCase = username.toLowerCase(Locale.ENGLISH);
    }

    public String toJson() {
        return gson.toJson(this);
    }

    /*
     * Update entry in database.
     */
    public void update() {
        LogDebug("Updating player data for " + username + " in database: " + toJson());
        THREADPOOL.execute(() -> DB.updateUserData(this));
    }

    public enum OnlineAccount {
        TRUE,
        FALSE,
        UNKNOWN
    }
}

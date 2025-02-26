package xyz.nikitacartes.easyauth.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import net.minecraft.server.network.ServerPlayerEntity;
import xyz.nikitacartes.easyauth.event.AuthEventHandler;

import java.util.Locale;

import static xyz.nikitacartes.easyauth.EasyAuth.DB;
import static xyz.nikitacartes.easyauth.EasyAuth.THREADPOOL;
import static xyz.nikitacartes.easyauth.utils.EasyLogger.LogDebug;

public class PlayerEntryV1 {

    public static final Gson gson = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create();

    public String username;
    public String usernameLowerCase;

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
    @SerializedName("last_authenticated")
    public long lastAuthenticated = 0;

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
    @SerializedName("last_kicked")
    public long lastKicked = 0;

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
    public long registrationDate = -1;

    /**
     * Stores version of the player data.
     */
    @Expose
    @SerializedName("data_version")
    public int dataVersion = 1;


    public PlayerEntryV1(String username, String usernameLowerCase, String json) {
        PlayerEntryV1 entry = gson.fromJson(json, PlayerEntryV1.class);
        this.password = entry.password;
        this.lastIp = entry.lastIp;
        this.lastAuthenticated = entry.lastAuthenticated;
        this.loginTries = entry.loginTries;
        this.lastKicked = entry.lastKicked;
        this.username = username;
        this.usernameLowerCase = usernameLowerCase;
        this.dataVersion = entry.dataVersion;
        this.onlineAccount = entry.onlineAccount;
        this.registrationDate = entry.registrationDate;
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

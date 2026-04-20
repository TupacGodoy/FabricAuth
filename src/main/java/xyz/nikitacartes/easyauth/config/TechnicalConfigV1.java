package xyz.nikitacartes.easyauth.config;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Comment;

import java.util.ArrayList;

import static xyz.nikitacartes.easyauth.utils.StoneCutterUtils.isModLoaded;

@ConfigSerializable
public class TechnicalConfigV1 extends ConfigTemplate {

    @Comment("""
            Hashed global password with embedded salt.
            BCrypt and Argon2 algorithms include salt in the hash itself (128-bit random salt).
            The globalPasswordSalt field is deprecated and not used - algorithm handles salting.""")
    public @Nullable String globalPassword = null;

    @Comment("""
            Deprecated: BCrypt/Argon2 handle salting internally.
            This field is kept for backwards compatibility but is not used.""")
    @Deprecated
    public @Nullable String globalPasswordSalt = null;

    @Comment("""
            
            List of players forced to offline mode.""")
    @Deprecated
    public transient ArrayList<String> forcedOfflinePlayers = new ArrayList<>();

    @Comment("""
            
            List of players confirmed as online.""")
    @Deprecated
    public transient ArrayList<String> confirmedOnlinePlayers = new ArrayList<>();

    @Comment("""
            
            Whether Floodgate mod is loaded.""")
    public transient boolean floodgateLoaded = false;

    @Comment("""
            
            Whether LuckPerms mod is loaded.""")
    public transient boolean luckPermsLoaded = false;

    @Comment("""
            
            Whether Vanish mod is loaded.""")
    public transient boolean vanishLoaded = false;

    @Comment("""

            Whether Permissions API mod is loaded.""")
    public transient boolean permissionsLoaded = false;

    @Comment("""

            Deprecated: Legacy IP salt for SHA-256 fallback.
            HMAC key is now derived from server UUID (not stored in config).
            This field is kept for backwards compatibility only.""")
    @Deprecated
    public @Nullable String ipSalt = null;

    public TechnicalConfigV1() {
        super("technical.conf", """
                ##                          ##
                ##         EasyAuth         ##
                ##     Technical Config     ##
                ##                          ##""");
    }

    public static TechnicalConfigV1 create() {
        TechnicalConfigV1 config = loadConfig(TechnicalConfigV1.class, "technical.conf");
        if (config == null) {
            config = new TechnicalConfigV1();
            config.save();
        }
        config.loadedMods();
        return config;
    }

    public static TechnicalConfigV1 load() {
        return create();
    }

    private void loadedMods() {
        if (isModLoaded("floodgate")) {
            floodgateLoaded = true;
        }
        if (isModLoaded("luckperms")) {
            luckPermsLoaded = true;
        }
        if (isModLoaded("melius-vanish")) {
            vanishLoaded = true;
        }
        if (isModLoaded("fabric-permissions-api-v0")) {
            permissionsLoaded = true;
        }
    }

    @Override
    public void save() {
        save(TechnicalConfigV1.class, this);
    }
}

package xyz.nikitacartes.easyauth.config;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;

@ConfigSerializable
public class VersionConfig extends ConfigTemplate {
    public int configVersion = -1;

    public VersionConfig() {
        super("main.conf", null);
    }

    public static VersionConfig load() {
        VersionConfig config = loadConfig(VersionConfig.class, "main.conf");
        return config != null ? config : new VersionConfig();
    }

    @Override
    public void save() {
        throw new UnsupportedOperationException("VersionConfig should not be saved directly. Use the specific config classes instead.");
    }
}

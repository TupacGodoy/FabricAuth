package xyz.nikitacartes.easyauth.config;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraft.server.command.CommandOutput;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Comment;
import xyz.nikitacartes.easyauth.utils.EasyLogger;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

import static net.minecraft.text.Text.translatable;
import static net.minecraft.text.Text.translatableWithFallback;
import static net.minecraft.text.TranslatableTextContent.EMPTY_ARGUMENTS;
import static xyz.nikitacartes.easyauth.EasyAuth.langConfig;

@ConfigSerializable
public class LangConfigV1 extends ConfigTemplate {

    @Comment("""
            Enable server-side translation.
            While enabled EasyAuth sends messages, translated to player's client language.
            List of available languages: https://github.com/NikitaCartes/EasyAuth/tree/HEAD/src/main/resources/data/easyauth/lang
            Disabling this option will force EasyAuth to send all messaged from that file.""")
    public boolean enableServerSideTranslation = true;

    @Comment("""
            
            Default language for EasyAuth.
            
            Note: with server-side translation enabled, this language will be used for non-translatable messages
            Note: with disable server-side translation, message from "text" field have higher priority than defaultLanguage.""")
    public String defaultLanguage = "en_us";
    public TranslatableText enterPassword = new TranslatableText();
    public TranslatableText enterNewPassword = new TranslatableText();
    public TranslatableText wrongPassword = new TranslatableText();
    public TranslatableText matchPassword = new TranslatableText();
    public TranslatableText passwordUpdated = new TranslatableText();
    public TranslatableText loginRequired = new TranslatableText();
    public TranslatableText loginTriesExceeded = new TranslatableText();
    public TranslatableText globalPasswordSet = new TranslatableText();
    public TranslatableText cannotChangePassword = new TranslatableText();
    public TranslatableText cannotUnregister = new TranslatableText();
    public TranslatableText notAuthenticated = new TranslatableText();
    public TranslatableText alreadyAuthenticated = new TranslatableText();
    public TranslatableText successfullyAuthenticated = new TranslatableText();
    public TranslatableText successfulLogout = new TranslatableText();
    public TranslatableText timeExpired = new TranslatableText();
    public TranslatableText registerRequired = new TranslatableText();
    public TranslatableText alreadyRegistered = new TranslatableText();
    public TranslatableText registerSuccess = new TranslatableText();
    public TranslatableText userdataDeleted = new TranslatableText();
    public TranslatableText userdataUpdated = new TranslatableText();
    public TranslatableText accountDeleted = new TranslatableText();
    public TranslatableText configurationReloaded = new TranslatableText();
    public TranslatableText maxPasswordChars = new TranslatableText();
    public TranslatableText minPasswordChars = new TranslatableText();
    public TranslatableText disallowedUsername = new TranslatableText();
    public TranslatableText playerAlreadyOnline = new TranslatableText();
    public TranslatableText worldSpawnSet = new TranslatableText();
    public TranslatableText corruptedPlayerData = new TranslatableText();
    public TranslatableText userNotRegistered = new TranslatableText();
    public TranslatableText cannotLogout = new TranslatableText();
    public TranslatableText offlineUuid = new TranslatableText();
    public TranslatableText registeredPlayers = new TranslatableText();
    public TranslatableText validSession = new TranslatableText();
    public TranslatableText onlinePlayerLogin = new TranslatableText();
    public TranslatableText differentUsernameCase = new TranslatableText();
    public TranslatableText wrongGlobalPassword = new TranslatableText();
    public TranslatableText registerRequiredWithGlobalPassword = new TranslatableText();
    public TranslatableText markAsOffline = new TranslatableText();
    public TranslatableText markAsOnline = new TranslatableText();
    public TranslatableText selfMarkAsOnline = new TranslatableText();
    public TranslatableText selfMarkAsOnlineWarning = new TranslatableText();

    private static Map<String, String> translations = new HashMap<>();

    public LangConfigV1() {
        super("translation.conf", """
                ##                             ##
                ##          EasyAuth           ##
                ##  Translation Configuration  ##
                ##                             ##""");
    }

    public static LangConfigV1 create() {
        LangConfigV1 config = loadConfig(LangConfigV1.class, "translation.conf");
        if (config == null) {
            config = new LangConfigV1();
            config.save();
            config = loadConfig(LangConfigV1.class, "translation.conf");
        }
        return config;
    }

    public static LangConfigV1 load() {
        LangConfigV1 config = loadConfig(LangConfigV1.class, "translation.conf");
        if (config == null) {
            throw new RuntimeException("Failed to load translation.conf");
        }

        Gson gson = new Gson();
        Type mapType = new TypeToken<Map<String, String>>() {}.getType();

        String path = "data/easyauth/lang/" + config.defaultLanguage + ".json";
        InputStream input = LangConfigV1.class.getClassLoader().getResourceAsStream(path);

        if (input == null) {
            EasyLogger.LogError("Failed to load default language " + config.defaultLanguage + ".json. Using en_us.json instead.");
            translations = new HashMap<>();
            return config;
        }

        try (InputStreamReader reader = new InputStreamReader(input)) {
            translations = gson.fromJson(reader, mapType);
        } catch (Exception e) {
            EasyLogger.LogError("Failed to load default language", e);
            translations = new HashMap<>();
        }

        return config;
    }

    @Override
    public void save() {
        save(LangConfigV1.class, this);
    }

    public static final class TranslatableText {
        private final String key;
        public final String fallback;
        public final boolean enabled;
        public final boolean serverSide;

        public TranslatableText() {
            this.key = null;
            this.fallback = "";
            this.enabled = true;
            this.serverSide = true;
        }

        public TranslatableText(String key, String fallback) {
            this.key = key;
            this.fallback = fallback;
            this.enabled = true;
            this.serverSide = true;
        }

        public TranslatableText(String key, String fallback, boolean enabled, boolean serverSide) {
            this.key = key;
            this.fallback = fallback;
            this.enabled = enabled;
            this.serverSide = serverSide;
        }

        public void send(ServerCommandSource commandOutput) {
            if (enabled && commandOutput != null) {
                commandOutput.sendMessage(getTranslation());
            }
        }

        public void send(ServerPlayerEntity commandOutput) {
            if (enabled && commandOutput != null) {
                commandOutput.sendMessage(getTranslation());
            }
        }

        public <T extends CommandOutput> void send(T commandOutput) {
            if (enabled && commandOutput != null) {
                commandOutput.sendMessage(getTranslation());
            }
        }

        public void send(ServerCommandSource commandOutput, Object... args) {
            if (enabled && commandOutput != null) {
                commandOutput.sendMessage(getTranslation(args));
            }
        }

        public MutableText get() {
            if (enabled) {
                return getTranslation();
            } else {
                return Text.literal("");
            }
        }

        public MutableText get(Object... args) {
            if (enabled) {
                return getTranslation(args);
            } else {
                return Text.literal("");
            }
        }

        public MutableText getNonTranslatable() {
            return getNonTranslatable(EMPTY_ARGUMENTS);
        }

        public MutableText getNonTranslatable(Object... args) {
            if (enabled) {
                if (!fallback.isEmpty()) {
                    return translatableWithFallback(key, fallback, args);
                } else {
                    return translatableWithFallback(key, translations.get(key), args);
                }
            } else {
                return Text.literal("");
            }
        }

        private MutableText getTranslation() {
            return getTranslation(EMPTY_ARGUMENTS);
        }

        private MutableText getTranslation(Object... args) {
            if (langConfig.enableServerSideTranslation && serverSide) {
                return translatable(key, args);
            } if (!fallback.isEmpty()) {
                return translatableWithFallback(key, fallback, args);
            } else {
                return translatableWithFallback(key, translations.get(key), args);
            }
        }
    }

}

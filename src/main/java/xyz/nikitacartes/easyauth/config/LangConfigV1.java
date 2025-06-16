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
    public TranslatableText enterPassword = new TranslatableText("text.easyauth.enterPassword");
    public TranslatableText enterNewPassword = new TranslatableText("text.easyauth.enterNewPassword");
    public TranslatableText wrongPassword = new TranslatableText("text.easyauth.wrongPassword");
    public TranslatableText matchPassword = new TranslatableText("text.easyauth.matchPassword");
    public TranslatableText passwordUpdated = new TranslatableText("text.easyauth.passwordUpdated");
    public TranslatableText loginRequired = new TranslatableText("text.easyauth.loginRequired");
    public TranslatableText loginTriesExceeded = new TranslatableText("text.easyauth.loginTriesExceeded");
    public TranslatableText globalPasswordSet = new TranslatableText("text.easyauth.globalPasswordSet");
    public TranslatableText cannotChangePassword = new TranslatableText("text.easyauth.cannotChangePassword");
    public TranslatableText cannotUnregister = new TranslatableText("text.easyauth.cannotUnregister");
    public TranslatableText notAuthenticated = new TranslatableText("text.easyauth.notAuthenticated");
    public TranslatableText alreadyAuthenticated = new TranslatableText("text.easyauth.alreadyAuthenticated");
    public TranslatableText successfullyAuthenticated = new TranslatableText("text.easyauth.successfullyAuthenticated");
    public TranslatableText successfulLogout = new TranslatableText("text.easyauth.successfulLogout");
    public TranslatableText timeExpired = new TranslatableText("text.easyauth.timeExpired");
    public TranslatableText registerRequired = new TranslatableText("text.easyauth.registerRequired");
    public TranslatableText alreadyRegistered = new TranslatableText("text.easyauth.alreadyRegistered");
    public TranslatableText registerSuccess = new TranslatableText("text.easyauth.registerSuccess");
    public TranslatableText userdataDeleted = new TranslatableText("text.easyauth.userdataDeleted");
    public TranslatableText userdataUpdated = new TranslatableText("text.easyauth.userdataUpdated");
    public TranslatableText accountDeleted = new TranslatableText("text.easyauth.accountDeleted");
    public TranslatableText configurationReloaded = new TranslatableText("text.easyauth.configurationReloaded");
    public TranslatableText maxPasswordChars = new TranslatableText("text.easyauth.maxPasswordChars");
    public TranslatableText minPasswordChars = new TranslatableText("text.easyauth.minPasswordChars");
    public TranslatableText disallowedUsername = new TranslatableText("text.easyauth.disallowedUsername");
    public TranslatableText playerAlreadyOnline = new TranslatableText("text.easyauth.playerAlreadyOnline");
    public TranslatableText worldSpawnSet = new TranslatableText("text.easyauth.worldSpawnSet");
    public TranslatableText corruptedPlayerData = new TranslatableText("text.easyauth.corruptedPlayerData");
    public TranslatableText userNotRegistered = new TranslatableText("text.easyauth.userNotRegistered");
    public TranslatableText cannotLogout = new TranslatableText("text.easyauth.cannotLogout");
    public TranslatableText offlineUuid = new TranslatableText("text.easyauth.offlineUuid");
    public TranslatableText registeredPlayers = new TranslatableText("text.easyauth.registeredPlayers");
    public TranslatableText validSession = new TranslatableText("text.easyauth.validSession");
    public TranslatableText onlinePlayerLogin = new TranslatableText("text.easyauth.onlinePlayerLogin");
    public TranslatableText differentUsernameCase = new TranslatableText("text.easyauth.differentUsernameCase");
    public TranslatableText wrongGlobalPassword = new TranslatableText("text.easyauth.wrongGlobalPassword");
    public TranslatableText registerRequiredWithGlobalPassword = new TranslatableText("text.easyauth.registerRequiredWithGlobalPassword");
    public TranslatableText markAsOffline = new TranslatableText("text.easyauth.markAsOffline");
    public TranslatableText markAsOnline = new TranslatableText("text.easyauth.markAsOnline");
    public TranslatableText selfMarkAsOnline = new TranslatableText("text.easyauth.selfMarkAsOnline");
    public TranslatableText selfMarkAsOnlineWarning = new TranslatableText("text.easyauth.selfMarkAsOnlineWarning");
    public TranslatableText accountNotFound = new TranslatableText("text.easyauth.accountNotFound");
    public TranslatableText accountCheckFailed = new TranslatableText("text.easyauth.accountCheckFailed");

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

        ClassLoader classLoader = LangConfigV1.class.getClassLoader();
        InputStream defaultLanguage = classLoader.getResourceAsStream("data/easyauth/lang/" + config.defaultLanguage + ".json");

        if (defaultLanguage == null) {
            EasyLogger.LogError("Failed to load default language " + config.defaultLanguage + ".json. Using en_us.json instead.");
            defaultLanguage = classLoader.getResourceAsStream("data/easyauth/lang/en_us.json");
        }

        try (InputStreamReader reader = new InputStreamReader(defaultLanguage)) {
            Gson gson = new Gson();
            Type mapType = new TypeToken<Map<String, String>>() {}.getType();

            translations = gson.fromJson(reader, mapType);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load default language", e);
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

        public TranslatableText(String key) {
            this.key = key;
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

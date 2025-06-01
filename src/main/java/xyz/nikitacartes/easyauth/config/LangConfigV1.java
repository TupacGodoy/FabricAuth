package xyz.nikitacartes.easyauth.config;

import net.minecraft.server.command.CommandOutput;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Comment;

import static net.minecraft.text.Text.translatable;
import static net.minecraft.text.Text.translatableWithFallback;
import static xyz.nikitacartes.easyauth.EasyAuth.langConfig;

@ConfigSerializable
public class LangConfigV1 extends ConfigTemplate {

    @Comment("""
            Enable server-side translation.
            While enabaled EasyAuth sends messages, translated to player's client language.
            List of aviailable languages: https://github.com/NikitaCartes/EasyAuth/tree/HEAD/src/main/resources/data/easyauth/lang
            Disabling this oprion will force EasyAuth to send all messaged from that file.""")
    public boolean enableServerSideTranslation = true;
    public TranslatableText enterPassword = new TranslatableText("text.easyauth.enterPassword", "§6You need to enter your password!");
    public TranslatableText enterNewPassword = new TranslatableText("text.easyauth.enterNewPassword", "§4You need to enter new password!");
    public TranslatableText wrongPassword = new TranslatableText("text.easyauth.wrongPassword", "§4Wrong password!");
    public TranslatableText matchPassword = new TranslatableText("text.easyauth.matchPassword", "§6Passwords must match!");
    public TranslatableText passwordUpdated = new TranslatableText("text.easyauth.passwordUpdated", "§aYour password was updated successfully!");
    public TranslatableText loginRequired = new TranslatableText("text.easyauth.loginRequired", "§cYou are not authenticated!\n§6Use /login, /l to authenticate!");
    public TranslatableText loginTriesExceeded = new TranslatableText("text.easyauth.loginTriesExceeded", "§4Too many login tries. Please wait a few minutes and try again.");
    public TranslatableText globalPasswordSet = new TranslatableText("text.easyauth.globalPasswordSet", "§aGlobal password was successfully set!");
    public TranslatableText cannotChangePassword = new TranslatableText("text.easyauth.cannotChangePassword", "§cYou cannot change password!");
    public TranslatableText cannotUnregister = new TranslatableText("text.easyauth.cannotUnregister", "§cYou cannot unregister this account!");
    public TranslatableText notAuthenticated = new TranslatableText("text.easyauth.notAuthenticated", "§cYou are not authenticated!\n§6Try with /login, /l or /register.");
    public TranslatableText alreadyAuthenticated = new TranslatableText("text.easyauth.alreadyAuthenticated", "§6You are already authenticated.");
    public TranslatableText successfullyAuthenticated = new TranslatableText("text.easyauth.successfullyAuthenticated", "§aYou are now authenticated.");
    public TranslatableText successfulLogout = new TranslatableText("text.easyauth.successfulLogout", "§aLogged out successfully.");
    public TranslatableText timeExpired = new TranslatableText("text.easyauth.timeExpired", "§cTime for authentication has expired.");
    public TranslatableText registerRequired = new TranslatableText("text.easyauth.registerRequired", "§6Type /register <password> <password> to claim this account.");
    public TranslatableText alreadyRegistered = new TranslatableText("text.easyauth.alreadyRegistered", "§6This account name is already registered!");
    public TranslatableText registerSuccess = new TranslatableText("text.easyauth.registerSuccess", "§aYou are now authenticated.");
    public TranslatableText userdataDeleted = new TranslatableText("text.easyauth.userdataDeleted", "§aUserdata deleted.");
    public TranslatableText userdataUpdated = new TranslatableText("text.easyauth.userdataUpdated", "§aUserdata updated.");
    public TranslatableText accountDeleted = new TranslatableText("text.easyauth.accountDeleted", "§aYour account was successfully deleted!");
    public TranslatableText configurationReloaded = new TranslatableText("text.easyauth.configurationReloaded", "§aConfiguration file was reloaded successfully.");
    public TranslatableText maxPasswordChars = new TranslatableText("text.easyauth.maxPasswordChars", "§6Password can be at most %d characters long!");
    public TranslatableText minPasswordChars = new TranslatableText("text.easyauth.minPasswordChars", "§6Password needs to be at least %d characters long!");
    public TranslatableText disallowedUsername = new TranslatableText("text.easyauth.disallowedUsername", "§6Invalid username characters! Allowed character regex: %s");
    public TranslatableText playerAlreadyOnline = new TranslatableText("text.easyauth.playerAlreadyOnline", "§cPlayer %s is already online!");
    public TranslatableText worldSpawnSet = new TranslatableText("text.easyauth.worldSpawnSet", "§aSpawn for logging in was set successfully.");
    public TranslatableText corruptedPlayerData = new TranslatableText("text.easyauth.corruptedPlayerData", "§cYour data is probably corrupted. Please contact admin.");
    public TranslatableText userNotRegistered = new TranslatableText("text.easyauth.userNotRegistered", "§cThis player is not registered!");
    public TranslatableText cannotLogout = new TranslatableText("text.easyauth.cannotLogout", "§cYou cannot logout!");
    public TranslatableText offlineUuid = new TranslatableText("text.easyauth.offlineUuid", "Offline UUID for %s is %s");
    public TranslatableText registeredPlayers = new TranslatableText("text.easyauth.registeredPlayers", "List of registered players:");
    public TranslatableText validSession = new TranslatableText("text.easyauth.validSession", "§aYou have a valid session. No need to log in.");
    public TranslatableText onlinePlayerLogin = new TranslatableText("text.easyauth.onlinePlayerLogin", "§aYou are using an online account. No need to log in.");
    public TranslatableText differentUsernameCase = new TranslatableText("text.easyauth.diffrentUsernameCase", "§6You are using a different case of your username. Please use the correct one.");
    public TranslatableText wrongGlobalPassword = new TranslatableText("text.easyauth.wrongGlobalPassword", "§4Wrong global password!");
    public TranslatableText registerRequiredWithGlobalPassword = new TranslatableText("text.easyauth.registerRequiredWithGlobalPassword", "§6Type /register <global password> <password> <password> to claim this account.");
    public TranslatableText markAsOffline = new TranslatableText("text.easyauth.markAsOffline", "§aPlayer %s was marked as offline.");
    public TranslatableText markAsOnline = new TranslatableText("text.easyauth.markAsOnline", "§aPlayer %s was marked as online.");
    public TranslatableText selfMarkAsOnline = new TranslatableText("text.easyauth.selfMarkAsOnline", "§aYou marked yourself as online player. You can rejoin now.");
    public TranslatableText selfMarkAsOnlineWarning = new TranslatableText("text.easyauth.selfMarkAsOnlineWarning", "§6You want to mark yourself as online player.\n§6You will not be able to log in if you don't have an online account.\n§6Data, connected to offline uuid (villagers' discounts, pets) will be lost.\n§aIf you are want to continue, type /account online <password> true.");

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
        }
        return config;
    }

    public static LangConfigV1 load() {
        LangConfigV1 config = loadConfig(LangConfigV1.class, "translation.conf");
        if (config == null) {
            throw new RuntimeException("Failed to load translation.conf");
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
                if (langConfig.enableServerSideTranslation && serverSide) {
                    commandOutput.sendMessage(translatable(key));
                } else {
                    commandOutput.sendMessage(Text.literal(fallback));
                }
            }
        }

        public void send(ServerPlayerEntity commandOutput) {
            if (enabled && commandOutput != null) {
                if (langConfig.enableServerSideTranslation && serverSide) {
                    commandOutput.sendMessage(translatable(key));
                } else {
                    commandOutput.sendMessage(Text.literal(fallback));
                }
            }
        }

        public <T extends CommandOutput> void send(T commandOutput) {
            if (enabled && commandOutput != null) {
                if (langConfig.enableServerSideTranslation && serverSide) {
                    commandOutput.sendMessage(translatable(key));
                } else {
                    commandOutput.sendMessage(Text.literal(fallback));
                }
            }
        }

        public void send(ServerCommandSource commandOutput, Object... args) {
            if (enabled && commandOutput != null) {
                if (langConfig.enableServerSideTranslation && serverSide) {
                    commandOutput.sendMessage(translatable(key, args));
                } else {
                    commandOutput.sendMessage(translatable(fallback, args));
                }
            }
        }

        public MutableText get() {
            if (enabled) {
                if (langConfig.enableServerSideTranslation && serverSide) {
                    return translatable(key);
                } else {
                    return Text.literal(fallback);
                }
            } else {
                return Text.literal("");
            }
        }

        public MutableText get(Object... args) {
            if (enabled) {
                if (langConfig.enableServerSideTranslation && serverSide) {
                    return translatable(key, args);
                } else {
                    return translatable(fallback, args);
                }
            } else {
                return Text.literal("");
            }
        }

        public MutableText getWithFallback() {
            if (enabled) {
                if (langConfig.enableServerSideTranslation && serverSide) {
                    return translatableWithFallback(key, fallback);
                } else {
                    return Text.literal(fallback);
                }
            } else {
                return Text.literal("");
            }
        }

        public MutableText getWithFallback(Object... args) {
            if (enabled) {
                if (langConfig.enableServerSideTranslation && serverSide) {
                    return translatableWithFallback(key, fallback, args);
                } else {
                    return translatable(fallback, args);
                }
            } else {
                return Text.literal("");
            }
        }
    }

}

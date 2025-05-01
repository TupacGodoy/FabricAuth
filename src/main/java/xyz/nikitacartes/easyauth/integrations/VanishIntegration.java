package xyz.nikitacartes.easyauth.integrations;

import me.drex.vanish.api.VanishAPI;
import me.drex.vanish.api.VanishEvents;
import me.drex.vanish.util.VanishedEntity;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.server.network.ServerPlayerEntity;
import xyz.nikitacartes.easyauth.utils.PlayerAuth;

import static xyz.nikitacartes.easyauth.EasyAuth.config;

public class VanishIntegration {
    public static boolean isVanished(ServerPlayerEntity player) {
        return VanishAPI.isVanished(player);
    }

    public static void setVanished(ServerPlayerEntity player, boolean vanished) {
        VanishAPI.setVanish(player, vanished);
        ((VanishedEntity) player).vanish$setDirty();
    }

    public static void listenJoinEvent() {
        VanishEvents.JOIN_EVENT.register(player -> {
            if (config.vanishUntilAuth) {
                ((PlayerAuth) player).easyAuth$wasVanished(VanishIntegration.isVanished(player));
                return TriState.TRUE;
            }
            return TriState.DEFAULT;
        });
    }
}

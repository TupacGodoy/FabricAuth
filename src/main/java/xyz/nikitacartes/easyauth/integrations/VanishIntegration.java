package xyz.nikitacartes.easyauth.integrations;

import me.drex.vanish.api.VanishAPI;
//? if != 1.20.2 || < 1.20 {
import me.drex.vanish.util.VanishedEntity;
//?}
import net.minecraft.server.network.ServerPlayerEntity;

public class VanishIntegration {
    public static boolean isVanished(ServerPlayerEntity player) {
        return VanishAPI.isVanished(player);
    }

    public static void setVanished(ServerPlayerEntity player, boolean vanished) {
        VanishAPI.setVanish(player, vanished);
        //? if != 1.20.2 || < 1.20 {
        ((VanishedEntity) player).vanish$setDirty();
        //?}
    }
}

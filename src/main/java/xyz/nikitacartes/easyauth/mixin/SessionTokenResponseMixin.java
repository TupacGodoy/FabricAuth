//? if >= 1.20.5 {
package xyz.nikitacartes.easyauth.mixin;

import net.minecraft.network.packet.c2s.common.CookieResponseC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.nikitacartes.easyauth.interfaces.PlayerAuth;

import java.util.Optional;

/**
 * Handles session token cookie response from client.
 * Stores the received token for validation during session resume.
 */
@Mixin(ServerPlayNetworkHandler.class)
public abstract class SessionTokenResponseMixin {

    @Shadow
    public ServerPlayerEntity player;

    /**
     * Session token cookie identifier.
     * Format: easyauth:session_token
     */
    @Unique
    private static final net.minecraft.util.Identifier SESSION_TOKEN_COOKIE_ID =
        net.minecraft.util.Identifier.of("easyauth", "session_token");

    /**
     * Handle cookie response from client and store session token for validation.
     */
    @Inject(
        method = "onCookieResponse(Lnet/minecraft/network/packet/c2s/common/CookieResponseC2SPacket;)V",
        at = @At("HEAD")
    )
    private void onCookieResponse(CookieResponseC2SPacket packet, CallbackInfo ci) {
        // Check if this is the session token cookie
        if (!packet.key().equals(SESSION_TOKEN_COOKIE_ID)) {
            return;
        }

        // Extract the cookie value (session token) and store it in player data
        Optional<String> cookieValue = packet.value();
        if (cookieValue.isPresent()) {
            PlayerAuth playerAuth = (PlayerAuth) player;
            playerAuth.easyAuth$setSessionToken(cookieValue.get());
        }
    }
}
//?}

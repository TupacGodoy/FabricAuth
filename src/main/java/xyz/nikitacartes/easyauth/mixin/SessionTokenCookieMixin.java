//? if >= 1.20.5 {
package xyz.nikitacartes.easyauth.mixin;

import net.minecraft.network.packet.s2c.common.CookieRequestS2CPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Requests session token cookie from client on join.
 *
 * For Minecraft 1.20.5+, this requests any stored session token from the client.
 * Vanilla clients will return empty; modded clients with EasyAuth support can return stored token.
 *
 * Note: Full session token protection requires client-side support to store and return tokens.
 * Without client mod, sessions are based on IP hash + timestamp only (still more secure than before).
 */
@Mixin(ServerPlayNetworkHandler.class)
public abstract class SessionTokenCookieMixin {

    @Shadow
    public ServerPlayerEntity player;

    @Unique
    private static final net.minecraft.util.Identifier SESSION_TOKEN_COOKIE_ID =
        net.minecraft.util.Identifier.of("easyauth", "session_token");

    /**
     * Request session token cookie from client when they join.
     */
    @Inject(
        method = "onPlayerSession(Lnet/minecraft/network/packet/c2s/play/PlayerSessionC2SPacket;)V",
        at = @At("TAIL")
    )
    private void onPlayerSession(net.minecraft.network.packet.c2s.play.PlayerSessionC2SPacket packet, CallbackInfo ci) {
        CookieRequestS2CPacket cookieRequest = new CookieRequestS2CPacket(SESSION_TOKEN_COOKIE_ID);
        ((ServerPlayNetworkHandler)(Object)this).send(cookieRequest);
    }
}
//?}

package xyz.nikitacartes.easyauth.mixin;

import com.mojang.brigadier.ParseResults;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.util.ActionResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;

//? if >= 1.20.3 {
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//?} else {
/*import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
*///?}
import xyz.nikitacartes.easyauth.event.AuthEventHandler;

import static xyz.nikitacartes.easyauth.EasyAuth.langConfig;

@Mixin(CommandManager.class)
public class CommandManagerMixin {
    //? if >= 1.20.3 {
    @Inject(method = "execute(Lcom/mojang/brigadier/ParseResults;Ljava/lang/String;)V", at = @At("HEAD"), cancellable = true)
    private void checkCanUseCommands(ParseResults<ServerCommandSource> parseResults, String command, CallbackInfo ci) {
    //?} else {
    /*@Inject(method = "execute(Lcom/mojang/brigadier/ParseResults;Ljava/lang/String;)I", at = @At("HEAD"), cancellable = true)
    private void checkCanUseCommands(ParseResults<ServerCommandSource> parseResults, String command, CallbackInfoReturnable<Integer> cir) {
    *///?}
        ActionResult result = AuthEventHandler.onPlayerCommand(parseResults.getContext().getSource().getPlayer(), command);
        if (result == ActionResult.FAIL) {
            langConfig.loginRequired.send(parseResults.getContext().getSource());
            //? if >= 1.20.3 {
            ci.cancel();
            //?} else {
            /*cir.setReturnValue(1);
            *///?}
        }
    }
}
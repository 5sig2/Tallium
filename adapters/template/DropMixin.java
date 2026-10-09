package dev.sig.tallium.mixin;
import dev.sig.tallium.adapter.TalliumClient;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(LocalPlayer.class)
public abstract class DropMixin {
    @Inject(method="drop",at=@At("HEAD")) private void tallium$drop(boolean all,CallbackInfoReturnable<Boolean> ci){TalliumClient.cancelAttempts();}
}

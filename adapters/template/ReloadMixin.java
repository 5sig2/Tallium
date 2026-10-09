package dev.sig.tallium.mixin;
import dev.sig.tallium.adapter.*;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.concurrent.CompletableFuture;
@Mixin(Minecraft.class)
public abstract class ReloadMixin {
    @Inject(method="reloadResourcePacks()Ljava/util/concurrent/CompletableFuture;",at=@At("RETURN"),require=0)
    private void tallium$reload(CallbackInfoReturnable<CompletableFuture<Void>> ci){ci.getReturnValue().thenRun(()->Minecraft.getInstance().execute(()->{HudReference.clear();VanillaIcons.clear();TalliumClient.rebuildCatalogue();}));}
}

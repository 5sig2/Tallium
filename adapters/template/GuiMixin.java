package dev.sig.tallium.mixin;
import dev.sig.tallium.adapter.TalliumClient;
import net.minecraft.client.gui.*;
import net.minecraft.client.DeltaTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Gui.class)
public abstract class GuiMixin {
    @Inject(method="render",at=@At("TAIL")) private void tallium$hud(GuiGraphics graphics,DeltaTracker delta,CallbackInfo ci){TalliumClient.render(graphics);}
}

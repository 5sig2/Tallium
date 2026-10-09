package dev.sig.tallium.mixin;
import dev.sig.tallium.adapter.HudReference;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(@SCREEN_OWNER@.class)
public abstract class ScreenMixin {
    @Inject(method="setScreen",at=@At("HEAD")) private void tallium$reference(Screen next,CallbackInfo ci){HudReference.beforeScreen(next);}
}

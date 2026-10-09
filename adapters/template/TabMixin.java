package dev.sig.tallium.mixin;
import dev.sig.tallium.adapter.Tags;
import dev.sig.tallium.adapter.VanillaIcons;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.*;
@Mixin(PlayerTabOverlay.class)
public abstract class TabMixin {
    @Unique private final Map<Component,Tags.Layout> tallium$names=new IdentityHashMap<>();
    @Inject(method="@TAB_RENDER@",at=@At("HEAD")) private void tallium$clear(CallbackInfo ci){tallium$names.clear();}
    @Inject(method="getNameForDisplay",at=@At("RETURN"),cancellable=true)
    private void tallium$tab(PlayerInfo player,CallbackInfoReturnable<Component> ci){Tags.Layout layout=Tags.build(player.getProfile().@PROFILE_UUID@(),ci.getReturnValue(),true);tallium$names.put(layout.text(),layout);ci.setReturnValue(layout.text());}
    @Redirect(method="@TAB_RENDER@",at=@At(value="INVOKE",target="@TAB_DRAW_TARGET@"),require=0)
    private @DRAW_RETURN_TYPE@ tallium$draw(GuiGraphics g,Font font,Component text,int x,int y,int color){
        @DRAW_ORIGINAL@
        Tags.Layout layout=tallium$names.remove(text);if(layout!=null)for(Tags.Icon icon:layout.icons()){
            @TAB_POSE_PUSH@ @TAB_POSE_TRANSLATE@ @TAB_POSE_SCALE@
            if(icon.mode().equals("RESOURCE_PACK"))g.renderItem(icon.stack(),0,0);else VanillaIcons.draw(g,icon.stack(),0,0);
            @TAB_POSE_POP@
        }
        @DRAW_RETURN@
    }
}

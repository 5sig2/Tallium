package dev.sig.tallium.mixin;
import dev.sig.tallium.adapter.TalliumClient;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(LivingEntity.class)
public abstract class LocalPlayerMixin {
    @Inject(method="startUsingItem",at=@At("HEAD")) private void tallium$start(InteractionHand hand,CallbackInfo ci){if((Object)this==Minecraft.getInstance().player)TalliumClient.beginUse(hand);}
    @Inject(method="onSyncedDataUpdated",at=@At("HEAD")) private void tallium$useState(net.minecraft.network.syncher.EntityDataAccessor<?> data,CallbackInfo ci){if((Object)this instanceof net.minecraft.world.entity.player.Player player)TalliumClient.remoteUseState(player);}
}

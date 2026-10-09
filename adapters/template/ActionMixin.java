package dev.sig.tallium.mixin;
import dev.sig.tallium.adapter.TalliumClient;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(MultiPlayerGameMode.class)
public abstract class ActionMixin {
    @Inject(method="useItem",at=@At("HEAD")) private void tallium$use(Player player,InteractionHand hand,CallbackInfoReturnable<InteractionResult> ci){TalliumClient.attemptUse(hand);}
    @Inject(method="releaseUsingItem",at=@At("HEAD")) private void tallium$bow(Player player,CallbackInfo ci){TalliumClient.attemptBowRelease();}
    @Inject(method="useItemOn",at=@At("HEAD")) private void tallium$place(LocalPlayer player,InteractionHand hand,BlockHitResult hit,CallbackInfoReturnable<InteractionResult> ci){TalliumClient.attemptPlacement(hand,hit);}
    @Inject(method="attack",at=@At("HEAD")) private void tallium$attack(CallbackInfo ci){TalliumClient.cancelAttempts();}
}

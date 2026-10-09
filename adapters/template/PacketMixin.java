package dev.sig.tallium.mixin;
import dev.sig.tallium.adapter.TalliumClient;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(ClientPacketListener.class)
public abstract class PacketMixin {
    @Inject(method="sendCommand",at=@At("HEAD"),cancellable=true) private void tallium$command(String command,CallbackInfo ci){if(dev.sig.tallium.adapter.ClientCommands.execute(command))ci.cancel();}
    @Inject(method="sendUnsignedCommand",at=@At("HEAD"),cancellable=true,require=0) private void tallium$unsigned(String command,CallbackInfoReturnable<Boolean> ci){if(dev.sig.tallium.adapter.ClientCommands.execute(command))ci.setReturnValue(true);}
    @Inject(method="handleCommands",at=@At("TAIL")) private void tallium$commands(ClientboundCommandsPacket packet,CallbackInfo ci){dev.sig.tallium.adapter.ClientCommands.register();}

    @Inject(method="handleEntityEvent",at=@At(value="INVOKE",target="Lnet/minecraft/network/protocol/game/ClientboundEntityEventPacket;getEntity(Lnet/minecraft/world/level/Level;)Lnet/minecraft/world/entity/Entity;"))
    private void tallium$status(ClientboundEntityEventPacket packet,CallbackInfo ci){TalliumClient.entityEvent(packet);}
    @Inject(method="handleAddEntity",at=@At("TAIL")) private void tallium$spawn(ClientboundAddEntityPacket packet,CallbackInfo ci){TalliumClient.spawn(packet);}
    @Inject(method="handleSetEntityData",at=@At("TAIL")) private void tallium$metadata(ClientboundSetEntityDataPacket packet,CallbackInfo ci){dev.sig.tallium.adapter.ProjectileObservations.metadata(packet.id());}
    @Inject(method="handleRemoveEntities",at=@At("HEAD")) private void tallium$removal(ClientboundRemoveEntitiesPacket packet,CallbackInfo ci){
        if(net.minecraft.client.Minecraft.getInstance().isSameThread())packet.getEntityIds().forEach(id->{TalliumClient.entityRemoved(id);dev.sig.tallium.adapter.ProjectileObservations.metadata(id);});
    }
    @Inject(method="handleContainerSetSlot",at=@At("TAIL")) private void tallium$inventory(ClientboundContainerSetSlotPacket packet,CallbackInfo ci){TalliumClient.slotUpdate(packet);}
    @Inject(method="handleContainerContent",at=@At("TAIL")) private void tallium$contents(ClientboundContainerSetContentPacket packet,CallbackInfo ci){TalliumClient.containerUpdate(packet);}
    @PLAYER_INVENTORY_HOOK@
    @Inject(method="handleBlockUpdate",at=@At("TAIL")) private void tallium$block(ClientboundBlockUpdatePacket packet,CallbackInfo ci){TalliumClient.blockUpdate(packet);}
    @Inject(method="handleSetEquipment",at=@At("TAIL")) private void tallium$equipment(ClientboundSetEquipmentPacket packet,CallbackInfo ci){TalliumClient.equipment(packet);}
}

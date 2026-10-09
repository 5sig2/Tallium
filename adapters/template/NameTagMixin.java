package dev.sig.tallium.mixin;
import dev.sig.tallium.adapter.*;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.*;
@Mixin(EntityRenderer.class)
public abstract class NameTagMixin {
    @Unique private final Map<Object,Tags.Layout> tallium$layouts=new WeakHashMap<>();
    @TAG_HOOKS@
    @Unique private static void tallium$quad(PoseStack.Pose pose,VertexConsumer vertices,float x,int light){
        vertices.addVertex(pose.pose(),x,0,0.01f).setColor(-1).setUv(0,0).setUv2(light & 65535,light >>> 16);
        vertices.addVertex(pose.pose(),x,8,0.01f).setColor(-1).setUv(0,1).setUv2(light & 65535,light >>> 16);
        vertices.addVertex(pose.pose(),x+8,8,0.01f).setColor(-1).setUv(1,1).setUv2(light & 65535,light >>> 16);
        vertices.addVertex(pose.pose(),x+8,0,0.01f).setColor(-1).setUv(1,0).setUv2(light & 65535,light >>> 16);
    }
}

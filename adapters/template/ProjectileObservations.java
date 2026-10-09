package dev.sig.tallium.adapter;

import dev.sig.tallium.tracking.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.Vec3;
import java.util.*;


public final class ProjectileObservations {
    private record Pending(UUID uuid,int id,int owner,String type,Vec3 position,long tick,UUID session,long epoch) {}
    private static final Set<UUID> seen=new HashSet<>();
    private static final Map<UUID,Pending> pending=new LinkedHashMap<>();
    private static final Set<UUID> metadataReady=new HashSet<>();
    private static final Map<UUID,Long> lastArrow=new HashMap<>();
    private static long tick;private static boolean limited;
    public static boolean limited(){return limited;}
    public static void clear(){limited=false;seen.clear();pending.clear();metadataReady.clear();lastArrow.clear();}
    public static void cancel(){pending.clear();metadataReady.clear();lastArrow.clear();}
    public static void metadata(int entityId){
        for(Pending p:pending.values())if(p.id()==entityId)metadataReady.add(p.uuid());
        resolve();
    }
    public static void spawn(ClientboundAddEntityPacket packet){
        String type=BuiltInRegistries.ENTITY_TYPE.getKey(packet.getType()).toString();
        if(!Set.of("minecraft:ender_pearl","minecraft:wind_charge","minecraft:experience_bottle","minecraft:potion","minecraft:splash_potion","minecraft:lingering_potion","minecraft:arrow","minecraft:spectral_arrow").contains(type))return;
        if(seen.contains(packet.getUUID()))return;
        if(seen.size()>=CounterStore.MAX_IDENTITIES){if(!limited)TalliumClient.STORE.diagnostic(tick,"Projectile tracking limit reached. Reconnect to resume.");limited=true;return;}
        seen.add(packet.getUUID());
        if(!TalliumClient.config.tracking||pending.size()>=4096)return;
        Pending value=new Pending(packet.getUUID(),packet.getId(),packet.getData(),type,new Vec3(packet.getX(),packet.getY(),packet.getZ()),tick,TalliumClient.STORE.session(),TalliumClient.STORE.epoch());
        pending.put(value.uuid(),value);resolve();
    }
    public static void tick(){
        tick++;resolve();
    }
    private static void resolve(){
        Minecraft mc=Minecraft.getInstance();if(mc.level==null)return;
        var iterator=pending.values().iterator();
        while(iterator.hasNext()){
            Pending p=iterator.next();
            if(!TalliumClient.config.tracking||!p.session().equals(TalliumClient.STORE.session())||p.epoch()!=TalliumClient.STORE.epoch()||tick-p.tick()>20){iterator.remove();continue;}
            Entity entity=mc.level.getEntity(p.id());if(!(entity instanceof Projectile projectile)||!entity.getUUID().equals(p.uuid()))continue;
            Entity owner=projectile.getOwner();if(owner==null&&p.owner()>0)owner=mc.level.getEntity(p.owner());
            if(!(owner instanceof Player player))continue;
            if(player!=mc.player&&player.distanceToSqr(p.position())>256){iterator.remove();continue;}
            boolean arrow=p.type().endsWith(":arrow")||p.type().endsWith(":spectral_arrow");
            boolean potion=p.type().contains("potion");

            if(potion&&tick-p.tick()<1&&!metadataReady.contains(p.uuid()))continue;
            ItemStack stack=switch(p.type()){
                case "minecraft:ender_pearl"->new ItemStack(Items.ENDER_PEARL);
                case "minecraft:wind_charge"->new ItemStack(Items.WIND_CHARGE);
                case "minecraft:experience_bottle"->new ItemStack(Items.EXPERIENCE_BOTTLE);
                case "minecraft:spectral_arrow"->new ItemStack(Items.SPECTRAL_ARROW);
                case "minecraft:arrow"->new ItemStack(Items.ARROW);
                default->entity instanceof ItemSupplier supplier?supplier.getItem().copy():ItemStack.EMPTY;
            };
            iterator.remove();if(stack.isEmpty())continue;
            if(arrow&&player!=mc.player){Long previous=lastArrow.put(player.getUUID(),tick);if(previous!=null&&tick-previous<=2)continue;}
            TalliumClient.projectileObserved(player,TalliumClient.identity(stack),arrow,p.uuid(),p.session(),p.epoch());
        }
        metadataReady.retainAll(pending.keySet());
    }
}

package dev.sig.tallium.adapter;

import com.mojang.blaze3d.platform.InputConstants;
import dev.sig.tallium.config.*;
import dev.sig.tallium.display.*;
import dev.sig.tallium.inventory.*;
import dev.sig.tallium.items.*;
import dev.sig.tallium.tracking.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.@IDENTIFIER@;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.phys.*;
import net.minecraft.world.level.ClipContext;
import net.minecraft.network.protocol.game.*;
import java.util.*;
import java.io.IOException;

public final class TalliumClient implements ClientModInitializer {
    public static final String GAME=FabricLoader.getInstance().getModContainer("minecraft").orElseThrow().getMetadata().getVersion().getFriendlyString();
    public static final ConfigStore STORAGE=new ConfigStore(FabricLoader.getInstance().getConfigDir());
    public static Config config=STORAGE.load();
    public static final CounterStore STORE=new CounterStore();
    public static final Capabilities CAPABILITIES=new Capabilities(@VANILLA_TOTEM@);
    public static final KeyMapping OPEN=@KEY_OPEN@;
    public static final KeyMapping INFO=@KEY_INFO@;
    public static final KeyMapping RESET=@KEY_RESET@;
    public static final Map<String,KeyMapping> RESET_KEYS=new LinkedHashMap<>();
    public static InventorySnapshot inventory=InventorySnapshot.empty();
    public record SeenPlayer(UUID uuid,String name,long lastSeen,boolean present) {}
    public static final Map<UUID,SeenPlayer> roster=new LinkedHashMap<>();
    private static Object connection;
    private static String world="";
    private static long sequence,ticks,lastInspection;
    private static String heldReset;
    private static long heldSince;
    private record UseSnapshot(ItemIdentity item,UUID session,long epoch,long sequence,net.minecraft.world.InteractionHand hand,int slot,int count,long tick) {}
    private static UseSnapshot useSnapshot;
    private static final class LocalAction {
        final UUID session=STORE.session();final long epoch=STORE.epoch(),id=++sequence,tick=ticks;
        final ItemStack before;final ItemIdentity counted;final int slot;final Observation.Action action;final Observation.Unit unit;
        final net.minecraft.core.BlockPos target;
        boolean outcome=Minecraft.getInstance().player.getAbilities().instabuild,projectile,block;
        LocalAction(ItemStack before,ItemIdentity counted,int slot,Observation.Action action,Observation.Unit unit,net.minecraft.core.BlockPos target){this.before=before.copy();this.counted=counted;this.slot=slot;this.action=action;this.unit=unit;this.target=target;}
    }
    private static LocalAction localAction;
    private static boolean previousTracking=true;
    private static final Map<String,Cycle> cycles=new HashMap<>();
    private static final Set<UUID> dead=new HashSet<>();
    private static final ConsumptionTracker remoteUses=new ConsumptionTracker();
    private static Object observedLevel;
    private static final Map<String,ItemStack> representatives=new HashMap<>();
    public static final List<ItemStack> catalogue=new ArrayList<>();
    private static Map<String,Long> hudValues=Map.of();
    private static Map<UUID,Map<String,Value>> usageValues=Map.of();
    @Override public void onInitializeClient() {}
    public static KeyMapping[] installKeys(KeyMapping[] existing){
        @REGISTER_KEY_CATEGORY@
        List<KeyMapping> list=new ArrayList<>(Arrays.asList(existing));
        list.removeIf(k->k.getName().startsWith("key.tallium."));list.addAll(List.of(OPEN,INFO,RESET));
        for(Config.ResetAction action:config.resetActions){
            KeyMapping key=RESET_KEYS.computeIfAbsent(action.id,id->@KEY_CUSTOM@);list.add(key);
        }
        RESET_KEYS.keySet().removeIf(id->config.resetActions.stream().noneMatch(a->a.id.equals(id)));
        dev.sig.tallium.mixin.KeyMappingAccessor.tallium$all().keySet().removeIf(name->name.startsWith("key.tallium.action.")&&config.resetActions.stream().noneMatch(a->name.equals("key.tallium.action."+a.id)));
        return list.toArray(KeyMapping[]::new);
    }
    public static void apply(Config working)throws IOException{
        working.validate();STORAGE.save(working);config=working;cycles.clear();Tags.clear();
        Minecraft mc=Minecraft.getInstance();
        ((dev.sig.tallium.mixin.OptionsAccessor)mc.options).tallium$setKeys(installKeys(mc.options.keyMappings));
        mc.options.load();KeyMapping.resetMapping();rebuildCatalogue();
    }
    public static ItemIdentity identity(ItemStack stack){
        String id=BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();PotionContents p=stack.get(DataComponents.POTION_CONTENTS);
        List<ItemIdentity.Effect> effects=new ArrayList<>();String potion="";
        if(p!=null){potion=p.potion().flatMap(h->h.unwrapKey()).map(k->k.identifier().toString()).orElse("");
            for(var e:p.getAllEffects())effects.add(new ItemIdentity.Effect(BuiltInRegistries.MOB_EFFECT.getKey(e.getEffect().value()).toString(),e.getAmplifier(),e.getDuration()));}
        Set<String> tags=new HashSet<>();if(stack.has(DataComponents.FOOD))tags.add("food");if(consumable(stack))tags.add("consumables");
        if(id.endsWith(":potion")||id.endsWith(":splash_potion")||id.endsWith(":lingering_potion"))tags.add("potions");
        if(id.endsWith(":tipped_arrow"))tags.add("tipped_arrows");
        if(id.endsWith(":arrow")||id.endsWith(":spectral_arrow")||id.endsWith(":tipped_arrow"))tags.add("arrows");

        return new ItemIdentity(id,potion,effects,p==null?"":"potion="+potion+";effects="+effects.stream().sorted().toList(),tags);
    }
    public static void rebuildCatalogue(){
        @WORLD_ITEMS_CLEAR@
        catalogue.clear();representatives.clear();
        for(Item item:BuiltInRegistries.ITEM){if(item==Items.AIR)continue;ItemStack stack=menuStack(item);catalogue.add(stack);representatives.put(BuiltInRegistries.ITEM.getKey(item).toString(),stack);}
        Minecraft mc=Minecraft.getInstance();if(mc.level==null){refreshAvailability(false);return;}
        @POTION_CATALOGUE@
        refreshAvailability(true);
        config.profiles.stream().flatMap(p->p.definitions.stream()).filter(d->d.enabled&&!d.unavailable).forEach(d->VanillaIcons.texture(representative(d)));
    }
    private static void refreshAvailability(boolean potionsLoaded){for(Config.Profile profile:config.profiles)for(Config.Definition d:profile.definitions){
        boolean missing=d.selector.item()!=null&&!representatives.containsKey(d.selector.item());
        if(d.selector.category()!=null&&!Set.of("food","potions","tipped_arrows","arrows").contains(d.selector.category()))missing=true;
        if(d.selector.effect()!=null&&!BuiltInRegistries.MOB_EFFECT.containsKey(@IDENTIFIER@.parse(d.selector.effect())))missing=true;
        if(potionsLoaded&&d.selector.potion()!=null&&catalogue.stream().noneMatch(s->identity(s).potion().equals(d.selector.potion())))missing=true;
        d.unavailable=missing;
    }}
    public static ItemStack representative(Config.Definition d){
        String key=d.selector.item()==null?"":d.selector.item();
        if(d.selector.exactComponents()!=null){ItemStack stack=representatives.get(key+"|exact|"+d.selector.exactComponents());if(stack!=null)return stack;}
        if(d.selector.potion()!=null){ItemStack stack=representatives.get(key+"|"+d.selector.potion());if(stack!=null)return stack;}
        return representatives.getOrDefault(key,menuStack(Items.PAPER));
    }

    private static ItemStack menuStack(Item item){@MENU_STACK@}
    public static void tick(){
        Minecraft mc=Minecraft.getInstance();ticks++;
        if(localAction!=null&&(ticks-localAction.tick>100||localAction.epoch!=STORE.epoch()||!config.tracking||mc.screen!=null))localAction=null;
        if(mc.level==null||mc.player==null){if(connection!=null){HudReference.clear();PlayerPopup.clear();STORE.newSession();summaries.clear();roster.clear();cycles.clear();Tags.clear();dead.clear();ProjectileObservations.clear();remoteUses.clear();cancelAttempts();usageValues=Map.of();inventory=InventorySnapshot.empty();connection=null;}return;}
        Object current=mc.getConnection();String dimension=mc.level.dimension().identifier().toString();
        if(connection!=current){HudReference.clear();PlayerPopup.clear();STORE.newSession();summaries.clear();roster.clear();dead.clear();ProjectileObservations.clear();remoteUses.clear();cancelAttempts();cycles.clear();Tags.clear();connection=current;world=dimension;rebuildCatalogue();}
        if(observedLevel!=mc.level||!world.equals(dimension)){observedLevel=mc.level;CustomLabels.clear();HudReference.clear();PlayerPopup.clear();world=dimension;ProjectileObservations.clear();remoteUses.clear();cancelAttempts();if(config.resetDimension)STORE.resetAll();}
        if(config.tracking!=previousTracking){ProjectileObservations.cancel();remoteUses.clear();cancelAttempts();previousTracking=config.tracking;}
        Set<UUID> loaded=new HashSet<>();
        for(Player p:mc.level.players()){
            loaded.add(p.getUUID());if(roster.size()<1024||roster.containsKey(p.getUUID()))roster.put(p.getUUID(),new SeenPlayer(p.getUUID(),p.getName().getString(),ticks,true));
            if(p.isDeadOrDying()){if(dead.add(p.getUUID())&&(p==mc.player?config.resetOwnDeath:config.resetOtherDeath))STORE.reset(p.getUUID()::equals,List.of(new Selector(null,null,null,null,null,null,null)));}
            else dead.remove(p.getUUID());
            if(p!=mc.player)remoteUseState(p);
        }
        roster.replaceAll((id,p)->loaded.contains(id)?p:new SeenPlayer(id,p.name(),p.lastSeen(),false));
        ProjectileObservations.tick();
        remoteUses.retain(loaded);CustomLabels.tick(ticks);
        List<InventorySnapshot.Slot> slots=new ArrayList<>();
        for(int i=0;i<36;i++){ItemStack s=mc.player.getInventory().getItem(i);if(!s.isEmpty()){ItemIdentity identity=identity(s);slots.add(new InventorySnapshot.Slot(i,identity,s.getCount(),i<9,false));rememberVariant(s,identity);}}
        ItemStack off=mc.player.getOffhandItem();if(!off.isEmpty())slots.add(new InventorySnapshot.Slot(40,identity(off),off.getCount(),false,true));inventory=new InventorySnapshot(slots);
        prepareViews(mc.player.getUUID());
        if(mc.screen!=null){heldReset=null;drain(OPEN);drain(INFO);drain(RESET);RESET_KEYS.values().forEach(TalliumClient::drain);return;}
        if(heldReset!=null){KeyMapping heldKey=heldReset.equals("all")?RESET:RESET_KEYS.get(heldReset);if(heldKey==null||!heldKey.isDown())heldReset=null;
            else if(ticks-heldSince>=40){String id=heldReset;heldReset=null;requestReset(id);}}
        if(mc.screen!=null)return;
        if(OPEN.consumeClick()){drain(OPEN);mc.setScreen(new TalliumScreen(null));return;}
        if(INFO.consumeClick()){drain(INFO);long now=System.nanoTime();if(now-lastInspection>=500_000_000L){lastInspection=now;Player p=target();if(p==null)inspectionMiss();else if(config.playerInfo.equals("POPUP"))PlayerPopup.show(p);else if(config.playerInfo.equals("PLAYERS"))mc.setScreen(new PlayersScreen(null,p==null?null:p.getUUID()));else inspect(p);}}
        if(mc.screen!=null){drain(RESET);RESET_KEYS.values().forEach(TalliumClient::drain);return;}
        List<String> pressed=new ArrayList<>();if(RESET.consumeClick())pressed.add("all");
        RESET_KEYS.forEach((id,key)->{if(key.consumeClick())pressed.add(id);drain(key);});drain(RESET);
        if(pressed.size()>1){hint("Conflicting Tallium reset assignments; no usage reset.");return;}
        if(pressed.size()==1){String id=pressed.getFirst();KeyMapping key=id.equals("all")?RESET:RESET_KEYS.get(id);
            long conflicts=java.util.stream.Stream.concat(java.util.stream.Stream.of(OPEN,INFO,RESET),RESET_KEYS.values().stream()).filter(k->!k.isUnbound()&&k.same(key)).count();
            if(conflicts>1){hint("Conflicting Tallium reset assignments; no usage reset.");return;}
            Config.ResetAction action=config.resetActions.stream().filter(a->a.id.equals(id)).findFirst().orElse(null);
            if(id.equals("all")?config.holdResetAll:action!=null&&action.hold){heldReset=id;heldSince=ticks;hint("Hold the assigned key for 2 seconds to reset usage.");}else requestReset(id);
        }
    }
    private static void rememberVariant(ItemStack stack,ItemIdentity identity){if(identity.exactComponents().isEmpty())return;String key=identity.item()+"|exact|"+identity.exactComponents();
        if(!representatives.containsKey(key)&&representatives.size()<8192){ItemStack copy=stack.copy();copy.setCount(1);representatives.put(key,copy);catalogue.add(copy);if(enabled(identity))VanillaIcons.texture(copy);}}
    public static String variantLabel(ItemStack stack){ItemIdentity identity=identity(stack);String name=stack.getHoverName().getString();
        if(!identity.potion().isBlank()){String potion=identity.potion().substring(identity.potion().indexOf(':')+1);if(potion.startsWith("strong_"))potion=potion.substring(7)+" II";else if(potion.startsWith("long_"))potion=potion.substring(5)+" (extended)";return name+" · "+potion.replace('_',' ');}
        return identity.effects().isEmpty()?name:name+" · "+identity.effects();}
    private static void drain(KeyMapping k){while(k.consumeClick()){} }
    private static void prepareViews(UUID local){
        Config.Profile p=config.active();Map<String,Long> hud=new HashMap<>();
        for(Config.Counter c:p.counters){Config.Definition d=p.definition(c.definition);if(d!=null&&d.enabled&&!d.unavailable)hud.put(c.id,inventory.total(List.of(d.selector),config.inventoryScope.equals("HOTBAR")));}
        hudValues=Map.copyOf(hud);Map<UUID,Map<String,Value>> all=new HashMap<>();
        for(UUID id:roster.keySet()){Map<String,Value> values=new HashMap<>();for(Config.Definition d:p.definitions)values.put(d.id,usage(id,d));all.put(id,Map.copyOf(values));}usageValues=Map.copyOf(all);
    }
    public static Value usage(UUID player,Config.Definition d){
        if(d.unavailable)return Value.unsupported("Item is unavailable in this game version.");
        boolean local=Minecraft.getInstance().player!=null&&player.equals(Minecraft.getInstance().player.getUUID());
        var capability=CAPABILITIES.forFamily(consumable(representative(d))&&!d.family.equals("potions")?"food":d.family);var support=local?capability.local():capability.remote();
        if(support==Capabilities.Support.UNSUPPORTED)return Value.unsupported(capability.gap());
        if(!roster.containsKey(player)||!config.tracking||STORE.saturated()||ProjectileObservations.limited())return Value.unknown("Tracking unavailable or paused.");
        long count=STORE.total(player,List.of(d.selector));if(!local&&consumable(representative(d))&&remoteUses.incomplete(player))return count==0?Value.unknown("Consumption was not sufficiently observed."):new Value(Value.State.ESTIMATED,count,"");return STORE.estimated(player,List.of(d.selector))?Value.estimated(count):Value.known(count);
    }
    public static Value cachedUsage(UUID player,Config.Definition d){return usageValues.getOrDefault(player,Map.of()).getOrDefault(d.id,Value.unknown("Player has not entered tracking range."));}
    public static void beginUse(net.minecraft.world.InteractionHand hand){Minecraft mc=Minecraft.getInstance();if(mc.player!=null&&mc.isSameThread()){
        ItemStack stack=mc.player.getItemInHand(hand).copy();int slot=hand==net.minecraft.world.InteractionHand.OFF_HAND?40:@SELECTED_SLOT@;
        useSnapshot=new UseSnapshot(identity(stack),STORE.session(),STORE.epoch(),++sequence,hand,slot,stack.getCount(),ticks);}}
    public static void entityEvent(ClientboundEntityEventPacket packet){
        Minecraft mc=Minecraft.getInstance();if(!mc.isSameThread()||mc.level==null)return;
        Entity e=packet.getEntity(mc.level);sequence++;if(!config.tracking||!(e instanceof Player player))return;
        if(packet.getEventId()==3&&(player==mc.player?config.resetOwnDeath:config.resetOtherDeath)&&dead.add(player.getUUID())){STORE.reset(player.getUUID()::equals,List.of(new Selector(null,null,null,null,null,null,null)));return;}
        ItemIdentity item=null;Observation.Action action=null;Observation.Unit unit=null;
        long eventEpoch=STORE.epoch();UUID eventSession=STORE.session();long actionSequence=sequence;
        if(packet.getEventId()==9&&player==mc.player&&player.isUsingItem()&&useSnapshot!=null&&useSnapshot.hand()==player.getUsedItemHand()){
            ItemStack captured=player.getUseItem().copy();String id=BuiltInRegistries.ITEM.getKey(captured.getItem()).toString();
            ItemIdentity current=identity(captured);
            if(current.equals(useSnapshot.item())&&consumable(captured)&&useSnapshot.epoch()==STORE.epoch()){
                UseSnapshot completed=useSnapshot;useSnapshot=null;
                if(enabled(completed.item()))STORE.accept(new Observation(player.getUUID(),completed.item(),"consumption",Observation.Action.CONSUME,Observation.Unit.CONSUMPTIONS,1,"Server consumption completion",Observation.Validation.CONFIRMED,completed.session(),world,++sequence,completed.epoch(),"consume/"+completed.sequence()));return;}
        }
        if(packet.getEventId()==9&&player!=mc.player){var completion=remoteUses.confirm(player.getUUID(),ticks,STORE.session(),STORE.epoch(),world);if(completion!=null&&enabled(completion.item()))STORE.accept(completion);return;}
        if(packet.getEventId()==35&&(@VANILLA_TOTEM@||@VISIBLE_TOTEM@)){item=identity(new ItemStack(Items.TOTEM_OF_UNDYING));action=Observation.Action.ACTIVATE;unit=Observation.Unit.ACTIVATIONS;}
        if(item!=null&&enabled(item))STORE.accept(new Observation(player.getUUID(),item,"",action,unit,1,"server entity status "+packet.getEventId(),Observation.Validation.CONFIRMED,eventSession,world,sequence,eventEpoch,"status/"+actionSequence));
        else if(packet.getEventId()==9||packet.getEventId()==35)STORE.diagnostic(sequence,"Status "+packet.getEventId()+" rejected: original item identity was not established.");
    }
    private static boolean enabled(ItemIdentity item){return config.profiles.stream().flatMap(p->p.definitions.stream()).anyMatch(d->d.enabled&&!d.unavailable&&d.selector.matches(item));}
    public static void slotUpdate(ClientboundContainerSetSlotPacket packet){
        Minecraft mc=Minecraft.getInstance();if(!mc.isSameThread()||mc.player==null)return;
        int slot=packet.getSlot(),container=packet.getContainerId();int inventorySlot=container==-2?slot:container==0?(slot==45?40:slot>=36&&slot<=44?slot-36:slot>=9&&slot<=35?slot:-1):-1;
        authoritativeSlot(inventorySlot,packet.getItem());
    }
    public static void containerUpdate(ClientboundContainerSetContentPacket packet){
        if(packet.@CONTAINER_ID@()!=0)return;var items=packet.@CONTAINER_ITEMS@();
        for(int slot=0;slot<items.size();slot++){int inventorySlot=slot==45?40:slot>=36&&slot<=44?slot-36:slot>=9&&slot<=35?slot:-1;
            if(inventorySlot>=0)authoritativeSlot(inventorySlot,items.get(slot));}
    }
    public static void authoritativeSlot(int inventorySlot,ItemStack authoritative){
        Minecraft mc=Minecraft.getInstance();if(!mc.isSameThread()||mc.player==null||inventorySlot<0)return;
        LocalAction attempt=localAction;
        if(attempt!=null&&inventorySlot==attempt.slot){
            if(attempt.action==Observation.Action.FIRE){
                if(attempt.before.is(Items.BOW))attempt.outcome=(authoritative.isEmpty()&&attempt.before.getDamageValue()+1>=attempt.before.getMaxDamage())||authoritative.is(Items.BOW)&&authoritative.getDamageValue()==attempt.before.getDamageValue()+1;
                else if(authoritative.is(Items.CROSSBOW)){var charged=authoritative.get(DataComponents.CHARGED_PROJECTILES);attempt.outcome=charged==null||charged.isEmpty();}
            }else attempt.outcome=(attempt.before.getCount()==1&&authoritative.isEmpty())||authoritative.getCount()==attempt.before.getCount()-1&&identity(authoritative).equals(identity(attempt.before));
            completeAction();
        }
    }

    public static void attemptUse(net.minecraft.world.InteractionHand hand){
        Minecraft mc=Minecraft.getInstance();if(mc.player==null||!config.tracking)return;ItemStack stack=mc.player.getItemInHand(hand);
        if(stack.is(Items.ENDER_PEARL)||stack.is(Items.WIND_CHARGE)||stack.is(Items.EXPERIENCE_BOTTLE)||stack.is(Items.SPLASH_POTION)||stack.is(Items.LINGERING_POTION))setAction(new LocalAction(stack,identity(stack),hand==net.minecraft.world.InteractionHand.OFF_HAND?40:@SELECTED_SLOT@,Observation.Action.THROW,Observation.Unit.THROWS,null));
        else if(stack.is(Items.CROSSBOW)){var charged=stack.get(DataComponents.CHARGED_PROJECTILES);if(charged!=null&&!charged.isEmpty()){
            ItemStack arrow=charged.getItems().getFirst();if(identity(arrow).categories().contains("arrows"))setAction(new LocalAction(stack,identity(arrow),hand==net.minecraft.world.InteractionHand.OFF_HAND?40:@SELECTED_SLOT@,Observation.Action.FIRE,Observation.Unit.SHOTS,null));}}
    }
    public static void attemptBowRelease(){Minecraft mc=Minecraft.getInstance();if(mc.player==null||!config.tracking||!mc.player.isUsingItem())return;ItemStack bow=mc.player.getUseItem();if(!bow.is(Items.BOW))return;
        ItemStack arrow=mc.player.getProjectile(bow);if(arrow.isEmpty())return;var hand=mc.player.getUsedItemHand();setAction(new LocalAction(bow,identity(arrow),hand==net.minecraft.world.InteractionHand.OFF_HAND?40:@SELECTED_SLOT@,Observation.Action.FIRE,Observation.Unit.SHOTS,null));}
    public static void attemptPlacement(net.minecraft.world.InteractionHand hand,BlockHitResult hit){Minecraft mc=Minecraft.getInstance();if(mc.player==null||!config.tracking)return;ItemStack stack=mc.player.getItemInHand(hand);if(!stack.is(Items.COBWEB))return;
        var context=new net.minecraft.world.item.context.BlockPlaceContext(mc.player,hand,stack,hit);var pos=context.getClickedPos();if(mc.level.getBlockState(pos).is(net.minecraft.world.level.block.Blocks.COBWEB))return;
        setAction(new LocalAction(stack,identity(stack),hand==net.minecraft.world.InteractionHand.OFF_HAND?40:@SELECTED_SLOT@,Observation.Action.PLACE,Observation.Unit.PLACEMENTS,pos));}
    private static void setAction(LocalAction action){if(localAction!=null)STORE.diagnostic(sequence,"Overlapping local attempts: prior unresolved action suppressed.");localAction=action;}
    public static void blockUpdate(ClientboundBlockUpdatePacket packet){Minecraft mc=Minecraft.getInstance();if(!mc.isSameThread()||localAction==null||localAction.target==null)return;
        if(packet.getPos().equals(localAction.target)&&packet.getBlockState().is(net.minecraft.world.level.block.Blocks.COBWEB)){localAction.block=true;completeAction();}}
    public static void cancelAttempts(){localAction=null;useSnapshot=null;heldReset=null;}
    private static void completeAction(){LocalAction a=localAction;Minecraft mc=Minecraft.getInstance();if(a==null||!a.outcome||!(a.action==Observation.Action.PLACE?a.block:a.projectile))return;
        localAction=null;if(config.tracking&&a.epoch==STORE.epoch()&&a.session.equals(STORE.session())&&enabled(a.counted))
            STORE.accept(new Observation(mc.player.getUUID(),a.counted,"local action",a.action,a.unit,1,"Captured local action + authoritative item outcome + "+(a.target==null?"player-owned projectile":"block outcome"),Observation.Validation.CONFIRMED,a.session,world,sequence,a.epoch,"local/"+a.id));
    }
    public static void spawn(ClientboundAddEntityPacket packet){
        Minecraft mc=Minecraft.getInstance();if(mc.isSameThread()&&mc.level!=null)ProjectileObservations.spawn(packet);
    }
    public static void projectileObserved(Player player,ItemIdentity item,boolean arrow,UUID projectile,UUID session,long epoch){
        Minecraft mc=Minecraft.getInstance();LocalAction a=localAction;
        if(player==mc.player&&a!=null&&a.session.equals(session)&&a.epoch==epoch){
            if(arrow&&a.action==Observation.Action.FIRE){a.outcome=true;a.projectile=true;completeAction();return;}
            if(!arrow&&a.action==Observation.Action.THROW&&a.counted.item().equals(item.item())){item=a.counted;localAction=null;}
        }

        if(arrow&&player==mc.player)return;
        if(config.tracking&&enabled(item))STORE.accept(new Observation(player.getUUID(),item,"projectile",arrow?Observation.Action.FIRE:Observation.Action.THROW,arrow?Observation.Unit.SHOTS:Observation.Unit.THROWS,1,"Observed player-owned projectile",Observation.Validation.CONFIRMED,session,world,++sequence,epoch,"projectile/"+projectile));
    }
    private static boolean consumable(ItemStack stack){
        String animation=stack.getUseAnimation().name();return animation.equals("EAT")||animation.equals("DRINK");
    }
    public static void remoteUseState(Player player){
        Minecraft mc=Minecraft.getInstance();if(!mc.isSameThread()||mc.level==null||mc.player==null||player==mc.player||!config.tracking||player.isRemoved()||mc.level.players().stream().noneMatch(loaded->loaded==player))return;
        if(player.isDeadOrDying()){remoteUses.remove(player.getUUID());return;}
        boolean using=player.isUsingItem();var hand=player.getUsedItemHand();var stack=player.getItemInHand(hand);
        var event=remoteUses.observe(player.getUUID(),hand.name(),using&&consumable(stack)?identity(stack):null,using&&consumable(stack)?stack.getUseDuration(player):using&&stack.isEmpty()?-1:0,using,ticks,STORE.session(),STORE.epoch(),world);
        if(event!=null&&enabled(event.item()))STORE.accept(event);
    }
    public static void equipment(ClientboundSetEquipmentPacket packet){
        Minecraft mc=Minecraft.getInstance();if(mc.isSameThread()&&mc.level!=null&&mc.level.getEntity(packet.getEntity()) instanceof Player player)remoteUseState(player);
    }
    public static void entityRemoved(int id){var mc=Minecraft.getInstance();if(mc.level!=null){var entity=mc.level.getEntity(id);if(entity!=null){remoteUses.remove(entity.getUUID());CustomLabels.remove(entity.getUUID());}}}
    private static void requestReset(String id){Minecraft mc=Minecraft.getInstance();Config.ResetAction action=config.resetActions.stream().filter(a->a.id.equals(id)).findFirst().orElse(null);
        if(id.equals("all")?config.confirmResetAll:action!=null&&action.confirm){mc.setScreen(new ConfirmDialog(ok->{mc.setScreen(null);if(ok)performReset(id);},Component.literal("Reset Usage?"),Component.literal("Only the selected usage tallies will change. Inventory remains intact.")));}else performReset(id);}
    private static void performReset(String id){if(id.equals("all")){STORE.resetAll();cycles.values().forEach(Cycle::reset);Tags.clear();hint("All tracked usage reset.");}else resetAction(id);}
    public static void resetAction(String id){
        Config.ResetAction a=config.resetActions.stream().filter(x->x.id.equals(id)).findFirst().orElse(null);if(a==null)return;
        if(a.scope==Config.Scope.UNRESOLVED){hint("Reset target requires local assignment.");return;}
        List<Selector> selectors=new ArrayList<>();
        if(a.all)selectors.add(new Selector(null,null,null,null,null,null,null));
        else {Config.Profile p=config.profiles.stream().filter(x->x.id.equals(a.profileId)).findFirst().orElse(null);if(p==null){hint("Reset disabled: profile is missing.");return;}
            List<String> definitions=a.definitions;if(a.groupId!=null){Config.Group g=p.group(a.groupId);if(g==null){hint("Reset disabled: group is missing.");return;}definitions=g.members.stream().map(p::counter).filter(Objects::nonNull).map(c->c.definition).toList();}
            for(String d:definitions){Config.Definition definition=p.definition(d);if(definition==null){hint("Reset disabled: counter is missing.");return;}selectors.add(definition.selector);}}
        if(selectors.isEmpty()){hint("Reset disabled: no valid targets.");return;}
        UUID own=Minecraft.getInstance().player.getUUID();STORE.reset(u->switch(a.scope){case YOURSELF->u.equals(own);case SELECTED->a.players.contains(u.toString());case EVERYONE->true;case OTHERS->!u.equals(own);default->false;},selectors);
        cycles.values().forEach(Cycle::reset);hint(a.name+": usage reset.");
    }
    public static void hint(String text){Minecraft mc=Minecraft.getInstance();if(mc.player!=null)mc.player.displayClientMessage(Component.literal("Tallium · "+text),true);}
    public static void resetGroup(String id,List<Selector> selectors){STORE.reset(u->true,selectors);Cycle cycle=cycles.get(id);if(cycle!=null)cycle.reset();Tags.resetGroup(id);}
    public static int usageColor(Config.Definition d,Value value){Config.Counter c=config.active().counters.stream().filter(x->x.definition.equals(d.id)&&x.used!=null).findFirst().orElse(null);return value.hasCount()?(c==null?ColorRules.used(d.family):c.used).color(value.count()):0xffaaaaaa;}
    public static Player target(){
        Minecraft mc=Minecraft.getInstance();if(mc.level==null||mc.player==null)return null;Entity camera=mc.getCameraEntity();if(camera==null)return null;
        var view=mc.gameRenderer.@MAIN_CAMERA@();var forward=view.@CAMERA_FORWARD@();Vec3 start=view.@CAMERA_POSITION@(),direction=new Vec3(forward.x(),forward.y(),forward.z()).normalize();
        double radius=config.inspectionRenderDistance?mc.options.renderDistance().get()*16.0:config.inspectionRange;
        Vec3 end=start.add(direction.scale(radius));Player best=null;double score=Double.POSITIVE_INFINITY;
        for(Player p:mc.level.players()){
            if(p==mc.player||p.isSpectator()||!p.isAlive()||p.isInvisibleTo(mc.player))continue;
            Optional<Vec3> hit=p.getBoundingBox().clip(start,end);Vec3 aim=hit.orElse(p.getEyePosition());Vec3 offset=aim.subtract(start);double distance=offset.length();
            double candidate=dev.sig.tallium.display.Targeting.score(distance<.001?1:direction.dot(offset.scale(1/distance)),distance,radius,config.inspectionAngle,hit.isPresent());
            if(candidate>=score)continue;
            BlockHitResult block=mc.level.clip(new ClipContext(start,aim,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,camera));
            if(block.getType()!=HitResult.Type.MISS&&start.distanceToSqr(block.getLocation())+.01<start.distanceToSqr(aim))continue;
            score=candidate;best=p;
        }return best;
    }
    private static void inspectionMiss(){
        PlayerPopup.clear();
        hint("No visible player nearby in that direction.");
        if(config.inspectionMissSound)Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BASS.value(),.65f));
    }
    private static final Map<String,List<Component>> summaries=new LinkedHashMap<>();
    public static List<String> summary(UUID player,int cap){return summaryComponents(player,cap).stream().map(Component::getString).toList();}
    private static Component summaryItem(UUID player,Config.Definition d,boolean child){Value value=usage(player,d);return Component.literal((child?"  ↳ ":"")+d.label+": ").append(Component.literal(value.text()).withStyle(style->style.withColor(usageColor(d,value)&0xffffff)));}
    private static boolean categoryContains(Config.Definition broad,Config.Definition child){String category=broad.selector.category();if(category==null||child==broad||broad.selector.item()!=null||broad.selector.potion()!=null||broad.selector.effect()!=null||broad.selector.amplifier()!=null||broad.selector.duration()!=null||broad.selector.exactComponents()!=null)return false;
        if(child.selector.category()!=null)return false;String item=child.selector.item();if(item==null)return false;
        return switch(category){case "food"->child.family.equals("food");case "potions"->Set.of("minecraft:potion","minecraft:splash_potion","minecraft:lingering_potion").contains(item);case "tipped_arrows"->item.equals("minecraft:tipped_arrow");default->false;};}
    public static List<Component> summaryComponents(UUID player,int cap){String key=player+"/"+STORE.session()+"/"+cap+"/"+config.active().id+"/"+STORE.revision()+"/"+config.tracking+"/"+ticks/20;List<Component> cached=summaries.get(key);if(cached!=null)return cached;
        SeenPlayer seen=roster.get(player);List<Component> lines=new ArrayList<>();lines.add(Component.literal("Tallium · "+(seen==null?"Unknown player":seen.name())+" · Observed uses"));
        lines.add(Component.literal(java.time.LocalTime.now().withNano(0)+" · session "+STORE.session().toString().substring(0,8)+" · reset period "+STORE.epoch()));
        List<Config.Definition> defs=config.active().definitions.stream().filter(d->d.enabled).toList(),ordered=new ArrayList<>();Set<String> children=new HashSet<>();
        for(Config.Definition broad:defs)if(broad.selector.category()!=null){ordered.add(broad);for(Config.Definition child:defs)if(categoryContains(broad,child)&&children.add(child.id))ordered.add(child);}
        for(Config.Definition d:defs)if(!ordered.contains(d))ordered.add(d);
        int rows=0;for(Config.Definition d:ordered){if(rows++>=cap)break;lines.add(summaryItem(player,d,children.contains(d.id)));}
        if(defs.size()>cap)lines.add(Component.literal("+"+(defs.size()-cap)+" rows · Open "+OPEN.getTranslatedKeyMessage().getString()+" → Players"));
        if(cap>=512){Map<ItemIdentity,Long> variants=new TreeMap<>(Comparator.comparing(ItemIdentity::key));
            STORE.snapshot().forEach((bucket,count)->{if(bucket.player().equals(player)&&defs.stream().anyMatch(d->d.selector.matches(bucket.item())&&usage(player,d).hasCount()))variants.merge(bucket.item(),count,Long::sum);});
            if(!variants.isEmpty()){lines.add(Component.literal("Canonical variant breakdown (one row per item identity)"));variants.forEach((identity,count)->lines.add(Component.literal(identity.item()+(identity.potion().isEmpty()?"":" · "+identity.potion())+(identity.effects().isEmpty()?"":" · "+identity.effects())+": "+count)));}}
        lines.add(Component.literal("Only observed activity is included. Category rows include their breakdown; overlapping rows are not additive."));
        List<Component> result=List.copyOf(lines);if(summaries.size()>=64)summaries.remove(summaries.keySet().iterator().next());summaries.put(key,result);return result;
    }
    public static void inspect(Player player){Minecraft mc=Minecraft.getInstance();if(player==null){inspectionMiss();return;}
        for(Component line:summaryComponents(player.getUUID(),config.summaryRows))mc.gui.getChat().addMessage(line);}
    public static void render(GuiGraphics graphics){
        Minecraft mc=Minecraft.getInstance();if(mc.player==null||mc.options.hideGui)return;
        renderXp(graphics);if(mc.screen instanceof LayoutScreen)return;PlayerPopup.render(graphics,config.active(),false);if(!config.active().hud)return;
        Config.Profile p=config.active();for(Config.Group g:p.groups.stream().filter(x->x.enabled&&x.surface==Config.Surface.HUD).sorted(Comparator.comparingInt(x->x.placement.z)).toList()){
            List<Config.Counter> members=g.members.stream().map(p::counter).filter(Objects::nonNull).filter(c->{Config.Definition d=p.definition(c.definition);return d!=null&&(g.mode==Config.Mode.TOTAL||d.enabled&&!d.unavailable);}).toList();
            boolean showEmpty=false;
            if(g.mode==Config.Mode.CYCLE){String id=cycles.computeIfAbsent(g.id,k->new Cycle()).select(members.stream().map(c->c.id).toList(),c->{Value v=visibilityValue(p,g,p.counter(c));return v.state()!=Value.State.UNSUPPORTED&&(!g.skipZero||!v.hasCount()||v.count()!=0);},g.itemInterval(),System.nanoTime(),mc.isPaused());
                if(id==null&&!g.hideEmpty&&!members.isEmpty()){members=List.of(members.getFirst());showEmpty=true;}else members=id==null?List.of():List.of(p.counter(id));}
            if(g.mode!=Config.Mode.TOTAL){boolean zeros=showEmpty;members=members.stream().filter(c->{Value v=visibilityValue(p,g,c);return v.state()!=Value.State.UNSUPPORTED&&(zeros||!g.appearance.hideZero||!v.hasCount()||v.count()!=0);}).toList();}
            if(members.isEmpty())continue;
            int x=g.placement.anchor==Config.Anchor.SCREEN?g.placement.x:graphics.guiWidth()/2+g.placement.x;
            int y=anchorY(g,graphics.guiHeight())+g.placement.y;
            GroupBounds bounds=fit(measureBounds(p,g,members,false),graphics.guiWidth(),graphics.guiHeight());double renderScale=bounds.scale();int inset=(int)Math.ceil(g.appearance.padding*renderScale);
            x=Math.max(inset,Math.min(graphics.guiWidth()-bounds.width()+inset,x-bounds.width()/2+inset));y=Math.max(inset,Math.min(graphics.guiHeight()-bounds.height()+inset,y-bounds.height()/2+inset));
            @POSE_PUSH@
            @POSE_TRANSLATE@
            @POSE_SCALE@
            int n=0,nextX=0;
            if(g.mode==Config.Mode.TOTAL){List<Selector> selectors=members.stream().map(c->p.definition(c.definition).selector).toList();Config.Counter first=members.getFirst();
                Value v=scopedValue(first,selectors,members.stream().map(c->p.definition(c.definition)).toList());
                ColorRules totalRules=g.override!=null?g.override:first.metric==Config.Metric.REMAINING?ColorRules.remaining("other"):ColorRules.used("other");
                drawEntry(graphics,p,g,p.definition(first.definition),v,0,0,totalRules.color(v.count()),false);
            }else for(Config.Counter c:members){Value v=displayValue(p,g,c,false);if(v.state()==Value.State.UNSUPPORTED||visibilityValue(p,g,c).state()==Value.State.KNOWN&&visibilityValue(p,g,c).count()==0&&g.appearance.hideZero&&!showEmpty)continue;Config.Definition d=p.definition(c.definition);
                int cell=cellWidth(p,g,false),dx=g.mode==Config.Mode.GRID?(n%g.columns)*cell:nextX,dy=g.mode==Config.Mode.GRID?(n/g.columns)*rowHeight(g):0;
                ColorRules rules=remainingDisplay(g,c)?c.remaining:c.used;if(rules==null)rules=remainingDisplay(g,c)?ColorRules.remaining(d.family):ColorRules.used(d.family);
                drawEntry(graphics,p,g,d,v,dx,dy,g.override!=null?g.override.color(v.count()):rules.color(v.count()),false);nextX+=entryWidth(p,g,c,false)+g.appearance.spacing;n++;}
            @POSE_POP@
        }
    }
    private static int entryWidth(Config.Profile p,Config.Group group,Config.Counter c,boolean preview){
        return entryWidth(p,group,c,displayValue(p,group,c,preview),preview);
    }
    private static int entryWidth(Config.Profile p,Config.Group group,Config.Counter c,Value shown,boolean preview){
        var d=p.definition(c.definition);if(d==null)return group.appearance.iconSize;
        String text=(group.appearance.labels?d.label+": ":"")+numberText(group,d,shown,preview);
        int textWidth=(int)Math.ceil(Minecraft.getInstance().font.width(text)*group.appearance.textScale);return group.appearance.overlayNumber?Math.max(group.appearance.iconSize,textWidth):group.appearance.iconSize+group.appearance.spacing+textWidth;
    }
    private static int cellWidth(Config.Profile p,Config.Group group,boolean preview){
        return group.members.stream().map(p::counter).filter(Objects::nonNull).mapToInt(c->entryWidth(p,group,c,preview)).max().orElse(group.appearance.iconSize)+group.appearance.padding*2+group.appearance.spacing;
    }
    private static int rowHeight(Config.Group g){if(g.appearance.overlayNumber)return g.appearance.iconSize+2+g.appearance.padding*2+g.appearance.spacing;return Math.max(g.appearance.iconSize,(int)Math.ceil(Minecraft.getInstance().font.lineHeight*g.appearance.textScale))+g.appearance.padding*2+g.appearance.spacing;}
    public static int anchorY(Config.Group g,int height){
        if(g.placement.anchor==Config.Anchor.SCREEN)return 0;
        if(g.placement.anchor==Config.Anchor.HOTBAR)return height-22;
        Minecraft mc=Minecraft.getInstance();return height-38-mc.font.lineHeight+(g.appearance.overlayNumber?9:8)-(mc.player!=null&&mc.player.experienceLevel>0?6:0);
    }
    public record GroupBounds(int width,int height,double scale) {}
    private static GroupBounds measureBounds(Config.Profile p,Config.Group g,List<Config.Counter> members,boolean preview){
        int totalWidth=0;
        if(g.mode==Config.Mode.TOTAL&&!members.isEmpty()){
            Value shown;if(preview){Set<String> identities=new HashSet<>();long amount=0;int index=0;for(var c:members){var d=p.definition(c.definition);if(identities.add(identity(representative(d)).key()))amount+=12+index;index++;}shown=Value.known(amount);}
            else shown=scopedValue(members.getFirst(),members.stream().map(c->p.definition(c.definition).selector).toList(),members.stream().map(c->p.definition(c.definition)).toList());
            totalWidth=entryWidth(p,g,members.getFirst(),shown,preview)+g.appearance.padding*2;
        }
        if((g.mode==Config.Mode.TOTAL||g.mode==Config.Mode.CYCLE)&&!members.isEmpty())members=List.of(members.getFirst());
        int count=Math.max(1,members.size()),columns=g.mode==Config.Mode.GRID?Math.min(g.columns,count):count,rows=g.mode==Config.Mode.GRID?(count+g.columns-1)/g.columns:1;
        int w=g.mode==Config.Mode.GRID?columns*cellWidth(p,g,preview)-g.appearance.spacing:
            members.stream().mapToInt(c->entryWidth(p,g,c,preview)).sum()+Math.max(0,count-1)*g.appearance.spacing+g.appearance.padding*2;
        if(totalWidth>0)w=totalWidth;
        int h=rows*rowHeight(g)-g.appearance.spacing;
        return new GroupBounds(Math.max(1,(int)Math.ceil(w*g.placement.scale)),Math.max(1,(int)Math.ceil(h*g.placement.scale)),g.placement.scale);
    }
    public static GroupBounds groupBounds(Config.Profile p,Config.Group g,boolean preview){
        List<Config.Counter> members=g.members.stream().map(p::counter).filter(Objects::nonNull).filter(c->{var d=p.definition(c.definition);return d!=null&&d.enabled&&!d.unavailable;}).toList();
        if(g.mode==Config.Mode.CYCLE&&!members.isEmpty())members=List.of(members.getFirst());
        return measureBounds(p,g,members,preview);
    }
    private static GroupBounds fit(GroupBounds b,int width,int height){double scale=Math.min(1,Math.min(Math.max(1,width)/(double)b.width(),Math.max(1,height)/(double)b.height()));return new GroupBounds((int)Math.ceil(b.width()*scale),(int)Math.ceil(b.height()*scale),b.scale()*scale);}
    public static GroupBounds fittedBounds(Config.Profile p,Config.Group g,boolean preview,int width,int height){return fit(groupBounds(p,g,preview),width,height);}
    private static Config.Display display(Config.Group g){return g.appearance.twoValues?Config.Display.BOTH:g.appearance.display;}
    private static boolean remainingDisplay(Config.Group g,Config.Counter c){return switch(display(g)){case COUNTER->c.metric==Config.Metric.REMAINING;case REMAINING,BOTH->true;case TOTAL->false;case CYCLE->!"TOTAL".equals(cycles.computeIfAbsent(g.id+":display",key->new Cycle()).select(List.of("REMAINING","TOTAL"),key->true,g.interval,System.nanoTime(),Minecraft.getInstance().isPaused()));};}
    private static Value visibilityValue(Config.Profile p,Config.Group g,Config.Counter c){
        Value value=displayValue(p,g,c,false);if(display(g)!=Config.Display.BOTH&&display(g)!=Config.Display.CYCLE)return value;
        var own=Minecraft.getInstance().player;var d=p.definition(c.definition);if(own==null||d==null||!value.hasCount())return value;
        Value used=usage(own.getUUID(),d);long remaining=inventory.total(List.of(d.selector),config.inventoryScope.equals("HOTBAR"));return used.hasCount()?Value.known(Math.min(Long.MAX_VALUE-used.count(),remaining)+used.count()):Value.unknown("Some uses cannot be observed.");
    }
    private static Value displayValue(Config.Profile p,Config.Group g,Config.Counter c,boolean preview){
        if(display(g)==Config.Display.COUNTER)return preview?Value.known(12):value(p,c);
        if(preview)return Value.known(remainingDisplay(g,c)?14:12);
        var d=p.definition(c.definition);if(d==null||!d.enabled||d.unavailable)return Value.unsupported("Unavailable item.");
        if(remainingDisplay(g,c))return Value.known(inventory.total(List.of(d.selector),config.inventoryScope.equals("HOTBAR")));
        var own=Minecraft.getInstance().player;return own==null?Value.unknown("No player."):usage(own.getUUID(),d);
    }
    private static String format(Config.Group g,Value v){return g.appearance.numberFormat.equals("COMPACT")&&v.hasCount()&&v.count()>=1000?String.format(java.util.Locale.ROOT,"%.1fk",v.count()/1000.0):v.text();}
    private static String numberText(Config.Group group,Config.Definition d,Value value,boolean preview){
        if(display(group)!=Config.Display.BOTH)return format(group,value);
        Minecraft mc=Minecraft.getInstance();Value used=preview?Value.known(12):mc.player==null?Value.unknown("No player"):usage(mc.player.getUUID(),d);
        return format(group,Value.known(preview?14:inventory.total(List.of(d.selector),config.inventoryScope.equals("HOTBAR"))))+" | "+format(group,used);
    }
    private static void renderXp(GuiGraphics graphics){
        Minecraft mc=Minecraft.getInstance();Config.Profile p=config.active();if(!p.coloredXp||p.xpSource==null||mc.gameMode==null||!mc.gameMode.hasExperience())return;
        ColorRules groupOverride=null;Config.Group sourceGroup=null;Config.Counter source=p.counter(p.xpSource);if(source==null){Config.Group group=p.group(p.xpSource);if(group==null||group.mode!=Config.Mode.CYCLE)return;sourceGroup=group;groupOverride=group.override;
            String id=cycles.computeIfAbsent(group.id,k->new Cycle()).select(group.members,c->{Config.Counter counter=p.counter(c);if(counter==null)return false;Value v=visibilityValue(p,group,counter);return v.state()!=Value.State.UNSUPPORTED&&(!group.skipZero||!v.hasCount()||v.count()!=0);},group.itemInterval(),System.nanoTime(),mc.isPaused());source=id==null?null:p.counter(id);}
        if(source==null)return;Value v=sourceGroup==null?value(source):displayValue(p,sourceGroup,source,false);if(!v.hasCount()||v.count()==0&&!p.alwaysColoredXp)return;
        Config.Definition d=p.definition(source.definition);ColorRules rules=(sourceGroup==null?source.metric==Config.Metric.REMAINING:remainingDisplay(sourceGroup,source))?source.remaining:source.used;if(groupOverride!=null)rules=groupOverride;if(rules==null)return;
        int color=rules.color(v.count()),x=graphics.guiWidth()/2-91,y=graphics.guiHeight()-@XP_OFFSET@,progress=(int)(mc.player.experienceProgress*183);
        graphics.fill(x,y,x+182,y+5,0xff222222);if(progress>0)graphics.fill(x,y,x+Math.min(182,progress),y+5,color);
    }
    public static Value value(Config.Counter c){return value(config.active(),c);}
    public static Value value(Config.Profile profile,Config.Counter c){
        Config.Definition d=profile.definition(c.definition);if(d==null)return Value.unsupported("Missing definition.");
        return scopedValue(c,List.of(d.selector),List.of(d));
    }
    private static Value scopedValue(Config.Counter c,List<Selector> selectors,List<Config.Definition> definitions){
        Minecraft mc=Minecraft.getInstance();if(mc.player==null)return Value.unknown("No local player.");UUID own=mc.player.getUUID();
        if(c.scope==Config.Scope.UNRESOLVED)return Value.unsupported("Assign imported player targets locally.");
        if(definitions.stream().anyMatch(d->d.unavailable||!d.enabled))return Value.unsupported("A selected item is unavailable or disabled.");
        if(c.metric==Config.Metric.REMAINING)return c.scope==Config.Scope.YOURSELF?Value.known(inventory.total(selectors,config.inventoryScope.equals("HOTBAR"))):Value.unsupported("Another player's inventory is not observable.");
        boolean remote=c.scope==Config.Scope.OTHERS||c.scope==Config.Scope.EVERYONE||c.scope==Config.Scope.SELECTED&&c.players.stream().anyMatch(id->!id.equals(own.toString()));
        if(remote)for(Config.Definition d:definitions){var capability=CAPABILITIES.forFamily(consumable(representative(d))&&!d.family.equals("potions")?"food":d.family);if(capability.remote()==Capabilities.Support.UNSUPPORTED)return Value.unsupported(capability.gap());}
        List<UUID> targets=c.scope==Config.Scope.YOURSELF?List.of(own):roster.keySet().stream().filter(id->switch(c.scope){case EVERYONE->true;case OTHERS->!id.equals(own);case SELECTED->c.players.contains(id.toString());default->false;}).toList();
        if(c.scope==Config.Scope.SELECTED&&(c.players.isEmpty()||targets.size()!=c.players.size()))return Value.unknown("Selected players have not all entered tracking range.");
        for(UUID target:targets)for(Config.Definition d:definitions){Value v=usage(target,d);if(!v.hasCount())return v;}
        long total=0;boolean estimated=false;for(UUID target:targets){total+=STORE.total(target,selectors);estimated|=STORE.estimated(target,selectors)||definitions.stream().anyMatch(d->usage(target,d).state()==Value.State.ESTIMATED);}return estimated?Value.estimated(total):Value.known(total);
    }
    public static boolean counterIncludes(Config.Counter c,UUID player){Minecraft mc=Minecraft.getInstance();if(mc.player==null)return false;boolean own=mc.player.getUUID().equals(player);return switch(c.scope){case YOURSELF->own;case OTHERS->!own;case EVERYONE->true;case SELECTED->c.players.contains(player.toString());case UNRESOLVED->false;};}
    public static String iconMode(Config.Profile p,Config.Group g){return !g.appearance.iconMode.equals("INHERIT")?g.appearance.iconMode:switch(g.surface){case HUD->p.hudIconMode;case NAMETAG->p.nametagIconMode;case PLAYER_LIST->p.playerListIconMode;};}
    public static long lastSeenSeconds(SeenPlayer player){return Math.max(0,(ticks-player.lastSeen())/20);}
    private static void drawEntry(GuiGraphics g,Config.Profile profile,Config.Group group,Config.Definition d,Value value,int x,int y,int color,boolean preview){
        Minecraft mc=Minecraft.getInstance();int alpha=(int)(group.appearance.opacity*255);if(!value.hasCount())color=0xffffffff;color=(color&0xffffff)|(alpha<<24);
        String number=numberText(group,d,value,preview);
        String label=(group.appearance.labels?d.label+": ":"")+number;int w=group.appearance.iconSize+group.appearance.spacing+(int)Math.ceil(mc.font.width(label)*group.appearance.textScale);
        if(group.appearance.overlayNumber)w=Math.max(group.appearance.iconSize,(int)Math.ceil(mc.font.width(label)*group.appearance.textScale));
        if(group.appearance.background)HudShapes.panel(g,x-group.appearance.padding,y-group.appearance.padding,w+group.appearance.padding*2,(group.appearance.overlayNumber?group.appearance.iconSize+2:Math.max(group.appearance.iconSize,(int)Math.ceil(mc.font.lineHeight*group.appearance.textScale)))+group.appearance.padding*2,group.appearance.radius,((int)(alpha*group.appearance.backgroundOpacity)<<24)|group.appearance.backgroundColor);
        int iconX=x+(group.appearance.overlayNumber?(w-group.appearance.iconSize)/2:0);

        if(group.appearance.iconSize==16){
            if(iconMode(profile,group).equals("RESOURCE_PACK"))g.renderItem(representative(d),iconX,y);else VanillaIcons.draw(g,representative(d),iconX,y);
        }else{
            @ENTRY_PUSH@ @ICON_TRANSLATE@ @ENTRY_SCALE@
            if(iconMode(profile,group).equals("RESOURCE_PACK"))g.renderItem(representative(d),0,0);else VanillaIcons.draw(g,representative(d),0,0);
            @ENTRY_POP@
        }

        int textX=group.appearance.overlayNumber?iconX+group.appearance.iconSize/2-(int)Math.ceil(mc.font.width(label)*group.appearance.textScale)/2:x+group.appearance.iconSize+group.appearance.spacing,textY=group.appearance.overlayNumber?y+group.appearance.iconSize+2-(int)Math.ceil(mc.font.lineHeight*group.appearance.textScale):y+Math.max(0,(group.appearance.iconSize-(int)(mc.font.lineHeight*group.appearance.textScale))/2);
        int drawX=textX,drawY=textY;
        @ENTRY_PUSH@
        if(group.appearance.textScale!=1){@TEXT_TRANSLATE@ @TEXT_SCALE@ drawX=0;drawY=0;}
        if(display(group)==Config.Display.BOTH&&number.contains(" | ")){
            var counter=profile.counters.stream().filter(c->c.definition.equals(d.id)).findFirst().orElse(null);
            ColorRules remaining=counter!=null&&counter.remaining!=null?counter.remaining:ColorRules.remaining(d.family),used=counter!=null&&counter.used!=null?counter.used:ColorRules.used(d.family);
            long left=preview?14:inventory.total(List.of(d.selector),config.inventoryScope.equals("HOTBAR"));Value spent=preview?value:mc.player==null?Value.known(0):usage(mc.player.getUUID(),d);
            String first=(group.appearance.labels?d.label+": ":"")+number.substring(0,number.indexOf(" | ")),second=number.substring(number.indexOf(" | "));
            int leftColor=((group.override==null?remaining:group.override).color(left)&0xffffff)|(alpha<<24),usedColor=((group.override==null?used:group.override).color(spent.count())&0xffffff)|(alpha<<24);
            g.drawString(mc.font,first,drawX,drawY,leftColor,group.appearance.shadow);g.drawString(mc.font,second,drawX+mc.font.width(first),drawY,usedColor,group.appearance.shadow);
        }else g.drawString(mc.font,label,drawX,drawY,color,group.appearance.shadow);
        @ENTRY_POP@
    }
    public static void previewGroup(GuiGraphics graphics,Config.Profile profile,Config.Group source,int x,int y){
        previewGroup(graphics,profile,source,x,y,graphics.guiWidth(),graphics.guiHeight());
    }
    public static void previewGroup(GuiGraphics graphics,Config.Profile profile,Config.Group source,int x,int y,int maxWidth,int maxHeight){
        Config.Group g=source;boolean sample=Minecraft.getInstance().level==null;double renderScale=fittedBounds(profile,g,sample,maxWidth,maxHeight).scale();
        List<Config.Counter> members=g.members.stream().map(profile::counter).filter(Objects::nonNull).toList();
        long sampleTotal=0;java.util.Set<String> sampleIdentities=new java.util.HashSet<>();int sampleIndex=0;for(Config.Counter c:members){Config.Definition d=profile.definition(c.definition);if(d!=null&&sampleIdentities.add(identity(representative(d)).key()))sampleTotal+=12+sampleIndex;sampleIndex++;}
        List<Config.Counter> totalMembers=members;
        if((g.mode==Config.Mode.CYCLE||g.mode==Config.Mode.TOTAL)&&!members.isEmpty())members=List.of(members.getFirst());
        @POSE_PUSH@ @POSE_TRANSLATE@ @POSE_SCALE@
        int n=0,nextX=0;for(Config.Counter c:members){Config.Definition d=profile.definition(c.definition);if(d==null)continue;int cell=cellWidth(profile,g,sample),dx=g.mode==Config.Mode.GRID?(n%g.columns)*cell:nextX,dy=g.mode==Config.Mode.GRID?(n/g.columns)*rowHeight(g):0;
            long amount=g.mode==Config.Mode.TOTAL?sampleTotal:12+n;Value shown=sample?(g.mode==Config.Mode.TOTAL?Value.known(amount):displayValue(profile,g,c,true)):g.mode==Config.Mode.TOTAL?scopedValue(c,totalMembers.stream().map(member->profile.definition(member.definition).selector).toList(),totalMembers.stream().map(member->profile.definition(member.definition)).toList()):displayValue(profile,g,c,false);ColorRules rules=g.override!=null?g.override:g.mode==Config.Mode.TOTAL?(c.metric==Config.Metric.REMAINING?ColorRules.remaining("other"):ColorRules.used("other")):remainingDisplay(g,c)?c.remaining:c.used;if(rules==null)rules=ColorRules.used(d.family);drawEntry(graphics,profile,g,d,shown,dx,dy,rules.color(shown.count()),sample);nextX+=entryWidth(profile,g,c,sample)+g.appearance.spacing;n++;}
        @POSE_POP@
    }
}

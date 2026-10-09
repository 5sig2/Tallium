import dev.sig.tallium.config.*;
import dev.sig.tallium.display.*;
import dev.sig.tallium.items.*;
import dev.sig.tallium.inventory.*;
import dev.sig.tallium.tracking.*;
import java.util.*;
import java.nio.file.*;
import java.io.*;
import java.util.zip.*;
import java.nio.charset.StandardCharsets;

public class CoreVerification {
    static int checks;
    static void check(boolean condition,String message){checks++;if(!condition)throw new AssertionError(message);}
    static ItemIdentity pearl=new ItemIdentity("minecraft:ender_pearl","",List.of(),"",Set.of());
    static Observation event(CounterStore store,UUID player,ItemIdentity item,String key,Observation.Validation state,long epoch){
        return new Observation(player,item,"",Observation.Action.THROW,Observation.Unit.THROWS,1,"verification",state,store.session(),"overworld",1,epoch,key);
    }
    static void invalid(String code){try{ShareCode.decode(code);throw new AssertionError("Invalid code accepted");}catch(IOException expected){checks++;}}
    static String pack(String json)throws Exception{ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(var out=new DeflaterOutputStream(bytes)){out.write(json.getBytes(StandardCharsets.UTF_8));}byte[] data=bytes.toByteArray();CRC32 crc=new CRC32();crc.update(data);return "TLM1:"+Base64.getUrlEncoder().withoutPadding().encodeToString(data)+"."+String.format("%08x",crc.getValue());}
    public static void main(String[] args)throws Exception{
        var player=UUID.randomUUID();var other=UUID.randomUUID();var store=new CounterStore();var selector=Selector.item(pearl.item());
        check(!store.accept(event(store,player,pearl,"pending",Observation.Validation.PENDING,0)),"Pending entered count");
        check(!store.accept(event(store,player,pearl,"rejected",Observation.Validation.REJECTED,0)),"Rejected entered count");
        check(store.accept(event(store,player,pearl,"a",Observation.Validation.CONFIRMED,0)),"Confirmed rejected");
        check(!store.accept(event(store,player,pearl,"a",Observation.Validation.CONFIRMED,0)),"Duplicate counted");
        check(store.total(player,List.of(selector,selector))==1,"Overlapping total duplicated");
        store.reset(player::equals,List.of(selector));check(store.total(player,List.of(selector))==0,"Reset failed");
        check(!store.accept(event(store,player,pearl,"a",Observation.Validation.CONFIRMED,store.epoch())),"Reset lost identity");
        check(!store.accept(event(store,player,pearl,"old pending",Observation.Validation.CONFIRMED,0)),"Pending leaked across reset");
        var strength=new ItemIdentity("minecraft:splash_potion","minecraft:strong_strength",List.of(new ItemIdentity.Effect("minecraft:strength",1,1800)),"strong",Set.of("potions"));
        var speed=new ItemIdentity("minecraft:splash_potion","minecraft:swiftness",List.of(new ItemIdentity.Effect("minecraft:speed",0,3600)),"speed",Set.of("potions"));
        Selector potions=new Selector(null,"potions",null,null,null,null,null),strong=new Selector("minecraft:splash_potion",null,"minecraft:strong_strength",null,null,null,null);
        store.accept(event(store,player,strength,"s",Observation.Validation.CONFIRMED,store.epoch()));store.accept(event(store,player,speed,"v",Observation.Validation.CONFIRMED,store.epoch()));store.accept(event(store,other,strength,"o",Observation.Validation.CONFIRMED,store.epoch()));
        check(store.total(player,List.of(potions,strong))==2,"Broad/narrow union duplicated");store.reset(player::equals,List.of(strong));
        check(new Selector("minecraft:splash_potion",null,null,null,1,1800,null).matches(strength)&&!new Selector("minecraft:splash_potion",null,null,null,0,3600,null).matches(strength),"Amplifier/duration without effect filter ignored");
        check(store.total(player,List.of(potions))==1&&store.total(other,List.of(potions))==1,"Variant/player reset broadened");
        var inventory=new InventorySnapshot(List.of(new InventorySnapshot.Slot(0,strength,4,true,false),new InventorySnapshot.Slot(40,strength,2,false,true),new InventorySnapshot.Slot(9,speed,8,false,false)));
        check(inventory.total(List.of(potions,strong),false)==14,"Inventory overlap/offhand failed");check(inventory.total(List.of(potions),true)==4,"Hotbar scope wrong");
        var colors=ColorRules.used("pearls");int[] values={0,15,16,31,32,47,48,63,64,999};int[] rgb={0x55ff55,0x55ff55,0x00aa00,0x00aa00,0xffff55,0xffff55,0xffaa00,0xffaa00,0xff5555,0xff5555};
        for(int n=0;n<values.length;n++)check((colors.color(values[n])&0xffffff)==rgb[n],"Color boundary "+values[n]);
        var cycle=new Cycle();check("a".equals(cycle.select(List.of("a","b"),x->true,5,1,false)),"Cycle activation");
        check("b".equals(cycle.select(List.of("a","b"),x->true,5,5_000_000_001L,false)),"Cycle interval");
        check("b".equals(cycle.select(List.of("a","b"),x->true,5,15_000_000_001L,true)),"Paused cycle advanced");
        check("a".equals(cycle.select(List.of("a"),x->true,5,15_100_000_001L,false)),"Removed cycle member");
        check(cycle.select(List.of("a"),x->false,5,15_200_000_001L,false)==null,"All empty cycle");
        Config starter=Config.defaults();starter.validate();
        check(starter.active().definitions.size()==5,"Starter must contain five items");
        check(starter.active().definitions.stream().anyMatch(d->d.selector.item().equals("minecraft:experience_bottle")&&d.family.equals("experience")),"XP bottle starter missing");
        check(starter.active().groups.getFirst().mode==Config.Mode.CYCLE&&!starter.active().groups.getFirst().appearance.twoValues&&starter.active().groups.getFirst().appearance.display==Config.Display.REMAINING&&starter.active().groups.getFirst().appearance.overlayNumber&&!starter.active().groups.getFirst().appearance.hideZero,"Starter must cycle remaining in TotemCounter layout");
        check(starter.active().nametagCap==5,"All starter types must fit on nametags");
        var timed=Config.defaults().active().groups.getFirst();check(timed.itemInterval()==5,"Normal item cycle duration changed");timed.appearance.display=Config.Display.CYCLE;check(timed.itemInterval()==10,"Alternating measurements need two phases per item");
        var itemClock=new Cycle();var metricClock=new Cycle();for(int n=0;n<4;n++){long now=1+n*5_000_000_000L;String item=itemClock.select(List.of("pearl","totem"),key->true,timed.itemInterval(),now,false);String metric=metricClock.select(List.of("remaining","total"),key->true,timed.interval,now,false);check(item.equals(n<2?"pearl":"totem")&&metric.equals(n%2==0?"remaining":"total"),"An item did not receive both measurements before advancing");}
        check(starter.playerInfo.equals("POPUP")&&starter.inspectionRenderDistance&&starter.active().popup.seconds==5,"Inspection defaults");
        check(Snapping.axis(477,40,960,true,true,true)==480,"Centre snapping");
        check(Snapping.axis(26,40,960,true,true,true)==24,"Left edge snapping");
        check(Snapping.axis(26,40,960,true,false,true)==26,"Disabled left edge attracts drag");
        check(Snapping.axis(477,40,960,false,true,true)==477,"Disabled axis attracts drag");
        check(Snapping.axis(937,40,960,true,true,true)==936,"Right edge snapping");
        check(Double.isFinite(Targeting.score(Math.cos(Math.toRadians(6)),200,256,8,false)),"Distant near-aim target rejected");
        check(!Double.isFinite(Targeting.score(Math.cos(Math.toRadians(9)),200,256,8,false)),"Outside cone accepted");
        check(!Double.isFinite(Targeting.score(1,257,256,8,true)),"Outside range accepted");
        check(!Double.isFinite(Targeting.score(-1,50,256,8,false)),"Behind camera accepted");
        check(Targeting.score(1,200,256,8,true)<Targeting.score(.999,10,256,8,false),"Near miss steals direct target");
        for(Config.Display display:Config.Display.values()){var features=Config.defaults();var f=features.active();f.groups.getFirst().appearance.display=display;f.groups.getFirst().appearance.radius=8;f.groups.getFirst().appearance.backgroundColor=0x123456;f.groups.getFirst().appearance.backgroundOpacity=.4;f.popup.x=40;f.popup.seconds=12;f.popup.right=false;f.snap.vertical=false;features.validate();var saved=ShareCode.decode(ShareCode.encode(f,"PROFILE","1.21.11",List.of())).profile();check(saved.groups.getFirst().appearance.display==display&&saved.popup.seconds==12&&saved.popup.x==40&&!saved.popup.right&&!saved.snap.vertical&&saved.groups.getFirst().appearance.backgroundColor==0x123456,"HUD preferences lost on export "+display);}
        var broken=Config.defaults();broken.active().popup.seconds=0;try{broken.validate();throw new AssertionError("Zero popup duration accepted");}catch(IllegalArgumentException expected){checks++;}
        broken=Config.defaults();broken.inspectionAngle=Double.NaN;try{broken.validate();throw new AssertionError("NaN targeting angle accepted");}catch(IllegalArgumentException expected){checks++;}
        var capability=new Capabilities(false);
        for(String family:List.of("pearls","wind","experience","totems","food","potions"))check(capability.forFamily(family).remote()==Capabilities.Support.SUPPORTED,"Remote family disabled: "+family);
        var old=Config.defaults();old.defaultsRevision=0;var op=old.active();String removed=op.definitions.removeLast().id;String removedCounter=op.counters.removeLast().id;op.nametagOrder.remove(removed);op.groups.getFirst().members.remove(removedCounter);op.groups.getFirst().mode=Config.Mode.CYCLE;op.groups.getFirst().appearance.twoValues=false;op.nametagCap=4;
        var migrationFolder=Files.createTempDirectory("tallium-default-migration-");var migrationStore=new ConfigStore(migrationFolder);migrationStore.save(old);
        var migrated=migrationStore.load();check(migrated.active().id.equals(op.id)&&migrated.active().definitions.size()==5&&migrated.active().groups.getFirst().mode==Config.Mode.CYCLE,"Original defaults were not upgraded in place");
        old.active().name="My custom profile";migrationStore.save(old);check(migrationStore.load().active().definitions.size()==4,"Custom profile was changed by migration");
        var shipped=Config.defaults();shipped.defaultsRevision=2;var shippedGroup=shipped.active().groups.getFirst();shippedGroup.mode=Config.Mode.ROW;shippedGroup.appearance.twoValues=true;shippedGroup.appearance.display=Config.Display.COUNTER;shippedGroup.appearance.overlayNumber=false;shipped.playerInfo="CHAT";migrationStore.save(shipped);var upgraded=migrationStore.load();check(upgraded.defaultsRevision==3&&upgraded.active().groups.getFirst().mode==Config.Mode.CYCLE&&upgraded.playerInfo.equals("POPUP"),"0.1.1 defaults failed to upgrade");
        shippedGroup.placement.x=37;migrationStore.save(shipped);var customized=migrationStore.load();check(customized.active().groups.getFirst().mode==Config.Mode.ROW&&customized.active().groups.getFirst().placement.x==37,"Custom starter placement was overwritten");
        Config config=Config.defaults();config.validate();config.active().groups.getFirst().appearance.display=Config.Display.COUNTER;config.active().counters.getFirst().scope=Config.Scope.SELECTED;config.active().counters.getFirst().players.add(player.toString());
        var profile=config.active();profile.groups.getFirst().mode=Config.Mode.CYCLE;profile.playerList=true;profile.separator=true;profile.hideZero=false;profile.coloredXp=true;profile.alwaysColoredXp=true;profile.xpSource=profile.groups.getFirst().id;
        var appearance=profile.groups.getFirst().appearance;appearance.numberFormat="COMPACT";appearance.twoValues=false;appearance.opacity=.6;appearance.textScale=1.5;appearance.labels=true;appearance.iconMode="DEFAULT";
        profile.groups.getFirst().placement.x=-18;profile.groups.getFirst().placement.scale=1.75;profile.groups.getFirst().placement.locked=true;
        String code=ShareCode.encode(config.active(),"PROFILE","1.21.11",List.of());ShareCode.Export decoded=ShareCode.decode(code);
        check(decoded.profile().counters.getFirst().players.isEmpty()&&decoded.profile().counters.getFirst().scope==Config.Scope.UNRESOLVED,"Player identity escaped");
        check(code.equals(ShareCode.encode(decoded.profile(),"PROFILE","1.21.11",List.of())),"Share code nondeterministic or lost settings");
        var sanitized=ConfigStore.copy(config.active(),Config.Profile.class);sanitized.counters.getFirst().scope=Config.Scope.UNRESOLVED;sanitized.counters.getFirst().players.clear();
        check(ConfigStore.JSON.toJson(sanitized).equals(ConfigStore.JSON.toJson(decoded.profile())),"Exported profile settings changed");
        var duplicate=ShareCode.remap(decoded.profile());check(!duplicate.id.equals(decoded.profile().id),"Duplicate profile ID reused");Config.validateProfile(duplicate,new HashSet<>(Set.of(duplicate.id)));
        check(duplicate.groups.getFirst().members.getFirst().equals(duplicate.counters.getFirst().id),"ID reference remap failed");
        var layout=ShareCode.decode(ShareCode.encode(config.active(),"LAYOUT","1.21.11",List.of(config.active().groups.getFirst().id)));
        check(layout.profile().counters.size()==5&&layout.profile().definitions.size()==5,"Layout dependencies missing");
        invalid(code.substring(0,code.length()-1)+(code.endsWith("0")?"1":"0"));invalid("TLM2:"+code.substring(5));invalid("TLM1:"+"A".repeat(ShareCode.MAX_TEXT));
        invalid(pack(ConfigStore.JSON.toJson(new ShareCode.Export(2,"PROFILE","1.21.11",decoded.profile()))));
        invalid(pack("{schema:1,kind:'PROFILE'}"));var invalidProfile=ConfigStore.copy(decoded.profile(),Config.Profile.class);invalidProfile.id="";invalid(pack(ConfigStore.JSON.toJson(new ShareCode.Export(1,"PROFILE","1.21.11",invalidProfile))));
        invalidProfile=ConfigStore.copy(decoded.profile(),Config.Profile.class);invalidProfile.xpSource="missing";invalid(pack(ConfigStore.JSON.toJson(new ShareCode.Export(1,"PROFILE","1.21.11",invalidProfile))));
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(var out=new DeflaterOutputStream(bytes)){out.write(new byte[ShareCode.MAX_JSON+1]);}byte[] data=bytes.toByteArray();CRC32 crc=new CRC32();crc.update(data);invalid("TLM1:"+Base64.getUrlEncoder().withoutPadding().encodeToString(data)+"."+String.format("%08x",crc.getValue()));
        var folder=Files.createTempDirectory("tallium-verification-");var storage=new ConfigStore(folder);Config defaults=Config.defaults();storage.save(defaults);storage.save(defaults);Files.writeString(folder.resolve("tallium.json"),"invalid");
        check(storage.load().active()!=null&&!storage.recovery.isEmpty(),"Malformed config recovery failed");try{storage.save(defaults);throw new AssertionError("Corrupt file overwritten");}catch(IOException expected){checks++;}
        check(Files.readString(folder.resolve("tallium.json")).equals("invalid"),"Recovery destroyed original");
        var newerFolder=Files.createTempDirectory("tallium-newer-schema-");var newer=new ConfigStore(newerFolder);defaults.schema=2;String newerJson=ConfigStore.JSON.toJson(defaults);Files.writeString(newerFolder.resolve("tallium.json"),newerJson);check(newer.load().schema==1&&!newer.recovery.isEmpty(),"Newer schema not rejected safely");
        check(Files.readString(newerFolder.resolve("tallium.json")).equals(newerJson),"Newer configuration destroyed");
        var bounded=new CounterStore();for(int n=0;n<CounterStore.MAX_IDENTITIES;n++)bounded.accept(event(bounded,player,pearl,"id/"+n,Observation.Validation.CONFIRMED,0));check(!bounded.accept(event(bounded,player,pearl,"overflow",Observation.Validation.CONFIRMED,0))&&bounded.saturated(),"Identity overflow not fail-closed");
        check(bounded.total(player,List.of(selector))==CounterStore.MAX_IDENTITIES,"Overflow modified accepted totals");
        var styleConfig=Config.defaults();var style=styleConfig.active();style.nametagSpacing=3;style.playerListCap=2;style.playerListColors=false;style.cycleOverflow=true;style.hudIconMode="DEFAULT";
        var styleRoundTrip=ShareCode.decode(ShareCode.encode(style,"PROFILE","1.21.11",List.of())).profile();
        check(styleRoundTrip.nametagSpacing==3&&styleRoundTrip.playerListCap==2&&!styleRoundTrip.playerListColors&&styleRoundTrip.cycleOverflow&&styleRoundTrip.hudIconMode.equals("DEFAULT"),"New appearance fields were not exported");
        styleConfig.active().groups.getFirst().appearance.twoValues=true;check(ShareCode.decode(ShareCode.encode(style,"PROFILE","1.21.11",List.of())).profile().groups.getFirst().appearance.twoValues,"Local paired values did not round trip");styleConfig.active().counters.getFirst().scope=Config.Scope.EVERYONE;
        try{styleConfig.validate();throw new AssertionError("Remote paired inventory was accepted");}catch(IllegalArgumentException expected){checks++;}
        styleConfig.active().counters.getFirst().scope=Config.Scope.YOURSELF;styleConfig.active().groups.getFirst().mode=Config.Mode.TOTAL;
        try{styleConfig.validate();throw new AssertionError("Total paired inventory was accepted");}catch(IllegalArgumentException expected){checks++;}
        var optionalFolder=Files.createTempDirectory("tallium-optional-style-");var optionalStore=new ConfigStore(optionalFolder);var optional=Config.defaults();optional.active().groups.getFirst().appearance.iconMode="bad";optional.active().groups.getFirst().appearance.opacity=7;String rawOptional=ConfigStore.JSON.toJson(optional);Files.writeString(optionalFolder.resolve("tallium.json"),rawOptional);
        var repaired=optionalStore.load();check(repaired.active().groups.getFirst().appearance.iconMode.equals("INHERIT")&&repaired.active().groups.getFirst().appearance.opacity==1,"Optional local appearance fallback failed");check(Files.readString(optionalFolder.resolve("tallium.json")).equals(rawOptional),"Optional fallback changed the original on read");
        var invalidStyle=Config.defaults().active();invalidStyle.nametagSpacing=9;invalid(pack(ConfigStore.JSON.toJson(new ShareCode.Export(1,"PROFILE","1.21.11",invalidStyle))));
        var unsortedColors=ColorRules.used("pearls");Collections.reverse(unsortedColors.rows);check(unsortedColors.color(16)==0xff00aa00&&unsortedColors.color(0)==0xff55ff55,"Reordered threshold preview changed boundary meanings");
        var boundaries=new CounterStore();
        Observation delayed=event(boundaries,player,pearl,"delayed",Observation.Validation.CONFIRMED,0);
        boundaries.newSession();check(!boundaries.accept(delayed),"Previous session event accepted after reconnect");
        check(boundaries.accept(event(boundaries,player,pearl,"delayed",Observation.Validation.CONFIRMED,0)),"New session reused key rejected");
        check(boundaries.total(player,List.of(selector))==1,"Cached total setup");
        boundaries.resetAll();check(boundaries.total(player,List.of(selector))==0,"Cached totals survived resetAll");
        check(!boundaries.accept(event(boundaries,player,pearl,"old-epoch",Observation.Validation.CONFIRMED,0)),"Old epoch accepted after resetAll");
        for(int n=0;n<1000;n++){
            long epoch=boundaries.epoch();Observation pending=event(boundaries,player,pearl,"pending/"+n,Observation.Validation.CONFIRMED,epoch);
            boundaries.reset(other::equals,List.of(selector));check(!boundaries.accept(pending),"Overlapping reset retained pending event "+n);
            check(boundaries.accept(event(boundaries,player,pearl,"confirmed/"+n,Observation.Validation.CONFIRMED,boundaries.epoch())),"Fresh confirmation rejected "+n);
        }
        check(boundaries.total(player,List.of(selector))==1000,"Repeated resets altered unmatched player's total");
        boundaries.newSession();check(boundaries.snapshot().isEmpty()&&!boundaries.saturated()&&boundaries.diagnostics().isEmpty(),"Reconnect failed to clear state");
        consumptionAndLabels();
        System.out.println("PASS: "+checks+" dependency-free core verification assertions. Game hooks and multiplayer behavior are not covered.");
    }
    static void consumptionAndLabels(){
        var id=UUID.randomUUID();var session=UUID.randomUUID();var apple=new ItemIdentity("minecraft:golden_apple","",List.of(),"",Set.of("food"));
        for(int duration:new int[]{16,32,40})for(int end:new int[]{5,duration-3,duration,duration+4}){
            var tracker=new ConsumptionTracker();var store=new CounterStore();session=store.session();
            tracker.observe(id,"MAIN_HAND",null,0,false,0,session,0,"world");
            for(int tick=1;tick<=end;tick++)check(tracker.observe(id,"MAIN_HAND",apple,duration,true,tick,session,0,"world")==null,"Timer crossing manufactured a consumption");
            var event=tracker.observe(id,"MAIN_HAND",apple,0,false,end+1,session,0,"world");
            check((event!=null)==(end>=duration-1),"Completion/cancellation duration");
            if(event!=null){
                check(event.validation()==Observation.Validation.ESTIMATED&&store.accept(event),"Estimate not accepted");
                check(store.estimated(id,List.of(Selector.item(apple.item())))&&Value.estimated(1).text().equals("1"),"Internal evidence retained; displayed count is plain");
                var confirmed=tracker.confirm(id,end+2,session,0,"world");check(confirmed!=null&&store.accept(confirmed),"Late confirmation failed");
                check(!store.estimated(id,List.of(Selector.item(apple.item())))&&store.total(id,List.of(Selector.item(apple.item())))==1,"Confirmation counted twice");
                check(!store.accept(confirmed),"Duplicate confirmation counted");store.resetAll();check(!store.accept(confirmed),"Reset revived a late confirmation");
            }
            check(tracker.observe(id,"MAIN_HAND",null,0,false,end+3,session,0,"world")==null,"Idle tick repeated a completion");
        }
        var tracker=new ConsumptionTracker();session=UUID.randomUUID();
        for(int t=0;t<40;t++)tracker.observe(id,"MAIN_HAND",apple,32,true,t,session,0,"world");
        check(tracker.observe(id,"MAIN_HAND",apple,32,false,40,session,0,"world")==null,"Mid-cycle arrival invented completion");check(tracker.incomplete(id),"Incomplete observation was presented as exact");
        tracker.clear();tracker.observe(id,"MAIN_HAND",null,0,false,0,session,0,"world");
        for(int t=1;t<=15;t++)tracker.observe(id,"MAIN_HAND",apple,32,true,t,session,0,"world");
        for(int t=16;t<50;t++)tracker.observe(id,"OFF_HAND",apple,32,true,t,session,0,"world");
        check(tracker.observe(id,"OFF_HAND",apple,32,false,50,session,0,"world")==null,"Hand switch counted");
        tracker.clear();tracker.observe(id,"MAIN_HAND",null,0,false,0,session,0,"world");
        for(int t=1;t<35;t++)tracker.observe(id,"MAIN_HAND",apple,32,true,t,session,t>=16?1:0,"world");
        check(tracker.observe(id,"MAIN_HAND",null,0,false,35,session,1,"world")==null,"Reset during use counted");
        tracker.remove(id);check(tracker.confirm(id,36,session,1,"world")==null,"Removed entity retained completion");
        tracker.clear();tracker.observe(id,"MAIN_HAND",null,0,false,0,session,1,"world");tracker.observe(id,"MAIN_HAND",apple,32,true,1,session,1,"world");
        for(int t=8;t<40;t++)tracker.observe(id,"MAIN_HAND",apple,32,true,t,session,1,"world");
        check(tracker.observe(id,"MAIN_HAND",null,0,false,40,session,1,"world")==null,"Observation gap counted");
        tracker.clear();tracker.observe(id,"MAIN_HAND",null,0,false,0,session,1,"world");
        for(int t=1;t<16;t++)tracker.observe(id,"MAIN_HAND",apple,32,true,t,session,1,"world");
        var honey=new ItemIdentity("minecraft:honey_bottle","",List.of(),"",Set.of("food"));
        for(int t=16;t<60;t++)tracker.observe(id,"MAIN_HAND",honey,40,true,t,session,1,"world");
        check(tracker.observe(id,"MAIN_HAND",null,0,false,60,session,1,"world")==null&&tracker.incomplete(id),"Active item switch inferred completion");
        tracker.clear();tracker.observe(id,"MAIN_HAND",null,0,false,0,session,1,"world");
        for(int t=1;t<=32;t++)tracker.observe(id,"MAIN_HAND",apple,32,true,t,session,1,"world");
        var first=tracker.observe(id,"MAIN_HAND",null,0,false,33,session,1,"world");
        for(int t=34;t<=65;t++)tracker.observe(id,"MAIN_HAND",apple,32,true,t,session,1,"world");
        var second=tracker.observe(id,"MAIN_HAND",null,0,false,66,session,1,"world");
        check(first!=null&&second!=null&&!first.deduplicationKey().equals(second.deduplicationKey()),"Repeated eating shared attempt identity");
        tracker.clear();check(tracker.confirm(id,67,session,1,"world")==null,"World clearing retained an attempt");
        var labels=new LabelAssociations();var a=UUID.randomUUID();var b=UUID.randomUUID();var label=UUID.randomUUID();
        for(int t=0;t<10;t++){
            var pos=new LabelAssociations.Point(t*.1,0,0);labels.update(List.of(new LabelAssociations.Player(a,List.of("Alice"),pos),new LabelAssociations.Player(b,List.of("Bob"),new LabelAssociations.Point(1,0,0))),List.of(new LabelAssociations.Label(label,"§6[Rank]\nAlice",new LabelAssociations.Point(t*.1,2.3,0),null)),t);
        }
        check(a.equals(labels.owner(label)),"Formatted multiline moving label not associated");
        labels.update(List.of(new LabelAssociations.Player(a,List.of("Nick"),new LabelAssociations.Point(0,0,0)),new LabelAssociations.Player(b,List.of("Nick"),new LabelAssociations.Point(.2,0,0))),List.of(new LabelAssociations.Label(label,"Nick",new LabelAssociations.Point(0,2,0),null)),11);
        check(labels.owner(label)==null,"Ambiguous nickname used nearest player");
        labels.update(List.of(new LabelAssociations.Player(a,List.of("Alice"),new LabelAssociations.Point(0,0,0))),List.of(new LabelAssociations.Label(label,"Unknown nickname",new LabelAssociations.Point(0,2,0),null)),12);check(labels.owner(label)==null,"Unsupported label associated by position alone");
        labels.update(List.of(new LabelAssociations.Player(a,List.of("Alice"),new LabelAssociations.Point(0,0,0))),List.of(new LabelAssociations.Label(label,"Unknown nickname",new LabelAssociations.Point(0,2,0),a)),13);check(a.equals(labels.owner(label)),"Explicit passenger relationship ignored");
        labels.update(List.of(),List.of(),14);check(labels.owner(label)==null,"Stale label retained");
        var cfg=Config.defaults();cfg.active().nametagScale=1.5;cfg.active().nametagFallback=false;
        try{var round=ShareCode.decode(ShareCode.encode(cfg.active(),"PROFILE","1.21.11",List.of())).profile();check(round.nametagScale==1.5&&!round.nametagFallback,"Nametag options lost on export");}catch(Exception e){throw new AssertionError(e);}
    }

}

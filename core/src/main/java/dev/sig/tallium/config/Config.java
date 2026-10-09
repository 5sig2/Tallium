package dev.sig.tallium.config;

import dev.sig.tallium.items.*;
import dev.sig.tallium.display.*;
import java.util.*;

public final class Config {
    public static final int SCHEMA=1;
    public int schema=SCHEMA;
    public int defaultsRevision;
    public boolean tracking=true, resetOwnDeath=true, resetOtherDeath=true, resetDimension=false;
    public boolean holdResetAll=false, confirmResetAll=false;
    public String playerInfo="POPUP", activeProfile;
    public int inspectionRange=32, summaryRows=12;
    public boolean inspectionRenderDistance=true, inspectionMissSound=true;
    public double inspectionAngle=8;
    public String inventoryScope="MAIN_OFFHAND";
    public List<Profile> profiles=new ArrayList<>();
    public List<ResetAction> resetActions=new ArrayList<>();
    public enum Metric { REMAINING, USED }
    public enum Scope { YOURSELF, OTHERS, EVERYONE, SELECTED, UNRESOLVED }
    public enum Mode { ROW, GRID, CYCLE, TOTAL }
    public enum Surface { HUD, NAMETAG, PLAYER_LIST }
    public enum Anchor { SCREEN, HOTBAR, XP }
    public enum Display { COUNTER, REMAINING, TOTAL, BOTH, CYCLE }
    public static final class Snap {
        public boolean enabled=true,horizontal=true,vertical=true,left=true,right=true,top=true,bottom=true;
    }
    public static final class Popup {
        public boolean enabled=true,right=true,locked=false,background=true;
        public int x=10,y=34,seconds=5,radius=5,color=0x101517;
        public double scale=1,opacity=.85;
    }
    public static String id(){return UUID.randomUUID().toString();}
    public static final class Definition {
        public String id=Config.id(), label="", family="other";
        public Selector selector;
        public boolean enabled=true, nametag=true, unavailable=false;
    }
    public static final class Counter {
        public String id=Config.id(), definition;
        public Scope scope=Scope.YOURSELF;
        public List<String> players=new ArrayList<>();
        public Metric metric=Metric.REMAINING;
        public ColorRules remaining, used;
    }
    public static final class Group {
        public String id=Config.id(), name="Group";
        public List<String> members=new ArrayList<>();
        public Mode mode=Mode.ROW;
        public Surface surface=Surface.HUD;
        public int interval=5, columns=2;
        public boolean skipZero=true, hideEmpty=true, enabled=true;
        public Placement placement=new Placement();
        public Appearance appearance=new Appearance();
        public ColorRules override;
        public int itemInterval(){return interval*(appearance.display==Display.CYCLE&&!appearance.twoValues?2:1);}
    }
    public static final class Placement {
        public Anchor anchor=Anchor.XP;
        public int x=0,y=0,z=0;
        public double scale=1;
        public boolean locked=false;
    }
    public static final class Appearance {
        public String iconMode="INHERIT";
        public String numberFormat="PLAIN";
        public Display display=Display.COUNTER;
        public boolean overlayNumber=false;
        public int radius=0,backgroundColor=0;
        public double backgroundOpacity=.53;
        public int iconSize=16,spacing=2,padding=2;
        public double opacity=1;
        public double textScale=1;
        public boolean background=false,shadow=true,labels=false,hideZero=true,iconTint=false,twoValues=false;
    }
    public static final class Profile {
        public String id=Config.id(),name="Default";
        public List<Definition> definitions=new ArrayList<>();
        public List<Counter> counters=new ArrayList<>();
        public List<Group> groups=new ArrayList<>();
        public Snap snap=new Snap();
        public Popup popup=new Popup();
        public boolean hud=true,nametags=true,playerList=false,separator=false,hideZero=true;
        public int nametagCap=4;
        public int nametagSpacing=1,playerListCap=4,playerListSpacing=1,overflowInterval=5;
        public boolean cycleOverflow=false,playerListHideZero=true,playerListSeparator=false;
        public boolean nametagColors=true,playerListColors=true;
        public boolean nametagFallback=true;
        public int nametagDistance=64,nametagX=0,nametagY=0;
        public double nametagScale=1;
        public String playerListIconMode="DEFAULT";
        public String hudIconMode="RESOURCE_PACK";
        public String nametagIconMode="DEFAULT", xpSource=null;
        public boolean coloredXp=false,alwaysColoredXp=false;
        public List<String> nametagOrder=new ArrayList<>();
        public Definition definition(String id){return definitions.stream().filter(d->d.id.equals(id)).findFirst().orElse(null);}
        public Counter counter(String id){return counters.stream().filter(c->c.id.equals(id)).findFirst().orElse(null);}
        public Group group(String id){return groups.stream().filter(g->g.id.equals(id)).findFirst().orElse(null);}
    }
    public static final class ResetAction {
        public String id=Config.id(),name="Reset Usage",profileId,groupId;
        public List<String> definitions=new ArrayList<>(),players=new ArrayList<>();
        public Scope scope=Scope.EVERYONE;
        public boolean all=false,hold=false,confirm=false;
    }
    public Profile active(){return profiles.stream().filter(p->p.id.equals(activeProfile)).findFirst().orElse(profiles.getFirst());}
    public static Config defaults(){
        Config cfg=new Config();Profile p=new Profile();cfg.profiles.add(p);cfg.activeProfile=p.id;
        cfg.defaultsRevision=3;p.nametagCap=5;
        Group g=new Group();g.name="Personal";g.mode=Mode.CYCLE;g.appearance.hideZero=false;g.appearance.display=Display.REMAINING;g.appearance.overlayNumber=true;p.groups.add(g);
        for(String item:List.of("ender_pearl","totem_of_undying","wind_charge","golden_apple","experience_bottle")){
            Definition d=starter(item);p.definitions.add(d);p.nametagOrder.add(d.id);
            Counter c=new Counter();c.definition=d.id;c.used=ColorRules.used(d.family);c.remaining=ColorRules.remaining(d.family);
            p.counters.add(c);g.members.add(c.id);
        }
        Collections.swap(p.nametagOrder,0,1);return cfg;
    }
    public static Definition starter(String item){
        Definition d=new Definition();d.selector=Selector.item("minecraft:"+item);
        d.label=switch(item){case "totem_of_undying"->"Totem of Undying";case "experience_bottle"->"Bottle o' Enchanting";case "cooked_beef"->"Steak";default->Arrays.stream(item.split("_")).map(word->word.substring(0,1).toUpperCase(Locale.ROOT)+word.substring(1)).collect(java.util.stream.Collectors.joining(" "));};d.family=switch(item){
            case "ender_pearl"->"pearls";case "wind_charge"->"wind";case "totem_of_undying"->"totems";
            case "experience_bottle"->"experience";
            case "milk_bucket","honey_bottle","golden_apple","enchanted_golden_apple","golden_carrot","cooked_beef"->"food";
            case "potion","splash_potion","lingering_potion"->"potions";
            case "arrow","spectral_arrow","tipped_arrow"->"arrows";case "cobweb"->"cobwebs";default->"other";};return d;
    }
    public void validate(){
        if(schema!=SCHEMA)throw new IllegalArgumentException("Unsupported configuration schema "+schema);
        if(profiles==null||profiles.isEmpty()||profiles.size()>128)throw new IllegalArgumentException("Use 1–128 profiles.");
        if(resetActions==null||resetActions.size()>64)throw new IllegalArgumentException("At most 64 reset actions.");
        if(!Set.of("CHAT","PLAYERS","POPUP").contains(playerInfo)||!Set.of("MAIN_OFFHAND","HOTBAR").contains(inventoryScope)||inspectionRange<1||inspectionRange>1024||summaryRows<4||summaryRows>30||!Double.isFinite(inspectionAngle)||inspectionAngle<1||inspectionAngle>30)
            throw new IllegalArgumentException("Invalid Player Info settings.");
        Set<String> ids=new HashSet<>(),names=new HashSet<>();
        for(Profile p:profiles){
            unique(ids,p.id);if(p.name==null||p.name.isBlank()||p.name.length()>80||!names.add(p.name))throw new IllegalArgumentException("Profile names must be unique and 1–80 characters.");
            validateProfile(p,ids);
        }
        if(profiles.stream().noneMatch(p->p.id.equals(activeProfile)))throw new IllegalArgumentException("Active profile is missing.");
        for(ResetAction a:resetActions){unique(ids,a.id);if(a.scope==null||a.players==null||a.players.size()>1024||a.definitions==null||a.definitions.size()>512||a.name==null||a.name.isBlank()||a.name.length()>80)throw new IllegalArgumentException("Invalid reset action.");}
    }
    public static void validateProfile(Profile p,Set<String> ids){
        if(p.nametagDistance<1||p.nametagDistance>1024||!Double.isFinite(p.nametagScale)||p.nametagScale<.25||p.nametagScale>4||Math.abs((long)p.nametagX)>512||Math.abs((long)p.nametagY)>512)throw new IllegalArgumentException("Invalid nametag placement.");
        if(p.snap==null||p.popup==null)throw new IllegalArgumentException("Missing layout preferences.");
        Popup popup=p.popup;
        if(popup.seconds<1||popup.seconds>60||popup.radius<0||popup.radius>24||popup.color<0||popup.color>0xffffff||Math.abs((long)popup.x)>32768||Math.abs((long)popup.y)>32768||!Double.isFinite(popup.scale)||popup.scale<.5||popup.scale>2||!Double.isFinite(popup.opacity)||popup.opacity<.05||popup.opacity>1)throw new IllegalArgumentException("Invalid player popup settings.");
        if(p.definitions==null||p.counters==null||p.groups==null||p.definitions.size()>512||p.counters.size()>512||p.groups.size()>128)
            throw new IllegalArgumentException("Profile limit: 512 definitions/counters and 128 groups.");
        for(Definition d:p.definitions){unique(ids,d.id);if(d.selector==null||d.label==null||d.label.length()>160||d.family==null||d.family.length()>80)throw new IllegalArgumentException("Invalid item definition.");
            Selector s=d.selector;if(s.item()==null&&s.category()==null)throw new IllegalArgumentException("Item definitions need an item or category selector.");
            for(String identifier:Arrays.asList(s.item(),s.potion(),s.effect()))if(identifier!=null&&(identifier.length()>256||!identifier.matches("[a-z0-9_.-]+:[a-z0-9_/.-]+")))throw new IllegalArgumentException("Use a namespaced registry ID.");
            if(s.category()!=null&&s.category().length()>80||s.exactComponents()!=null&&s.exactComponents().length()>16384||s.amplifier()!=null&&s.amplifier()<0||s.duration()!=null&&s.duration()<0)throw new IllegalArgumentException("Invalid variant filter.");}
        for(Counter c:p.counters){unique(ids,c.id);if(p.definition(c.definition)==null||c.scope==null||c.metric==null||c.players==null||c.players.size()>1024)throw new IllegalArgumentException("Counter refers to a missing definition.");
            if(c.used!=null)c.used.validate();if(c.remaining!=null)c.remaining.validate();}
        for(Group g:p.groups){unique(ids,g.id);if(g.mode==null||g.surface==null||g.name==null||g.name.length()>80||g.members==null||g.members.size()>128||new HashSet<>(g.members).size()!=g.members.size())throw new IllegalArgumentException("Invalid group.");
            Metric metric=null;Scope scope=null;List<String> players=null;
            for(String member:g.members){Counter c=p.counter(member);if(c==null)throw new IllegalArgumentException("Group member is missing.");
                if(g.mode==Mode.TOTAL && metric!=null && (metric!=c.metric||scope!=c.scope||!players.equals(c.players)))throw new IllegalArgumentException("Totals need one measurement and player scope.");metric=c.metric;scope=c.scope;players=c.players;}
            if(g.interval<1||g.interval>60||g.columns<1||g.columns>16||g.placement==null||g.appearance==null)throw new IllegalArgumentException("Invalid cycle/layout settings.");
            Placement l=g.placement;Appearance a=g.appearance;
            if(a.display==null||a.radius<0||a.radius>24||a.backgroundColor<0||a.backgroundColor>0xffffff||!Double.isFinite(a.backgroundOpacity)||a.backgroundOpacity<.05||a.backgroundOpacity>1)throw new IllegalArgumentException("Invalid HUD appearance.");
            if(a.display!=Display.COUNTER&&(g.mode==Mode.TOTAL||g.members.stream().map(p::counter).anyMatch(c->c.scope!=Scope.YOURSELF)))throw new IllegalArgumentException("Display choices require individual counters for yourself.");
            if(a.twoValues&&(g.mode==Mode.TOTAL||g.members.stream().map(p::counter).anyMatch(c->c.scope!=Scope.YOURSELF)))throw new IllegalArgumentException("Two Values requires your own counters and cannot be a total.");
            if(l.anchor==null||!Double.isFinite(l.scale)||l.scale<.25||l.scale>4||Math.abs((long)l.x)>32768||Math.abs((long)l.y)>32768||l.z<0||l.z>128||
               !Double.isFinite(a.opacity)||a.opacity<.05||a.opacity>1||!Double.isFinite(a.textScale)||a.textScale<.5||a.textScale>2||a.iconSize<8||a.iconSize>32||a.spacing<0||a.spacing>16||a.padding<0||a.padding>16||!Set.of("INHERIT","DEFAULT","RESOURCE_PACK").contains(a.iconMode)||!Set.of("PLAIN","COMPACT").contains(a.numberFormat))throw new IllegalArgumentException("Layout is outside its allowed bounds.");
            if(g.override!=null)g.override.validate();
        }
        if(p.nametagOrder==null||p.nametagOrder.size()>512||new HashSet<>(p.nametagOrder).size()!=p.nametagOrder.size()||p.nametagOrder.stream().anyMatch(id->p.definition(id)==null)||p.nametagCap<1||p.nametagCap>30||!Set.of("DEFAULT","RESOURCE_PACK").contains(p.nametagIconMode))throw new IllegalArgumentException("Invalid nametag settings.");
        if(p.nametagSpacing<0||p.nametagSpacing>8||p.playerListSpacing<0||p.playerListSpacing>8||p.playerListCap<1||p.playerListCap>30||p.overflowInterval<1||p.overflowInterval>60||!Set.of("DEFAULT","RESOURCE_PACK").contains(p.playerListIconMode)||!Set.of("DEFAULT","RESOURCE_PACK").contains(p.hudIconMode))throw new IllegalArgumentException("Invalid player-name spacing or overflow settings.");
        if(p.xpSource!=null&&p.counter(p.xpSource)==null&&(p.group(p.xpSource)==null||p.group(p.xpSource).mode!=Mode.CYCLE))throw new IllegalArgumentException("XP source must reference a counter or cycle group.");
    }
    private static void unique(Set<String> ids,String id){if(id==null||id.isBlank()||id.length()>80||!ids.add(id))throw new IllegalArgumentException("IDs must be unique and 1–80 characters.");}
}

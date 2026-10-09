package dev.sig.tallium.config;

import com.google.gson.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.io.IOException;

public final class ConfigStore {
    public static final Gson JSON=new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    public static final int MAX_BYTES=8*1024*1024;
    private final Path file;
    private boolean protectedFile;
    public String recovery="";
    public ConfigStore(Path directory){file=directory.resolve("tallium.json");}
    public Config load(){
        if(!Files.exists(file))return Config.defaults();
        try {return read(file);} catch(Exception e){
            protectedFile=true;recovery="Tallium preserved unreadable/newer configuration: "+e.getMessage();
            try{return read(backup());}catch(Exception ignored){return Config.defaults();}
        }
    }
    private Config read(Path path)throws IOException{
        if(Files.size(path)>MAX_BYTES)throw new IOException("Configuration exceeds 8 MiB.");
        try {Config c=JSON.fromJson(Files.readString(path),Config.class);if(c==null)throw new IllegalArgumentException("Empty configuration.");
            if(c.schema==Config.SCHEMA){repairOptional(c);upgradeDefaults(c);}c.validate();return c;}
        catch(StackOverflowError e){throw new IOException("Configuration nesting exceeds limits.");}
    }
    public void save(Config config)throws IOException{
        config.validate();
        if(protectedFile)throw new IOException("Original configuration is preserved. Use explicit recovery before saving.");
        Files.createDirectories(file.getParent());byte[] bytes=JSON.toJson(config).getBytes(StandardCharsets.UTF_8);
        if(bytes.length>MAX_BYTES)throw new IOException("Configuration exceeds 8 MiB.");
        Path temp=file.resolveSibling("tallium.json.tmp");Files.write(temp,bytes);
        if(Files.exists(file))Files.copy(file,backup(),StandardCopyOption.REPLACE_EXISTING);
        try{Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
        catch(AtomicMoveNotSupportedException e){Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING);}
    }
    public void recover(Config config)throws IOException{
        if(Files.exists(file))Files.copy(file,file.resolveSibling("tallium.recovery-"+System.currentTimeMillis()+".json"));
        protectedFile=false;save(config);recovery="";
    }
    private Path backup(){return file.resolveSibling("tallium.json.bak");}

    static void upgradeDefaults(Config c){
        if(c.defaultsRevision>=3)return;
        if(c.defaultsRevision==2){
            if(c.profiles!=null&&c.profiles.size()==1){var p=c.profiles.getFirst();
                if(p.name.equals("Default")&&p.groups.size()==1&&p.counters.size()==5&&p.definitions.size()==5){var g=p.groups.getFirst();
                    var items=java.util.Set.of("minecraft:ender_pearl","minecraft:totem_of_undying","minecraft:wind_charge","minecraft:golden_apple","minecraft:experience_bottle");
                    if(g.name.equals("Personal")&&g.mode==Config.Mode.ROW&&g.members.size()==5&&g.appearance.twoValues&&g.placement.anchor==Config.Anchor.XP&&g.placement.x==0&&g.placement.y==0&&g.placement.scale==1&&g.appearance.iconSize==16&&p.definitions.stream().allMatch(d->items.contains(d.selector.item()))){
                        g.mode=Config.Mode.CYCLE;g.appearance.twoValues=false;g.appearance.display=Config.Display.REMAINING;g.appearance.overlayNumber=true;
                        if(c.playerInfo.equals("CHAT"))c.playerInfo="POPUP";
                    }
                }
            }
            c.defaultsRevision=3;return;
        }
        if(c.profiles!=null&&c.profiles.size()==1){Config.Profile p=c.profiles.getFirst();
            var original=java.util.Set.of("minecraft:ender_pearl","minecraft:totem_of_undying","minecraft:golden_apple","minecraft:wind_charge");
            if(p!=null&&p.name.equals("Default")&&p.definitions!=null&&p.definitions.size()==4&&p.counters!=null&&p.counters.size()==4&&p.groups!=null&&p.groups.size()==1&&
                p.definitions.stream().allMatch(d->d!=null&&d.selector!=null&&original.contains(d.selector.item()))&&
                p.definitions.stream().map(d->d.selector.item()).distinct().count()==4&&
                p.counters.stream().allMatch(x->x.metric==Config.Metric.REMAINING&&x.scope==Config.Scope.YOURSELF)){
                var g=p.groups.getFirst();
                if(g!=null&&g.name.equals("Personal")&&g.mode==Config.Mode.CYCLE&&g.surface==Config.Surface.HUD&&g.members.size()==4){
                    var d=Config.starter("experience_bottle");p.definitions.add(d);p.nametagOrder.add(d.id);
                    var x=new Config.Counter();x.definition=d.id;x.remaining=dev.sig.tallium.display.ColorRules.remaining(d.family);x.used=dev.sig.tallium.display.ColorRules.used(d.family);
                    p.counters.add(x);g.members.add(x.id);g.mode=Config.Mode.ROW;g.placement.y=0;g.appearance.hideZero=false;g.appearance.twoValues=true;
                    if(p.nametagCap==4)p.nametagCap=5;
                }
            }
        }
        c.defaultsRevision=2;
        upgradeDefaults(c);
    }

    private static void repairOptional(Config c){if(c.profiles==null)return;
        for(Config.Profile p:c.profiles){if(p==null)continue;
            if(p.snap==null)p.snap=new Config.Snap();if(p.popup==null)p.popup=new Config.Popup();
            if(p.hudIconMode==null||!java.util.Set.of("DEFAULT","RESOURCE_PACK").contains(p.hudIconMode))p.hudIconMode="RESOURCE_PACK";
            if(p.nametagIconMode==null||!java.util.Set.of("DEFAULT","RESOURCE_PACK").contains(p.nametagIconMode))p.nametagIconMode="DEFAULT";
            if(p.playerListIconMode==null||!java.util.Set.of("DEFAULT","RESOURCE_PACK").contains(p.playerListIconMode))p.playerListIconMode="DEFAULT";
            if(p.nametagCap<1||p.nametagCap>30)p.nametagCap=4;if(p.playerListCap<1||p.playerListCap>30)p.playerListCap=4;
            if(p.nametagSpacing<0||p.nametagSpacing>8)p.nametagSpacing=1;if(p.playerListSpacing<0||p.playerListSpacing>8)p.playerListSpacing=1;
            if(p.overflowInterval<1||p.overflowInterval>60)p.overflowInterval=5;
            if(p.groups==null)continue;for(Config.Group g:p.groups){if(g==null)continue;
                if(g.appearance==null)g.appearance=new Config.Appearance();var a=g.appearance;
                if(a.display==null)a.display=Config.Display.COUNTER;
                if(a.iconMode==null||!java.util.Set.of("DEFAULT","RESOURCE_PACK","INHERIT").contains(a.iconMode))a.iconMode="INHERIT";
                if(a.numberFormat==null||!java.util.Set.of("PLAIN","COMPACT").contains(a.numberFormat))a.numberFormat="PLAIN";
                if(!Double.isFinite(a.opacity)||a.opacity<.05||a.opacity>1)a.opacity=1;
                if(!Double.isFinite(a.textScale)||a.textScale<.5||a.textScale>2)a.textScale=1;
                if(a.iconSize<8||a.iconSize>32)a.iconSize=16;if(a.spacing<0||a.spacing>16)a.spacing=2;if(a.padding<0||a.padding>16)a.padding=2;
            }
        }
    }
    public static <T>T copy(T object,Class<T> type){return JSON.fromJson(JSON.toJson(object),type);}
}

package dev.sig.tallium.adapter;
import dev.sig.tallium.config.*;
import java.util.*;
public final class GroupScreen extends FormScreen {
    private final TalliumScreen editor;private final String id;
    public GroupScreen(TalliumScreen editor,String id){super(editor,"Tallium - "+editor.profile().group(id).name+" / Group");this.editor=editor;this.id=id;}
    protected void build(){Config.Group g=editor.profile().group(id);
        field("Name",g.name,v->g.name=v);
        action("Mode: "+g.mode,()->{nextMode(g);refresh();});
        action("Surface: "+g.surface,()->{g.surface=Config.Surface.values()[(g.surface.ordinal()+1)%3];refresh();});
        if(g.mode==Config.Mode.CYCLE){field("Interval (1–60)",Integer.toString(g.interval),v->{int n=Integer.parseInt(v);if(n<1||n>60)throw new IllegalArgumentException("Interval: 1–60 seconds.");g.interval=n;});action("Skip Zero: "+g.skipZero,()->{g.skipZero=!g.skipZero;refresh();});action("All Empty: "+(g.hideEmpty?"Hide":"Show Zero"),()->{g.hideEmpty=!g.hideEmpty;refresh();});}
        if(g.mode==Config.Mode.GRID)field("Columns",Integer.toString(g.columns),v->{int n=Integer.parseInt(v);if(n<1||n>16)throw new IllegalArgumentException("Columns: 1–16.");g.columns=n;});
        action("Icons: "+g.appearance.iconMode,()->{g.appearance.iconMode=switch(g.appearance.iconMode){case "INHERIT"->"DEFAULT";case "DEFAULT"->"RESOURCE_PACK";default->"INHERIT";};refresh();});
        action("Labels: "+g.appearance.labels,()->{g.appearance.labels=!g.appearance.labels;refresh();});
        action("Background: "+g.appearance.background,()->{g.appearance.background=!g.appearance.background;refresh();});
        field("Corner Radius",Integer.toString(g.appearance.radius),v->{int n=Integer.parseInt(v);if(n<0||n>24)throw new IllegalArgumentException("Radius: 0–24. Zero gives square corners.");g.appearance.radius=n;});
        field("Background Color (#RRGGBB)",String.format("#%06X",g.appearance.backgroundColor),v->{if(!v.matches("#?[0-9a-fA-F]{6}"))throw new IllegalArgumentException("Use six hex digits.");g.appearance.backgroundColor=Integer.parseInt(v.replace("#",""),16);});
        field("Background Opacity",Double.toString(g.appearance.backgroundOpacity),v->{double n=Double.parseDouble(v);if(!Double.isFinite(n)||n<.05||n>1)throw new IllegalArgumentException("Opacity: 0.05–1.");g.appearance.backgroundOpacity=n;});
        action("Hide Zero: "+g.appearance.hideZero,()->{g.appearance.hideZero=!g.appearance.hideZero;refresh();});
        field("Opacity",Double.toString(g.appearance.opacity),v->{double n=Double.parseDouble(v);if(!Double.isFinite(n)||n<.05||n>1)throw new IllegalArgumentException("Opacity: 0.05–1.");g.appearance.opacity=n;});
        field("Icon Size",Integer.toString(g.appearance.iconSize),v->{int n=Integer.parseInt(v);if(n<8||n>32)throw new IllegalArgumentException("Icon size: 8–32.");g.appearance.iconSize=n;});
        field("Text Scale",Double.toString(g.appearance.textScale),v->{double n=Double.parseDouble(v);if(!Double.isFinite(n)||n<.5||n>2)throw new IllegalArgumentException("Text scale: 0.5–2.");g.appearance.textScale=n;});
        field("Spacing",Integer.toString(g.appearance.spacing),v->{int n=Integer.parseInt(v);if(n<0||n>16)throw new IllegalArgumentException("Spacing: 0–16.");g.appearance.spacing=n;});
        field("Padding",Integer.toString(g.appearance.padding),v->{int n=Integer.parseInt(v);if(n<0||n>16)throw new IllegalArgumentException("Padding: 0–16.");g.appearance.padding=n;});
        action("Text Shadow: "+g.appearance.shadow,()->{g.appearance.shadow=!g.appearance.shadow;refresh();});
        action("Number Format: "+g.appearance.numberFormat,()->{g.appearance.numberFormat=g.appearance.numberFormat.equals("PLAIN")?"COMPACT":"PLAIN";refresh();});
        action("Display: "+(g.appearance.twoValues?"Remaining | Total":displayName(g.appearance.display)),()->{nextDisplay(editor.profile(),g);refresh();});
        action("Number Position: "+(g.appearance.overlayNumber?"On icon":"After icon"),()->{g.appearance.overlayNumber=!g.appearance.overlayNumber;refresh();});
        if(g.appearance.display==Config.Display.CYCLE&&g.mode!=Config.Mode.CYCLE)field("Interval (1–60)",Integer.toString(g.interval),v->{int n=Integer.parseInt(v);if(n<1||n>60)throw new IllegalArgumentException("Interval: 1–60 seconds.");g.interval=n;});
        action("Colors: "+(g.override==null?"Inherited":"Override"),()->{Config.Counter sample=new Config.Counter();sample.used=g.override==null?dev.sig.tallium.display.ColorRules.used("pearls"):g.override;minecraft.setScreen(new ColorsScreen(this,sample,true,"pearls",r->g.override=r));});
        action("Inherit Counter Colors",()->{g.override=null;refresh();});
        for(Config.Counter c:editor.profile().counters){String label=editor.profile().definition(c.definition).label;action("Member: "+label+": "+g.members.contains(c.id),()->{if(g.members.contains(c.id))g.members.remove(c.id);else if(g.members.size()<128)g.members.add(c.id);refresh();});}
        for(int n=1;n<g.members.size();n++){final int i=n;action("Move Up: "+editor.profile().definition(editor.profile().counter(g.members.get(n)).definition).label,()->{Collections.swap(g.members,i,i-1);refresh();});}
        action("Reset Group Usage",()->{var selectors=g.members.stream().map(editor.profile()::counter).map(c->editor.profile().definition(c.definition).selector).toList();TalliumClient.resetGroup(g.id,selectors);});
        action("Duplicate Group",()->{if(editor.profile().groups.size()>=128)throw new IllegalArgumentException("At most 128 groups.");Config.Group copy=ConfigStore.copy(g,Config.Group.class);copy.id=Config.id();copy.name+=" copy";editor.profile().groups.add(copy);onClose();});
        action("Delete Group",()->minecraft.setScreen(new ConfirmDialog(ok->{if(ok){editor.profile().groups.remove(g);if(g.id.equals(editor.profile().xpSource))editor.profile().xpSource=null;onClose();}else minecraft.setScreen(this);},net.minecraft.network.chat.Component.literal("Delete display group?"),net.minecraft.network.chat.Component.literal("Its XP source is cleared; reset actions targeting this group are disabled. Usage remains intact."))));
    }
    public static String displayName(Config.Display display){return switch(display){case COUNTER->"Counter setting";case REMAINING->"Remaining";case TOTAL->"Total used";case BOTH->"Remaining | Total";case CYCLE->"Alternate Remaining / Total";};}
    public static void nextMode(Config.Group g){g.mode=Config.Mode.values()[(g.mode.ordinal()+1)%Config.Mode.values().length];if(g.mode==Config.Mode.TOTAL){g.appearance.display=Config.Display.COUNTER;g.appearance.twoValues=false;}}
    public static void nextDisplay(Config.Profile p,Config.Group g){
        if(g.mode==Config.Mode.TOTAL||g.members.stream().map(p::counter).filter(Objects::nonNull).anyMatch(c->c.scope!=Config.Scope.YOURSELF)){
            if(g.appearance.display!=Config.Display.COUNTER||g.appearance.twoValues){g.appearance.display=Config.Display.COUNTER;g.appearance.twoValues=false;return;}
            throw new IllegalArgumentException("Choose Counter setting for totals or other players.");
        }
        g.appearance.display=Config.Display.values()[(g.appearance.display.ordinal()+1)%Config.Display.values().length];g.appearance.twoValues=false;
    }
}

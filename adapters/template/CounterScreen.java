package dev.sig.tallium.adapter;
import dev.sig.tallium.config.*;
import dev.sig.tallium.display.*;
import dev.sig.tallium.items.*;
import java.util.*;
public final class CounterScreen extends FormScreen {
    final TalliumScreen editor;private final String id;private boolean advanced;
    public CounterScreen(TalliumScreen editor,String id){super(editor,"Tallium - "+editor.profile().definition(editor.profile().counter(id).definition).label+" / Counter");this.editor=editor;this.id=id;}
    protected void build(){
        Config.Counter c=editor.profile().counter(id);Config.Definition d=editor.profile().definition(c.definition);
        field("Label",d.label,v->d.label=v);
        action("Tracking: "+d.enabled,()->{d.enabled=!d.enabled;refresh();});
        action("Measurement: "+(c.metric==Config.Metric.USED&&d.family.equals("arrows")?"Shots":c.metric),()->{if(c.metric==Config.Metric.REMAINING&&TalliumClient.CAPABILITIES.forFamily(d.family).local()==dev.sig.tallium.tracking.Capabilities.Support.UNSUPPORTED){error=TalliumClient.CAPABILITIES.forFamily(d.family).gap();return;}c.metric=c.metric==Config.Metric.REMAINING?Config.Metric.USED:Config.Metric.REMAINING;refresh();});
        action("Nametag: "+d.nametag,()->{d.nametag=!d.nametag;refresh();});
        action("Player Scope: "+c.scope,()->{c.scope=switch(c.scope){case YOURSELF->Config.Scope.EVERYONE;case EVERYONE->Config.Scope.OTHERS;case OTHERS->Config.Scope.SELECTED;default->Config.Scope.YOURSELF;};refresh();});
        if(c.scope==Config.Scope.SELECTED)for(var player:TalliumClient.roster.values())action((c.players.contains(player.uuid().toString())?"Remove ":"Select ")+player.name(),()->{String selected=player.uuid().toString();if(c.players.contains(selected))c.players.remove(selected);else c.players.add(selected);refresh();});
        action("Clear Exact Variant Filter",()->{Selector s=d.selector;d.selector=new Selector(s.item(),s.category(),s.potion(),s.effect(),s.amplifier(),s.duration(),null);refresh();});
        action("Edit Colors: Remaining",()->minecraft.setScreen(new ColorsScreen(this,c,false,d.family)));
        action("Edit Colors: Used",()->minecraft.setScreen(new ColorsScreen(this,c,true,d.family)));
        ColorRules current=c.metric==Config.Metric.REMAINING?c.remaining:c.used;
        action("Colors: "+(current==null||current.enabled),()->{ColorRules rules=c.metric==Config.Metric.REMAINING?c.remaining:c.used;if(rules==null){rules=c.metric==Config.Metric.REMAINING?ColorRules.remaining(d.family):ColorRules.used(d.family);if(c.metric==Config.Metric.REMAINING)c.remaining=rules;else c.used=rules;}rules.enabled=!rules.enabled;refresh();});
        action("Preset: "+d.family,()->minecraft.setScreen(new ConfirmDialog(ok->{if(ok){if(c.metric==Config.Metric.REMAINING)c.remaining=ColorRules.remaining(d.family);else c.used=ColorRules.used(d.family);}minecraft.setScreen(this);},net.minecraft.network.chat.Component.literal("Restore the family color preset?"),net.minecraft.network.chat.Component.literal("Only this measurement's color rules change. Usage is preserved."))));
        action("Advanced Filters: "+(advanced?"Expanded":"Collapsed"),()->{advanced=!advanced;refresh();});
        if(advanced){field("Potion identity",d.selector.potion()==null?"":d.selector.potion(),v->{Selector s=d.selector;d.selector=new Selector(s.item(),s.category(),v.isBlank()?null:v,s.effect(),s.amplifier(),s.duration(),s.exactComponents());});
        field("Effect family",d.selector.effect()==null?"":d.selector.effect(),v->{Selector s=d.selector;d.selector=new Selector(s.item(),s.category(),s.potion(),v.isBlank()?null:v,s.amplifier(),s.duration(),s.exactComponents());});
        field("Amplifier (0 = I)",d.selector.amplifier()==null?"":d.selector.amplifier().toString(),v->{Selector s=d.selector;Integer a=v.isBlank()?null:Integer.valueOf(v);if(a!=null&&a<0)throw new IllegalArgumentException("Amplifier cannot be negative.");d.selector=new Selector(s.item(),s.category(),s.potion(),s.effect(),a,s.duration(),s.exactComponents());});
        field("Duration (ticks)",d.selector.duration()==null?"":d.selector.duration().toString(),v->{Selector s=d.selector;Integer a=v.isBlank()?null:Integer.valueOf(v);if(a!=null&&a<0)throw new IllegalArgumentException("Duration cannot be negative.");d.selector=new Selector(s.item(),s.category(),s.potion(),s.effect(),s.amplifier(),a,s.exactComponents());});}
        action("Reset This Item's Usage",()->{TalliumClient.STORE.reset(u->true,List.of(d.selector));error="Usage reset across all profiles.";});
        action("Duplicate Counter",()->{if(editor.profile().counters.size()>=512)throw new IllegalArgumentException("At most 512 counters.");Config.Counter duplicate=ConfigStore.copy(c,Config.Counter.class);duplicate.id=Config.id();editor.profile().counters.add(duplicate);editor.selectCounter(duplicate.id);onClose();});
        action("Reset Counter Settings",()->minecraft.setScreen(new ConfirmDialog(ok->{if(ok){c.metric=Config.Metric.REMAINING;c.scope=Config.Scope.YOURSELF;c.players.clear();c.used=ColorRules.used(d.family);c.remaining=ColorRules.remaining(d.family);d.nametag=true;}minecraft.setScreen(this);},net.minecraft.network.chat.Component.literal("Reset counter settings?"),net.minecraft.network.chat.Component.literal("The item selector and usage tallies are preserved."))));
        action("Remove Counter",()->{String references=editor.profile().groups.stream().filter(g->g.members.contains(id)).map(g->g.name).collect(java.util.stream.Collectors.joining(", "));
            minecraft.setScreen(new ConfirmDialog(ok->{if(ok){editor.profile().counters.remove(c);editor.profile().groups.forEach(g->g.members.remove(id));if(editor.profile().counters.stream().noneMatch(x->x.definition.equals(d.id))){editor.profile().definitions.remove(d);editor.profile().nametagOrder.remove(d.id);}if(id.equals(editor.profile().xpSource))editor.profile().xpSource=null;editor.refresh();minecraft.setScreen(editor);}else minecraft.setScreen(this);},net.minecraft.network.chat.Component.literal("Remove counter and its display references?"),net.minecraft.network.chat.Component.literal("Groups: "+(references.isEmpty()?"none":references)+". Reset actions with deleted targets will be disabled.")));});
    }
}

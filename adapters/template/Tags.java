package dev.sig.tallium.adapter;
import dev.sig.tallium.config.Config;
import dev.sig.tallium.display.*;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.*;
import net.minecraft.world.item.ItemStack;
import java.util.*;
public final class Tags {
    public record Icon(ItemStack stack,int x,String mode) {}
    public record Row(Component text,List<Icon> icons,float x) {}
    public record Layout(Component text,List<Icon> icons,int nameWidth,int nameStart,List<Row> rows) {
        public Layout(Component text,List<Icon> icons,int nameWidth,int nameStart){this(text,icons,nameWidth,nameStart,List.of());}
        public float centerShift(){return (Minecraft.getInstance().font.width(text)-nameWidth)/2f-nameStart;}
    }
    public record RenderInfo(List<Icon> icons,int nameWidth,int nameStart) {}
    private record Segment(Config.Definition definition,long count,ColorRules rules,String iconMode,int spacing,boolean compact,boolean estimated) {}
    private static final Map<String,Cycle> cycles=new HashMap<>();
    private static final Map<Component,RenderInfo> renderIcons=new WeakHashMap<>();
    public static void remember(Layout layout){renderIcons.put(layout.text(),new RenderInfo(layout.icons(),layout.nameWidth(),layout.nameStart()));}
    public static RenderInfo renderInfo(Component text){return renderIcons.get(text);}
    public static void clear(){cycles.clear();renderIcons.clear();}
    public static void resetGroup(String id){cycles.keySet().removeIf(key->key.startsWith(id+"/"));}
    public static Layout build(UUID uuid,Component original,boolean tab){
        Config.Profile p=TalliumClient.config.active();if(tab?!p.playerList:!p.nametags)return new Layout(original,List.of(),Minecraft.getInstance().font.width(original),0);
        Minecraft mc=Minecraft.getInstance();
        List<Segment> segments=new ArrayList<>();Set<String> grouped=new HashSet<>();
        Config.Surface surface=tab?Config.Surface.PLAYER_LIST:Config.Surface.NAMETAG;
        for(Config.Group group:p.groups){if(!group.enabled||group.surface!=surface)continue;
            List<Config.Counter> members=group.members.stream().map(p::counter).filter(Objects::nonNull).filter(c->{Config.Definition d=p.definition(c.definition);return d!=null&&d.enabled&&d.nametag&&!d.unavailable&&c.metric==Config.Metric.USED&&TalliumClient.counterIncludes(c,uuid);}).toList();
            members.forEach(c->grouped.add(c.definition));
            if(group.mode==Config.Mode.CYCLE){String key=group.id+"/"+uuid;if(cycles.size()<8192||cycles.containsKey(key)){
                String selected=cycles.computeIfAbsent(key,k->new Cycle()).select(members.stream().map(c->c.id).toList(),id->{Value v=TalliumClient.cachedUsage(uuid,p.definition(p.counter(id).definition));return v.hasCount()&&(!group.skipZero||v.count()!=0);},group.interval,System.nanoTime(),mc.isPaused());
                if(selected==null&&!group.hideEmpty&&!members.isEmpty())members=List.of(members.getFirst());else members=selected==null?List.of():List.of(p.counter(selected));}}
            if(group.mode==Config.Mode.TOTAL&&!members.isEmpty()){
                boolean supported=members.stream().allMatch(c->TalliumClient.cachedUsage(uuid,p.definition(c.definition)).hasCount());if(supported){long total=TalliumClient.STORE.total(uuid,members.stream().map(c->p.definition(c.definition).selector).toList());
                    if(total!=0||!group.appearance.hideZero)segments.add(new Segment(p.definition(members.getFirst().definition),total,group.override==null?ColorRules.used("other"):group.override,TalliumClient.iconMode(p,group),group.appearance.spacing,group.appearance.numberFormat.equals("COMPACT"),TalliumClient.STORE.estimated(uuid,members.stream().map(c->p.definition(c.definition).selector).toList())));}continue;}
            for(Config.Counter c:members){Config.Definition d=p.definition(c.definition);Value v=TalliumClient.cachedUsage(uuid,d);if(v.hasCount()&&(v.count()!=0||!group.appearance.hideZero))segments.add(new Segment(d,v.count(),group.override!=null?group.override:c.used!=null?c.used:ColorRules.used(d.family),TalliumClient.iconMode(p,group),group.appearance.spacing,group.appearance.numberFormat.equals("COMPACT"),v.state()==Value.State.ESTIMATED));}
        }
        for(String id:p.nametagOrder){Config.Definition d=p.definition(id);if(d==null||!d.enabled||!d.nametag||d.unavailable)continue;
            if(grouped.contains(id))continue;Value value=TalliumClient.cachedUsage(uuid,d);if(!value.hasCount()||(value.count()==0&&(tab?p.playerListHideZero:p.hideZero)))continue;
            Config.Counter counter=p.counters.stream().filter(c->c.definition.equals(id)).findFirst().orElse(null);
            ColorRules rules=counter!=null&&counter.used!=null?counter.used:ColorRules.used(d.family);
            segments.add(new Segment(d,value.count(),rules,tab?p.playerListIconMode:p.nametagIconMode,tab?p.playerListSpacing:p.nametagSpacing,false,value.state()==Value.State.ESTIMATED));
        }
        return compose(p,original,tab,segments,uuid.toString());
    }
    private static Layout compose(Config.Profile p,Component original,boolean tab,List<Segment> all,String cycleKey){
        Minecraft mc=Minecraft.getInstance();MutableComponent text=Component.empty();List<Icon> icons=new ArrayList<>();int cap=tab?p.playerListCap:p.nametagCap;
        List<Segment> segments=all;int omitted=Math.max(0,all.size()-cap);
        if(p.cycleOverflow&&omitted>0&&!cycleKey.equals("preview")){String key="overflow/"+tab+"/"+cycleKey;int pages=(all.size()+cap-1)/cap;
            if(cycles.size()<8192||cycles.containsKey(key)){String page=cycles.computeIfAbsent(key,k->new Cycle()).select(java.util.stream.IntStream.range(0,pages).mapToObj(Integer::toString).toList(),id->true,p.overflowInterval,System.nanoTime(),mc.isPaused());int start=page==null?0:Integer.parseInt(page)*cap;segments=all.subList(start,Math.min(start+cap,all.size()));}}
        segments=segments.subList(0,Math.min(cap,segments.size()));String separator=(tab?p.playerListSeparator:p.separator)?" | ":" ";
        if(!tab){int outer=segments.size()-1;if((outer&1)==0)outer--;for(int i=outer;i>=1;i-=2){appendSegment(text,icons,segments.get(i),p.nametagColors);text.append(separator);}}
        Component leftText=text.copy();List<Icon> leftIcons=List.copyOf(icons);
        int nameStart=mc.font.width(text),nameWidth=mc.font.width(original);text.append(original.copy());MutableComponent rightText=Component.empty();List<Icon> rightIcons=new ArrayList<>();
        for(int i=0;i<segments.size();i+=tab?1:2){text.append(separator);appendSegment(text,icons,segments.get(i),tab?p.playerListColors:p.nametagColors);rightText.append(separator);appendSegment(rightText,rightIcons,segments.get(i),tab?p.playerListColors:p.nametagColors);}
        if(omitted>0&&!p.cycleOverflow){text.append(" +"+omitted);rightText.append(" +"+omitted);}
        List<Row> rows=new ArrayList<>();if(!leftIcons.isEmpty())rows.add(new Row(leftText,leftIcons,-nameWidth/2f-nameStart));if(!rightIcons.isEmpty())rows.add(new Row(rightText,List.copyOf(rightIcons),nameWidth/2f));
        return new Layout(text,List.copyOf(icons),nameWidth,nameStart,List.copyOf(rows));
    }
    private static void appendSegment(MutableComponent text,List<Icon> icons,Segment segment,boolean colors){
        Minecraft mc=Minecraft.getInstance();String number=segment.compact()&&segment.count()>=1000?String.format(Locale.ROOT,"%.1fk",segment.count()/1000.0):Long.toString(segment.count());
        text.append(Component.literal("-"+number).withStyle(style->style.withColor(colors?segment.rules().color(segment.count())&0xffffff:0xffffff)));
        int space=Math.max(1,mc.font.width(" "));text.append(" ".repeat((segment.spacing()+space-1)/space));int x=mc.font.width(text);text.append(" ".repeat((8+space-1)/space));icons.add(new Icon(TalliumClient.representative(segment.definition()),x,segment.iconMode()));
    }

    public static Layout preview(Config.Profile p,Component original,boolean tab){if(tab?!p.playerList:!p.nametags)return new Layout(original.copy().append(" (Off)"),List.of(),Minecraft.getInstance().font.width(original),0);List<Segment> samples=new ArrayList<>();int n=0;
        for(String id:p.nametagOrder){var d=p.definition(id);if(d==null||!d.enabled||!d.nametag||d.unavailable)continue;var counter=p.counters.stream().filter(c->c.definition.equals(id)).findFirst().orElse(null);
            var rules=counter==null||counter.used==null?ColorRules.used(d.family):counter.used;samples.add(new Segment(d,2+n++,rules,tab?p.playerListIconMode:p.nametagIconMode,tab?p.playerListSpacing:p.nametagSpacing,false,false));}
        return compose(p,original,tab,samples,"preview");
    }
    public static void drawPreview(net.minecraft.client.gui.GuiGraphics g,Layout layout,int x,int y){g.drawString(Minecraft.getInstance().font,layout.text(),x,y,0xffffffff);
        for(Icon icon:layout.icons()){@TAB_POSE_PUSH@ @TAB_POSE_TRANSLATE@ @TAB_POSE_SCALE@
            if(icon.mode().equals("RESOURCE_PACK"))g.renderItem(icon.stack(),0,0);else VanillaIcons.draw(g,icon.stack(),0,0);@TAB_POSE_POP@}
    }
}

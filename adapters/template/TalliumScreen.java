package dev.sig.tallium.adapter;

import dev.sig.tallium.config.*;
import dev.sig.tallium.display.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.function.BooleanSupplier;

public final class TalliumScreen extends ConfigScreen {
    private boolean rebuildPending;
    final Screen parent;
    Config working;
    String section="Counters",error="";
    int page;
    String counterQuery="";
    private String selected;
    private FlatEditBox search;
    private boolean searchDirty,appearance=true,advanced,showEnabled;
    private int tableWidth,inspectorX,inspectorWidth,visibleRows,measureX,trackX,nameX,groupX,contentBottom;
    private final List<Caption> captions=new ArrayList<>();
    private record Caption(String text,int x,int y,int width,int color) {}
    public TalliumScreen(Screen parent){super(Component.literal("Tallium"));this.parent=parent;working=ConfigStore.copy(TalliumClient.config,Config.class);if(TalliumClient.catalogue.isEmpty())TalliumClient.rebuildCatalogue();}
    Config.Profile profile(){return working.active();}
    void refresh(){rebuildPending=true;}
    private void safely(Runnable action){try{action.run();}catch(Exception e){error=Objects.toString(e.getMessage(),"Invalid settings.");}}
    FlatButton button(String label,int x,int y,int w,Runnable action){
        var button=FlatButton.builder(Component.literal(UiText.label(label)),b->safely(action)).bounds(x,y,w,18).build();
        button.setTooltip(Tooltip.create(Component.literal(UiText.help(label))));addRenderableWidget(button);return button;
    }
    private void check(String label,int x,int y,int w,BooleanSupplier checked,Runnable action){button(label,x,y,w,action).check(checked);}
    private void caption(String text,int x,int y,int width,int color){captions.add(new Caption(text,x,y,width,color));}
    @Override protected void init(){
        captions.clear();search=null;contentBottom=height-75;
        initNavigation();
        button("Discard",width-154,height-26,68,()->minecraft.setScreen(parent));
        button("Apply",width-80,height-26,70,()->{try{TalliumClient.apply(working);}catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}working=ConfigStore.copy(TalliumClient.config,Config.class);error="Saved.";refresh();}).primary();
        switch(section){case "Counters"->counters();case "Groups"->groups();case "Nametags"->nametags();case "Settings"->settings();default->{}}
    }
    void openTab(String tab){
        if(tab.equals("Players")){minecraft.setScreen(new PlayersScreen(this,null));return;}
        if(tab.equals("Layout")){minecraft.setScreen(new LayoutScreen(this));return;}
        if(tab.equals("Colors")){Config.Counter c=selectedCounter();if(c==null){error="Select a counter first.";return;}minecraft.setScreen(new ColorsScreen(this,c,c.metric==Config.Metric.USED,profile().definition(c.definition).family));return;}
        section=tab;page=0;refresh();
    }
    void selectCounter(String id){selected=id;section="Counters";counterQuery="";page=Math.max(0,profile().counters.indexOf(profile().counter(id)));}
    private Config.Counter selectedCounter(){Config.Counter c=profile().counter(selected);if(c==null&&!profile().counters.isEmpty()){c=profile().counters.getFirst();selected=c.id;}return c;}
    private List<Config.Counter> visibleCounters(){
        String q=counterQuery.toLowerCase(Locale.ROOT);
        return profile().counters.stream().filter(c->{Config.Definition d=profile().definition(c.definition);return d!=null&&(!showEnabled||d.enabled)&&(d.label.toLowerCase(Locale.ROOT).contains(q)||d.selector.item()!=null&&d.selector.item().contains(q)||d.family.contains(q));}).toList();
    }
    private void counters(){
        selectedCounter();inspectorWidth=width>=540&&height>=320?Math.min(235,width/3):0;
        tableWidth=width-20-(inspectorWidth==0?0:inspectorWidth+12);inspectorX=10+tableWidth+12;
        int searchWidth=Math.max(80,width-240);
        search=new FlatEditBox(font,10,57,searchWidth,18,Component.literal("Search counters"));
        search.setHint(Component.literal("Search counters..."));search.setValue(counterQuery);
        search.setResponder(v->{counterQuery=v;page=0;searchDirty=true;});addRenderableWidget(search);
        button(showEnabled?"Show: Enabled":"Show: All",18+searchWidth,57,74,()->{showEnabled=!showEnabled;page=0;refresh();});
        button("Add item",width-142,57,62,()->minecraft.setScreen(new ItemPickerScreen(this)));
        button("Reorder",width-74,57,64,()->minecraft.setScreen(new OrderScreen(this,true)));
        boolean group=tableWidth>=370;
        measureX=10+(int)(tableWidth*(group?.40:.47));trackX=10+(int)(tableWidth*(group?.57:.68));
        nameX=10+(int)(tableWidth*(group?.715:.84));groupX=group?10+(int)(tableWidth*.86):10+tableWidth;
        List<Config.Counter> list=visibleCounters();visibleRows=Math.max(1,(contentBottom-103)/19);page=Math.min(page,Math.max(0,list.size()-visibleRows));
        for(int n=page;n<Math.min(list.size(),page+visibleRows);n++){
            Config.Counter c=list.get(n);Config.Definition d=profile().definition(c.definition);int y=103+(n-page)*19;
            var row=button(d.label+" / "+UiText.value(c.metric),10,y,trackX-10,()->{selected=c.id;refresh();}).row(()->c.id.equals(selected));
            row.setTooltip(Tooltip.create(Component.literal("Select "+d.label+". "+UiText.help("Measurement"))));
            check("Track "+d.label,trackX,y,nameX-trackX,()->d.enabled,()->{d.enabled=!d.enabled;refresh();});
            check("Nametag "+d.label,nameX,y,groupX-nameX,()->d.nametag,()->{d.nametag=!d.nametag;refresh();});
        }
        caption(list.size()+" matching counters",10,contentBottom+5,tableWidth,UiStyle.MUTED);
        button("Duplicate",10,height-50,79,this::duplicate);
        button("Remove",95,height-50,66,this::remove);
        button("Edit details",167,height-50,87,()->{Config.Counter c=selectedCounter();if(c!=null)minecraft.setScreen(new CounterScreen(this,c.id));});
        if(inspectorWidth>0)inspector();
    }
    private void inspector(){
        Config.Counter c=selectedCounter();if(c==null){caption("Select or add a counter.",inspectorX+8,106,inspectorWidth-16,UiStyle.MUTED);return;}
        Config.Definition d=profile().definition(c.definition);int y=120,bottom=height-54;
        caption(d.label,inspectorX+29,104,inspectorWidth-37,UiStyle.TEXT);
        int controlX=inspectorX+inspectorWidth/2,controlWidth=inspectorWidth/2-8;
        caption("Label",inspectorX+8,y+5,controlX-inspectorX-12,UiStyle.TEXT);
        var label=new FlatEditBox(font,controlX,y,controlWidth,18,Component.literal("Label"));label.setValue(d.label);label.setResponder(v->d.label=v);addRenderableWidget(label);y+=21;
        inspectorChoice("Measurement",measurement(c,d),y,()->changeMetric(c,d));y+=21;
        inspectorChoice("Count for",UiText.value(c.scope),y,()->{c.scope=switch(c.scope){case YOURSELF->Config.Scope.EVERYONE;case EVERYONE->Config.Scope.OTHERS;case OTHERS->Config.Scope.SELECTED;default->Config.Scope.YOURSELF;};refresh();});y+=21;
        inspectorChoice("Inventory",working.inventoryScope.equals("HOTBAR")?"Hotbar":"Inv. + offhand",y,()->{working.inventoryScope=working.inventoryScope.equals("HOTBAR")?"MAIN_OFFHAND":"HOTBAR";refresh();});y+=24;
        if(y+18<bottom){int headingY=y;button((appearance?"v ":"> ")+"Appearance",inspectorX+6,headingY,inspectorWidth-12,()->{appearance=!appearance;refresh();});y+=22;}
        if(appearance&&y+60<bottom){
            caption("Show in nametags",inspectorX+8,y+5,inspectorWidth-40,UiStyle.TEXT);check("Show in nametags",inspectorX+inspectorWidth-30,y,24,()->d.nametag,()->{d.nametag=!d.nametag;refresh();});y+=20;
            ColorRules rules=c.metric==Config.Metric.REMAINING?c.remaining:c.used;
            caption("Number colors",inspectorX+8,y+5,inspectorWidth-40,UiStyle.TEXT);check("Number colors",inspectorX+inspectorWidth-30,y,24,()->rules==null||rules.enabled,()->{ColorRules r=rules;if(r==null){r=c.metric==Config.Metric.REMAINING?ColorRules.remaining(d.family):ColorRules.used(d.family);if(c.metric==Config.Metric.REMAINING)c.remaining=r;else c.used=r;}r.enabled=!r.enabled;refresh();});y+=20;
            inspectorChoice("Colors","Edit rules...",y,()->minecraft.setScreen(new ColorsScreen(this,c,c.metric==Config.Metric.USED,d.family)));y+=23;
        }
        if(y+18<bottom){button((advanced?"v ":"> ")+"Advanced filters",inspectorX+6,y,inspectorWidth-12,()->{advanced=!advanced;refresh();});y+=22;}
        if(advanced&&y+18<bottom){button("Edit variant and player filters",inspectorX+8,y,inspectorWidth-16,()->minecraft.setScreen(new CounterScreen(this,c.id)));y+=23;}
        var cap=TalliumClient.CAPABILITIES.forFamily(d.family);
        if(y+11<bottom)caption("Remaining: your inventory only",inspectorX+8,y,inspectorWidth-16,UiStyle.MUTED);
        if(y+24<bottom)caption("Usage: "+UiText.value(cap.local())+" / "+UiText.value(cap.remote()),inspectorX+8,y+13,inspectorWidth-16,UiStyle.MUTED);
    }
    private void inspectorChoice(String label,String value,int y,Runnable action){int start=inspectorX+inspectorWidth/2;caption(label,inspectorX+8,y+5,start-inspectorX-12,UiStyle.TEXT);var b=button(value,start,y,inspectorWidth/2-8,action);b.setTooltip(Tooltip.create(Component.literal(label.equals("Inventory")?UiText.help("Inventory Scope")+" Applies to all Remaining counters.":UiText.help(label))));}
    private String measurement(Config.Counter c,Config.Definition d){return c.metric==Config.Metric.USED&&d.family.equals("arrows")?"Shots":UiText.value(c.metric);}
    private void changeMetric(Config.Counter c,Config.Definition d){
        if(c.metric==Config.Metric.REMAINING&&TalliumClient.CAPABILITIES.forFamily(d.family).local()==dev.sig.tallium.tracking.Capabilities.Support.UNSUPPORTED){error=TalliumClient.CAPABILITIES.forFamily(d.family).gap();return;}
        c.metric=c.metric==Config.Metric.REMAINING?Config.Metric.USED:Config.Metric.REMAINING;refresh();
    }
    private void duplicate(){Config.Counter c=selectedCounter();if(c==null)return;if(profile().counters.size()>=512)throw new IllegalArgumentException("At most 512 counters.");Config.Counter copy=ConfigStore.copy(c,Config.Counter.class);copy.id=Config.id();profile().counters.add(copy);selected=copy.id;refresh();}
    private void remove(){
        Config.Counter c=selectedCounter();if(c==null)return;Config.Definition d=profile().definition(c.definition);
        String references=profile().groups.stream().filter(g->g.members.contains(c.id)).map(g->g.name).collect(java.util.stream.Collectors.joining(", "));
        minecraft.setScreen(new ConfirmDialog(ok->{if(ok){profile().counters.remove(c);profile().groups.forEach(g->g.members.remove(c.id));if(profile().counters.stream().noneMatch(x->x.definition.equals(d.id))){profile().definitions.remove(d);profile().nametagOrder.remove(d.id);}if(c.id.equals(profile().xpSource))profile().xpSource=null;selected=null;}minecraft.setScreen(this);},Component.literal("Remove "+d.label+"?"),Component.literal("Groups: "+(references.isEmpty()?"none":references)+". Display references are removed; usage counts remain.")));
    }
    private void groups(){
        button("Create group",10,57,110,()->{if(profile().groups.size()>=128)throw new IllegalArgumentException("At most 128 groups.");Config.Group g=new Config.Group();g.name="Group "+(profile().groups.size()+1);profile().groups.add(g);minecraft.setScreen(new GroupScreen(this,g.id));});
        visibleRows=Math.max(1,(contentBottom-94)/21);page=Math.min(page,Math.max(0,profile().groups.size()-visibleRows));
        caption("Group",14,84,width/3-20,UiStyle.TEXT);caption("Mode",width/3,84,width/4,UiStyle.TEXT);caption("Surface / Members",width*2/3,84,width/3-14,UiStyle.TEXT);
        for(int n=page;n<Math.min(profile().groups.size(),page+visibleRows);n++){Config.Group g=profile().groups.get(n);int y=99+(n-page)*21;button(g.name,10,y,width-20,()->minecraft.setScreen(new GroupScreen(this,g.id))).row(()->false);caption(g.name,14,y+5,width/3-20,UiStyle.TEXT);caption(UiText.value(g.mode),width/3,y+5,width/3-8,UiStyle.TEXT);caption(UiText.value(g.surface)+" / "+g.members.size(),width*2/3,y+5,width/3-14,UiStyle.TEXT);}
    }
    private void setting(String label,String value,int y,Runnable action){int control=Math.max(130,width*3/5);caption(label,14,y+5,control-24,UiStyle.TEXT);button(value,control,y,width-control-10,action);}
    private void toggle(String label,int y,BooleanSupplier value,Runnable action){caption(label,14,y+5,width-65,UiStyle.TEXT);check(label,width-44,y,34,value,action);}
    private void nametags(){
        List<String> labels=List.of("Nametags","Player list","Hide zero","Separator","Icons","Entry cap","Reorder entries","Name appearance");
        List<String> values=List.of(UiText.value(profile().nametags),UiText.value(profile().playerList),UiText.value(profile().hideZero),UiText.value(profile().separator),UiText.value(profile().nametagIconMode),Integer.toString(profile().nametagCap),"Edit...","Edit...");
        List<Runnable> actions=List.of(()->{profile().nametags=!profile().nametags;refresh();},()->{profile().playerList=!profile().playerList;refresh();},()->{profile().hideZero=!profile().hideZero;refresh();},()->{profile().separator=!profile().separator;refresh();},()->{profile().nametagIconMode=profile().nametagIconMode.equals("DEFAULT")?"RESOURCE_PACK":"DEFAULT";refresh();},()->{profile().nametagCap=profile().nametagCap%30+1;refresh();},()->minecraft.setScreen(new OrderScreen(this)),()->minecraft.setScreen(new NameSettingsScreen(this)));
        visibleRows=Math.max(1,(contentBottom-60)/21);page=Math.min(page,Math.max(0,labels.size()-visibleRows));
        for(int n=page;n<Math.min(labels.size(),page+visibleRows);n++){int i=n,y=60+(n-page)*21;String value=values.get(n);if(value.equals("On")||value.equals("Off"))toggle(labels.get(n),y,()->values.get(i).equals("On"),actions.get(n));else setting(labels.get(n),value,y,actions.get(n));}
    }
    private void settings(){
        List<Runnable> actions=List.of(()->{working.tracking=!working.tracking;refresh();},()->{working.playerInfo=switch(working.playerInfo){case "POPUP"->"CHAT";case "CHAT"->"PLAYERS";default->"POPUP";};refresh();},()->{working.resetOwnDeath=!working.resetOwnDeath;refresh();},()->{working.resetOtherDeath=!working.resetOtherDeath;refresh();},()->{working.resetDimension=!working.resetDimension;refresh();},()->minecraft.setScreen(new ResetActionsScreen(this)),()->minecraft.setScreen(new KeyBindsScreen(this,minecraft.options)),()->{profile().hud=!profile().hud;refresh();},()->minecraft.setScreen(new CapabilityScreen(this)),()->{try{TalliumClient.STORAGE.recover(working);}catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}error="Recovered; original archived.";},()->minecraft.setScreen(new GlobalSettingsScreen(this)));
        List<String> labels=List.of("Tracking","Player info","Reset own counts on death","Reset others on their death","Reset on dimension change","Reset actions","Controls","HUD","Detector capabilities","Recover configuration","Inventory, inspection & XP");
        List<String> values=List.of(UiText.value(working.tracking),UiText.value(working.playerInfo),UiText.value(working.resetOwnDeath),UiText.value(working.resetOtherDeath),UiText.value(working.resetDimension),"Edit...","Open...",UiText.value(profile().hud),"View...","Recover...","Edit...");
        visibleRows=Math.max(1,(contentBottom-60)/21);page=Math.min(page,Math.max(0,labels.size()-visibleRows));
        for(int n=page;n<Math.min(labels.size(),page+visibleRows);n++){int i=n,y=60+(n-page)*21;String value=values.get(n);if(value.equals("On")||value.equals("Off"))toggle(labels.get(n),y,()->values.get(i).equals("On"),actions.get(n));else setting(labels.get(n),value,y,actions.get(n));}
    }
    @Override public void render(GuiGraphics g,int mx,int my,float delta){
        if(searchDirty){searchDirty=false;rebuildPending=true;}
        if(rebuildPending){rebuildPending=false;UiStyle.rebuild(this,()->{clearWidgets();init();});}
        g.fill(0,0,width,height,UiStyle.BACKGROUND);

        if(section.equals("Counters")){
            UiStyle.frame(g,10,82,tableWidth,18,UiStyle.ALTERNATE,UiStyle.BORDER);
            UiStyle.text(g,font,"Item",14,87,measureX-20,UiStyle.TEXT);UiStyle.text(g,font,"Measure",measureX+3,87,trackX-measureX-6,UiStyle.TEXT);
            UiStyle.text(g,font,nameX-trackX>=53?"Tracking":"Track",trackX+3,87,nameX-trackX-6,UiStyle.TEXT);UiStyle.text(g,font,groupX-nameX>=53?"Nametag":"Name",nameX+3,87,groupX-nameX-6,UiStyle.TEXT);
            if(groupX<10+tableWidth)UiStyle.text(g,font,"Group",groupX+3,87,10+tableWidth-groupX-6,UiStyle.TEXT);
            var list=visibleCounters();
            for(int n=page;n<Math.min(list.size(),page+visibleRows);n++){Config.Counter c=list.get(n);int y=103+(n-page)*19;g.fill(10,y,10+tableWidth,y+18,c.id.equals(selected)?UiStyle.SELECTED:(n%2==0?UiStyle.ROW:UiStyle.ALTERNATE));}
            if(inspectorWidth>0){UiStyle.frame(g,inspectorX,96,inspectorWidth,height-150,UiStyle.PANEL,UiStyle.BORDER);Config.Counter c=selectedCounter();if(c!=null)g.renderItem(TalliumClient.representative(profile().definition(c.definition)),inspectorX+7,100);}
            UiStyle.scrollbar(g,10+tableWidth+3,103,visibleRows*19,list.size(),visibleRows,page);
        }else if(section.equals("Groups")){for(int n=0;n<Math.min(visibleRows,profile().groups.size()-page);n++)g.fill(10,99+n*21,width-10,119+n*21,n%2==0?UiStyle.ROW:UiStyle.ALTERNATE);}
        super.render(g,mx,my,delta);
        if(section.equals("Counters")){
            var list=visibleCounters();for(int n=page;n<Math.min(list.size(),page+visibleRows);n++){Config.Counter c=list.get(n);Config.Definition d=profile().definition(c.definition);int y=103+(n-page)*19;
                g.renderItem(TalliumClient.representative(d),14,y+1);UiStyle.text(g,font,d.label,34,y+5,measureX-38,UiStyle.TEXT);UiStyle.text(g,font,measurement(c,d),measureX+3,y+5,trackX-measureX-6,UiStyle.TEXT);
                if(groupX<10+tableWidth){String names=profile().groups.stream().filter(group->group.members.contains(c.id)).map(group->group.name).collect(java.util.stream.Collectors.joining(", "));UiStyle.text(g,font,names.isEmpty()?"-":names,groupX+3,y+5,10+tableWidth-groupX-6,UiStyle.TEXT);}
            }
            if(list.isEmpty())UiStyle.text(g,font,"No matching counters.",14,110,tableWidth-8,UiStyle.MUTED);
        }
        for(Caption c:captions)UiStyle.text(g,font,c.text,c.x,c.y,c.width,c.color);
        int previewTop=60+Math.min(visibleRows,Math.max(0,8-page))*21+12;
        if(section.equals("Nametags")&&previewTop+51<=contentBottom){g.enableScissor(10,previewTop,width-10,contentBottom);UiStyle.text(g,font,"Sample values",14,previewTop,width-28,UiStyle.MUTED);Tags.drawPreview(g,Tags.preview(profile(),Component.literal("Player"),false),14,previewTop+16);Tags.drawPreview(g,Tags.preview(profile(),Component.literal("Player"),true),14,previewTop+36);g.disableScissor();}
        String status=error.isEmpty()?TalliumClient.STORAGE.recovery:error;
        UiStyle.text(g,font,status.isEmpty()?"Changes are staged until Apply.":status,10,height-21,width-178,status.isEmpty()?UiStyle.MUTED:status.equals("Saved.")?UiStyle.GREEN:UiStyle.ERROR);
    }
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){if(super.mouseScrolled(x,y,horizontal,vertical))return true;if(vertical==0)return false;page=Math.max(0,page+(vertical<0?3:-3));refresh();return true;}
    @Override public void onClose(){minecraft.setScreen(parent);}
}

package dev.sig.tallium.adapter;

import dev.sig.tallium.config.*;
import dev.sig.tallium.display.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;


public final class ColorsScreen extends ConfigScreen {
    private boolean rebuildPending;
    private final Screen parent;private final Config.Counter counter;private final boolean used;private final String family;
    ColorRules working;private int sample=12,offset,count,tableWidth,previewWidth;
    private String error="";
    private final Set<String> invalid=new HashSet<>();
    private final Map<String,String> raw=new HashMap<>();
    static final Deque<String> recent=new ArrayDeque<>();
    private java.util.function.Consumer<ColorRules> applied;
    public ColorsScreen(Screen parent,Config.Counter counter,boolean used,String family){super(Component.literal("Tallium - Edit colors"));this.parent=parent;navigation(parent,"Colors");this.counter=counter;this.used=used;this.family=family;ColorRules r=used?counter.used:counter.remaining;working=ConfigStore.copy(r==null?(used?ColorRules.used(family):ColorRules.remaining(family)):r,ColorRules.class);}
    public ColorsScreen(Screen parent,Config.Counter counter,boolean used,String family,java.util.function.Consumer<ColorRules> applied){this(parent,counter,used,family);this.applied=applied;}
    private void refresh(){rebuildPending=true;}
    private boolean ready(){if(!invalid.isEmpty()){error="Correct the highlighted fields first.";return false;}return true;}
    private FlatButton button(String text,int x,int y,int w,Runnable action){var b=FlatButton.builder(Component.literal(text),pressed->{try{action.run();}catch(Exception e){error=Objects.toString(e.getMessage(),"Invalid color rules.");}}).bounds(x,y,w,18).build();addRenderableWidget(b);return b;}
    private void value(String key,String value,int x,int y,int w,java.util.function.Consumer<String> changed){
        var field=new FlatEditBox(font,x,y,w,18,Component.literal(key));field.setMaxLength(key.startsWith("Hex")?7:10);field.setValue(raw.getOrDefault(key,value));field.invalid(invalid.contains(key));
        field.setResponder(v->{try{changed.accept(v);invalid.remove(key);raw.remove(key);field.invalid(false);error="";}catch(Exception e){invalid.add(key);raw.put(key,v);field.invalid(true);error=Objects.toString(e.getMessage(),"Invalid color rules.");}});addRenderableWidget(field);
    }
    @Override protected void init(){
        initNavigation();
        if(applied==null){
            TalliumScreen editor=editor();Config.Definition definition=editor==null?null:editor.profile().definition(counter.definition);
            button("Item: "+(definition==null?family:definition.label),10,57,Math.min(220,width/2-14),()->{
                if(!stageRules()||editor==null)return;
                minecraft.setScreen(new FormScreen(this,"Tallium - Choose item colors"){
                    protected void build(){for(Config.Counter target:editor.profile().counters){var item=editor.profile().definition(target.definition);action(item.label,()->minecraft.setScreen(new ColorsScreen(ColorsScreen.this.parent,target,used,item.family)));}}
                });
            });
            button("Measurement: "+(used?"Used":"Remaining"),Math.min(236,width/2),57,Math.min(190,width/2-10),()->{if(stageRules())minecraft.setScreen(new ColorsScreen(parent,counter,!used,family));});
        }
        previewWidth=width>=540?Math.min(190,width/3):0;tableWidth=width-20-(previewWidth==0?0:previewWidth+12);
        button("Use number colors",139,86,24,()->{working.enabled=!working.enabled;refresh();}).check(()->working.enabled);

        count=Math.max(1,(height-218)/22);offset=Math.min(offset,Math.max(0,working.rows.size()-count));
        int lowerW=Math.min(66,tableWidth/5),hexW=Math.min(96,tableWidth/4),hexX=18+lowerW,swatchX=hexX+hexW+6,actionsX=swatchX+30;
        for(int n=offset;n<Math.min(working.rows.size(),offset+count);n++){
            final int i=n;var rule=working.rows.get(i);int y=133+(n-offset)*22;
            if(rule.lower()==0){var locked=button("0",14,y,lowerW,()->{});locked.active=false;}else value("Threshold "+i,Integer.toString(rule.lower()),14,y,lowerW,v->update(i,Integer.parseInt(v),working.rows.get(i).hex()));
            value("Hex "+i,rule.hex(),hexX,y,hexW,v->update(i,working.rows.get(i).lower(),v));
            button("",swatchX,y,24,()->{if(ready())minecraft.setScreen(new RgbScreen(this,i));}).setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("Open color picker")));
            var up=button("^",actionsX,y,20,()->{if(ready()){Collections.swap(working.rows,i,i-1);raw.clear();refresh();}});up.active=i>0;
            var down=button("v",actionsX+24,y,20,()->{if(ready()){Collections.swap(working.rows,i,i+1);raw.clear();refresh();}});down.active=i+1<working.rows.size();
            var remove=button("X",actionsX+48,y,20,()->{if(ready()){working.rows.remove(i);raw.clear();refresh();}});remove.active=rule.lower()!=0;
        }
        button("Add threshold",10,height-76,105,()->{if(!ready())return;if(working.rows.size()>=32)throw new IllegalArgumentException("At most 32 rows.");int max=working.rows.stream().mapToInt(ColorRules.Row::lower).max().orElse(0);if(max==Integer.MAX_VALUE)throw new IllegalArgumentException("No higher threshold fits an integer.");working.rows.add(new ColorRules.Row(max+1,"#FFFFFF"));offset=Math.max(0,working.rows.size()-count);refresh();});
        button("Restore preset",121,height-76,108,()->minecraft.setScreen(new ConfirmDialog(ok->{if(ok){working=used?ColorRules.used(family):ColorRules.remaining(family);invalid.clear();raw.clear();offset=0;}minecraft.setScreen(this);},Component.literal("Restore color preset?"),Component.literal("Only the staged color rules change. Usage counts are preserved."))));
        button("Copy rules...",10,height-53,105,this::copyRules);
        addRenderableWidget(new FlatSlider("Sample count",121,height-53,Math.max(80,Math.min(170,width-141)),sample,0,128,v->sample=v));
        button("Discard",width-174,height-26,68,this::onClose);
        button("Apply colors",width-100,height-26,90,()->{if(!ready())return;working.validate();ColorRules result=ConfigStore.copy(working,ColorRules.class);if(applied!=null)applied.accept(result);else if(used)counter.used=result;else counter.remaining=result;onClose();}).primary();
    }
    private boolean stageRules(){if(!ready())return false;working.validate();ColorRules rules=ConfigStore.copy(working,ColorRules.class);if(applied!=null)applied.accept(rules);else if(used)counter.used=rules;else counter.remaining=rules;return true;}
    void update(int index,int lower,String hex){
        ColorRules copy=ConfigStore.copy(working,ColorRules.class);copy.rows.set(index,new ColorRules.Row(lower,hex));copy.validate();
        working.rows.set(index,new ColorRules.Row(lower,hex.toUpperCase(Locale.ROOT)));
    }
    private TalliumScreen editor(){return rootEditor();}
    @Override protected boolean leaveForNavigation(){if(!ready()||!super.leaveForNavigation())return false;try{working.validate();ColorRules result=ConfigStore.copy(working,ColorRules.class);if(applied!=null)applied.accept(result);else if(used)counter.used=result;else counter.remaining=result;return true;}catch(Exception e){error=Objects.toString(e.getMessage(),"Invalid color rules.");return false;}}
    private void copyRules(){
        if(!ready())return;TalliumScreen editor=editor();if(editor==null){error="No destination profile is open.";return;}
        minecraft.setScreen(new FormScreen(this,"Tallium - Copy color rules"){
            protected void build(){for(Config.Counter destination:editor.profile().counters)if(destination!=counter){Config.Definition d=editor.profile().definition(destination.definition);action(d.label+" / "+(used?"Used":"Remaining"),()->{ColorRules copy=ConfigStore.copy(working,ColorRules.class);copy.validate();if(used)destination.used=copy;else destination.remaining=copy;error="Copied to "+d.label+" in the staged profile.";});}}
        });
    }
    @Override public void render(GuiGraphics g,int mx,int my,float delta){
        if(rebuildPending){rebuildPending=false;UiStyle.rebuild(this,()->{clearWidgets();init();});}
        g.fill(0,0,width,height,UiStyle.BACKGROUND);if(applied!=null)UiStyle.text(g,font,"Group color override",10,62,width-20,UiStyle.TEXT);TalliumScreen editor=editor();Config.Definition definition=editor==null?null:editor.profile().definition(counter.definition);
        UiStyle.text(g,font,(definition==null?"Colors":definition.label)+" / "+(used?"Used":"Remaining"),180,91,tableWidth-180,UiStyle.MUTED);
        UiStyle.text(g,font,"Use number colors",10,91,123,UiStyle.TEXT);
        UiStyle.frame(g,10,112,tableWidth,18,UiStyle.ALTERNATE,UiStyle.BORDER);
        int lowerW=Math.min(66,tableWidth/5),hexW=Math.min(96,tableWidth/4),hexX=18+lowerW,swatchX=hexX+hexW+6;
        UiStyle.text(g,font,"At least",14,117,lowerW,UiStyle.TEXT);UiStyle.text(g,font,"Hex",hexX,117,hexW,UiStyle.TEXT);UiStyle.text(g,font,"Color / Order",swatchX,117,tableWidth-(swatchX-10),UiStyle.TEXT);
        for(int n=offset;n<Math.min(working.rows.size(),offset+count);n++){int y=132+(n-offset)*22;g.fill(10,y,10+tableWidth,y+21,n%2==0?UiStyle.ROW:UiStyle.ALTERNATE);}
        UiStyle.scrollbar(g,tableWidth+13,133,count*22,working.rows.size(),count,offset);
        super.render(g,mx,my,delta);
        for(int n=offset;n<Math.min(working.rows.size(),offset+count);n++){var rule=working.rows.get(n);g.fill(swatchX+3,136+(n-offset)*22,swatchX+21,148+(n-offset)*22,0xff000000|Integer.parseInt(rule.hex().substring(1),16));}
        if(previewWidth>0){int x=width-previewWidth-10;UiStyle.frame(g,x,112,previewWidth,height-196,UiStyle.PANEL,UiStyle.BORDER);UiStyle.text(g,font,"Number preview",x+8,122,previewWidth-16,UiStyle.TEXT);UiStyle.text(g,font,"Sample values",x+8,140,previewWidth-16,UiStyle.MUTED);
            int y=164;for(int value:new int[]{Math.max(0,sample-1),sample,sample+1}){if(definition!=null)g.renderItem(TalliumClient.representative(definition),x+10,y-4);UiStyle.text(g,font,Integer.toString(value),x+34,y,previewWidth-42,working.color(value));y+=25;}
            int explanation=y+12;for(var line:font.split(Component.literal("The greatest threshold at or below the count determines its color. Base 0 is required."),previewWidth-16)){if(explanation>height-100)break;g.drawString(font,line,x+8,explanation,UiStyle.MUTED);explanation+=11;}
        }
        UiStyle.text(g,font,error.isEmpty()?"Staged rules; base 0 cannot be deleted.":error,10,height-21,width-195,error.isEmpty()?UiStyle.MUTED:UiStyle.ERROR);
    }
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){if(super.mouseScrolled(x,y,horizontal,vertical))return true;if(vertical==0)return false;clearFocus();offset=Math.max(0,offset+(vertical<0?1:-1));refresh();return true;}
    @Override public void onClose(){minecraft.setScreen(parent);}
}

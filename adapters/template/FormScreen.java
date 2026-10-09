package dev.sig.tallium.adapter;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;


public abstract class FormScreen extends ConfigScreen {
    private boolean rebuildPending;
    protected final Screen parent;
    protected String error="";
    protected int row,page;
    private final List<Runnable> rows=new ArrayList<>();
    private final Map<FlatEditBox,String> fields=new LinkedHashMap<>();
    private final Map<String,String> invalidValues=new HashMap<>();
    private final List<Label> labels=new ArrayList<>();
    private record Label(String text,int y) {}
    private int left,contentWidth,controlX,controlWidth,count;
    protected FormScreen(Screen parent,String title){super(Component.literal(title));this.parent=parent;navigation(parent,null);}
    @Override protected String activeCategory(){return switch(getClass().getSimpleName()){case "CounterScreen"->"Counters";case "GroupScreen"->"Groups";case "NameSettingsScreen"->"Nametags";case "PlacementScreen"->"Layout";case "GlobalSettingsScreen","ResetActionsScreen","ResetActionScreen","CapabilityScreen","ProfilesScreen","SwitchProfileScreen","RestoreProfileScreen"->"Settings";default->super.activeCategory();};}
    @Override protected boolean leaveForNavigation(){if(!invalidValues.isEmpty()){error="Correct the highlighted fields before leaving.";return false;}return super.leaveForNavigation();}
    protected abstract void build();
    @Override protected void init(){
        initNavigation();rows.clear();labels.clear();fields.clear();row=0;
        contentWidth=width-20;left=10;
        controlWidth=Math.min(340,Math.max(90,contentWidth*2/5));controlX=left+contentWidth-controlWidth;
        build();count=Math.max(1,(height-145)/21);page=Math.min(page,Math.max(0,(rows.size()-1)/count));
        for(int n=page*count;n<Math.min(rows.size(),(page+1)*count);n++){row=103+(n-page*count)*21;rows.get(n).run();}
        addRenderableWidget(FlatButton.builder(Component.literal("Back"),b->onClose()).bounds(left,height-26,64,18).build());
        var previous=FlatButton.builder(Component.literal("<"),b->{page=Math.max(0,page-1);refresh();}).bounds(left+70,height-26,24,18).build();
        previous.active=page>0;addRenderableWidget(previous);
        var next=FlatButton.builder(Component.literal(">"),b->{page++;refresh();}).bounds(left+98,height-26,24,18).build();
        next.active=(page+1)*count<rows.size();addRenderableWidget(next);
    }
    protected void refresh(){rebuildPending=true;}
    protected void action(String source,Runnable handler){rows.add(()->{
        String label=UiText.label(source);int split=label.lastIndexOf(": ");
        Runnable safe=()->{try{handler.run();}catch(Exception e){error=Objects.toString(e.getMessage(),"Invalid settings.");}};
        FlatButton button;
        if(split>0){
            String name=label.substring(0,split),value=label.substring(split+2);labels.add(new Label(name,row));
            boolean bool=value.equals("On")||value.equals("Off");
            button=FlatButton.builder(Component.literal(bool?name+": "+value:value),b->safe.run()).bounds(bool?controlX+controlWidth-24:controlX,row,bool?24:controlWidth,18).build();
            if(bool)button.check(()->value.equals("On"));
        }else{
            labels.add(new Label(label,row));
            button=FlatButton.builder(Component.literal(label),b->safe.run()).bounds(controlX,row,controlWidth,18).build();
        }
        button.setTooltip(Tooltip.create(Component.literal(UiText.help(source))));addRenderableWidget(button);
    });}
    protected void field(String label,String value,java.util.function.Consumer<String> changed){rows.add(()->{
        labels.add(new Label(label,row));
        FlatEditBox box=new FlatEditBox(font,controlX,row,controlWidth-(UiText.defaultValue(label)!=null?25:0),18,Component.literal(label));
        box.setTooltip(Tooltip.create(Component.literal(UiText.help(label))));
        box.setValue(invalidValues.getOrDefault(label,value));box.invalid(invalidValues.containsKey(label));
        box.setResponder(v->{try{changed.accept(v);invalidValues.remove(label);box.invalid(false);error="";}catch(Exception e){invalidValues.put(label,v);box.invalid(true);error=Objects.toString(e.getMessage(),"Invalid settings.");}});
        fields.put(box,label);addRenderableWidget(box);
        String reset=UiText.defaultValue(label);
        if(reset!=null){var button=FlatButton.builder(Component.literal("R"),b->box.setValue(reset)).bounds(controlX+controlWidth-21,row,21,18).build();button.setTooltip(Tooltip.create(Component.literal("Restore default: "+reset)));addRenderableWidget(button);}
    });}
    protected void slider(String label,int value,int min,int max,java.util.function.IntConsumer changed){rows.add(()->{
        labels.add(new Label(label,row));addRenderableWidget(new FlatSlider(label,controlX,row,controlWidth,value,min,max,changed));
    });}
    @Override public void render(GuiGraphics g,int x,int y,float delta){
        if(rebuildPending){rebuildPending=false;UiStyle.rebuild(this,()->{clearWidgets();init();});}
        g.fill(0,0,width,height,UiStyle.BACKGROUND);
        UiStyle.frame(g,left,82,contentWidth,18,UiStyle.ALTERNATE,UiStyle.BORDER);
        UiStyle.text(g,font,"Setting",left+4,87,controlX-left-12,UiStyle.TEXT);
        UiStyle.text(g,font,"Value / Action",controlX+4,87,controlWidth-8,UiStyle.TEXT);
        for(int n=0;n<Math.min(count,rows.size()-page*count);n++)g.fill(left,102+n*21,left+contentWidth,122+n*21,n%2==0?UiStyle.ROW:UiStyle.ALTERNATE);
        for(Label label:labels)UiStyle.text(g,font,label.text,left+4,label.y+5,controlX-left-12,UiStyle.TEXT);
        UiStyle.scrollbar(g,left+contentWidth+3,103,Math.max(1,Math.min(count,rows.size())*21),rows.size(),count,page*count);
        super.render(g,x,y,delta);
        UiStyle.text(g,font,"Page "+(page+1)+" / "+Math.max(1,(rows.size()+count-1)/count),left+130,height-21,contentWidth-132,UiStyle.MUTED);
        if(!error.isEmpty())UiStyle.text(g,font,error,left,height-39,contentWidth,UiStyle.ERROR);
    }
    @Override public void onClose(){if(!invalidValues.isEmpty()){error="Correct the highlighted fields before leaving.";return;}minecraft.setScreen(parent);}
    @Override public boolean mouseClicked(@CLICK_SIGNATURE@){@CLICK_VARS@
        if(button==1)for(var entry:fields.entrySet()){String reset=UiText.defaultValue(entry.getValue());if(reset!=null&&entry.getKey().isMouseOver(mouseX,mouseY)){entry.getKey().setValue(reset);return true;}}
        return super.mouseClicked(@CLICK_SUPER@);
    }
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){
        if(super.mouseScrolled(x,y,horizontal,vertical))return true;if(vertical==0)return false;
        clearFocus();page=Math.max(0,Math.min(Math.max(0,(rows.size()-1)/count),page+(vertical<0?1:-1)));refresh();return true;
    }
}

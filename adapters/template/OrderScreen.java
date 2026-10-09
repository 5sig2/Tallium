package dev.sig.tallium.adapter;

import dev.sig.tallium.config.Config;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;


public final class OrderScreen extends ConfigScreen {
    private final TalliumScreen editor;private final boolean counters;private int offset,count;private boolean rebuildPending;
    public OrderScreen(TalliumScreen editor){this(editor,false);}
    public OrderScreen(TalliumScreen editor,boolean counters){super(Component.literal("Tallium - "+(counters?"Counter order":"Nametag order")));this.editor=editor;this.counters=counters;navigation(editor,counters?"Counters":"Nametags");}
    private int size(){return counters?editor.profile().counters.size():editor.profile().nametagOrder.size();}
    private Config.Definition definition(int index){return counters?editor.profile().definition(editor.profile().counters.get(index).definition):editor.profile().definition(editor.profile().nametagOrder.get(index));}
    private void swap(int a,int b){if(counters)Collections.swap(editor.profile().counters,a,b);else Collections.swap(editor.profile().nametagOrder,a,b);rebuildPending=true;}
    @Override protected void init(){
        initNavigation();count=Math.max(1,(height-136)/21);offset=Math.min(offset,Math.max(0,size()-count));
        for(int n=offset;n<Math.min(size(),offset+count);n++){final int i=n;int y=101+(n-offset)*21;
            var up=FlatButton.builder(Component.literal("^"),b->swap(i,i-1)).bounds(width-60,y,22,18).build();up.active=i>0;addRenderableWidget(up);
            var down=FlatButton.builder(Component.literal("v"),b->swap(i,i+1)).bounds(width-34,y,22,18).build();down.active=i+1<size();addRenderableWidget(down);
        }
        addRenderableWidget(FlatButton.builder(Component.literal("Back"),b->onClose()).bounds(10,height-26,64,18).build());
    }
    @Override public void render(GuiGraphics g,int mx,int my,float delta){
        if(rebuildPending){rebuildPending=false;UiStyle.rebuild(this,()->{clearWidgets();init();});}
        g.fill(0,0,width,height,UiStyle.BACKGROUND);
        UiStyle.frame(g,10,80,width-20,18,UiStyle.ALTERNATE,UiStyle.BORDER);UiStyle.text(g,font,"Item",14,85,width-85,UiStyle.TEXT);UiStyle.text(g,font,"Order",width-62,85,48,UiStyle.TEXT);
        for(int n=offset;n<Math.min(size(),offset+count);n++){var d=definition(n);int y=101+(n-offset)*21;g.fill(10,y,width-10,y+20,n%2==0?UiStyle.ROW:UiStyle.ALTERNATE);g.renderItem(TalliumClient.representative(d),14,y+1);UiStyle.text(g,font,d.label,35,y+5,width-103,UiStyle.TEXT);}
        UiStyle.scrollbar(g,width-7,101,count*21,size(),count,offset);super.render(g,mx,my,delta);
        UiStyle.text(g,font,"Changes remain staged.",82,height-21,width-92,UiStyle.MUTED);
    }
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){if(super.mouseScrolled(x,y,horizontal,vertical))return true;if(vertical==0)return false;offset=Math.max(0,offset+(vertical<0?1:-1));clearFocus();rebuildPending=true;return true;}
    @Override public void onClose(){minecraft.setScreen(editor);}
}

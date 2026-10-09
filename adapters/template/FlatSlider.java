package dev.sig.tallium.adapter;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;
import java.util.function.IntConsumer;

final class FlatSlider extends AbstractSliderButton {
    private final String label;private final int min,max;private final IntConsumer changed;
    FlatSlider(String label,int x,int y,int width,int value,int min,int max,IntConsumer changed){
        super(x,y,width,18,Component.literal(label),Math.max(0,Math.min(1,(value-min)/(double)Math.max(1,max-min))));
        this.label=label;this.min=min;this.max=max;this.changed=changed;updateMessage();
    }
    private int number(){return min+(int)Math.round(value*(max-min));}
    @Override protected void updateMessage(){if(label!=null)setMessage(Component.literal(label+": "+number()));}
    @Override protected void applyValue(){changed.accept(number());}
    @Override public void @WIDGET_RENDER@(GuiGraphics g,int mx,int my,float delta){
        int line=getY()+14,end=getX()+getWidth()-4,handle=getX()+4+(int)Math.round(value*Math.max(1,getWidth()-8));
        g.fill(getX()+4,line,end,line+2,UiStyle.BORDER);g.fill(getX()+4,line,handle,line+2,UiStyle.ACCENT);
        UiStyle.frame(g,handle-3,line-3,6,8,UiStyle.SELECTED,isHoveredOrFocused()?UiStyle.ACCENT:UiStyle.MUTED);
        var font=Minecraft.getInstance().font;String number=Integer.toString(number());
        UiStyle.text(g,font,number,end-font.width(number),getY()+2,getWidth()-8,UiStyle.TEXT);
    }
}

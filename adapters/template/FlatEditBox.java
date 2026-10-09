package dev.sig.tallium.adapter;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

final class FlatEditBox extends EditBox {
    private boolean invalid;
    FlatEditBox(Font font,int x,int y,int width,int height,Component label){
        super(font,x+4,y+5,Math.max(8,width-8),Math.max(9,height-10),label);
        setBordered(false);setTextColor(UiStyle.TEXT);setMaxLength(160);
    }
    void invalid(boolean invalid){this.invalid=invalid;}
    @Override public boolean isMouseOver(double x,double y){return visible&&x>=getX()-4&&x<getX()+getWidth()+4&&y>=getY()-5&&y<getY()+getHeight()+5;}
    @Override public void @WIDGET_RENDER@(GuiGraphics g,int mx,int my,float delta){
        UiStyle.frame(g,getX()-4,getY()-5,getWidth()+8,getHeight()+10,UiStyle.FIELD,invalid?UiStyle.ERROR:isFocused()?UiStyle.ACCENT:UiStyle.BORDER);
        super.@WIDGET_RENDER@(g,mx,my,delta);
    }
}

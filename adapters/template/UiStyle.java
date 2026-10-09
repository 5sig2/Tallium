package dev.sig.tallium.adapter;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;


final class UiStyle {
    static final int BACKGROUND=0xe5090b0c, PANEL=0xeb191919, BORDER=0xff666666;
    static final int ROW=0xc0101010, ALTERNATE=0xc0303030, SELECTED=0xff3e4a50;
    static final int TEXT=0xffdddddd, MUTED=0xffaaaaaa, ACCENT=0xff68b5d5;
    static final int GREEN=0xff77d877, ERROR=0xffff7777, FIELD=0xff101010;
    private UiStyle() {}
    static void frame(GuiGraphics g,int x,int y,int w,int h,int background,int border){
        if(w<=0||h<=0)return;
        g.fill(x,y,x+w,y+h,background);
        g.fill(x,y,x+w,y+1,border);g.fill(x,y+h-1,x+w,y+h,border);
        g.fill(x,y,x+1,y+h,border);g.fill(x+w-1,y,x+w,y+h,border);
    }
    static void text(GuiGraphics g,Font font,String text,int x,int y,int width,int color){
        if(width>0)g.drawString(font,font.plainSubstrByWidth(text,width),x,y,color);
    }
    static void scrollbar(GuiGraphics g,int x,int y,int h,int total,int visible,int offset){
        if(total<=visible||h<1)return;
        g.fill(x,y,x+3,y+h,FIELD);
        int thumb=Math.min(h,Math.max(10,h*visible/total));
        int top=y+(h-thumb)*Math.min(offset,total-visible)/Math.max(1,total-visible);
        g.fill(x,top,x+3,top+thumb,offset==0?BORDER:MUTED);
    }

    static void rebuild(Screen screen,Runnable build){
        var focused=screen.getFocused();
        int cursor=focused instanceof EditBox box?box.getCursorPosition():0;
        screen.clearFocus();build.run();
        if(focused instanceof AbstractWidget old)for(var child:screen.children()){
            if(child instanceof AbstractWidget next&&old.getClass()==next.getClass()&&old.getX()==next.getX()&&old.getY()==next.getY()){
                screen.setFocused(next);next.setFocused(true);
                if(next instanceof EditBox box)box.setCursorPosition(Math.min(cursor,box.getValue().length()));
                return;
            }
        }
    }
}

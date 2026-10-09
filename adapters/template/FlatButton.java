package dev.sig.tallium.adapter;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import java.util.function.BooleanSupplier;


final class FlatButton extends Button {
    private enum Kind { ACTION, CHECK, ROW, TAB }
    private Kind kind=Kind.ACTION;
    private BooleanSupplier selected=()->false;
    private boolean primary;
    private FlatButton(int x,int y,int w,int h,Component label,OnPress pressed,CreateNarration narration){
        super(x,y,w,h,label,pressed,narration);
    }
    FlatButton primary(){primary=true;return this;}
    FlatButton tab(BooleanSupplier selection){kind=Kind.TAB;selected=selection;return this;}
    FlatButton row(BooleanSupplier selection){kind=Kind.ROW;selected=selection;return this;}
    FlatButton check(BooleanSupplier checked){kind=Kind.CHECK;selected=checked;return this;}
    @Override protected net.minecraft.network.chat.MutableComponent createNarrationMessage(){
        if(kind==Kind.CHECK)return Component.literal(getMessage().getString()+": "+(selected.getAsBoolean()?"On":"Off"));
        return super.createNarrationMessage();
    }
    @Override protected void @BUTTON_RENDER@(GuiGraphics g,int mx,int my,float delta){
        var font=Minecraft.getInstance().font;
        boolean focused=isHoveredOrFocused(), chosen=selected.getAsBoolean();
        if(kind==Kind.CHECK){
            int size=Math.min(10,Math.min(getWidth(),getHeight())-2),x=getX()+(getWidth()-size)/2,y=getY()+(getHeight()-size)/2;
            UiStyle.frame(g,x,y,size,size,UiStyle.FIELD,active?(focused?UiStyle.ACCENT:UiStyle.MUTED):UiStyle.BORDER);
            if(chosen)g.fill(x+2,y+2,x+size-2,y+size-2,active?UiStyle.GREEN:UiStyle.BORDER);
            return;
        }
        if(kind==Kind.ROW){
            if(chosen||focused)g.fill(getX(),getY(),getX()+getWidth(),getY()+getHeight(),chosen?UiStyle.SELECTED:UiStyle.ALTERNATE);
            if(chosen)g.fill(getX(),getY(),getX()+1,getY()+getHeight(),UiStyle.ACCENT);
            if(isFocused())UiStyle.frame(g,getX(),getY(),getWidth(),getHeight(),0x00444444,UiStyle.ACCENT);
            return;
        }
        int fill=chosen||primary?UiStyle.SELECTED:focused?0xff3b3b3b:0xff252525;
        UiStyle.frame(g,getX(),getY(),getWidth(),getHeight(),fill,focused||chosen||primary?UiStyle.ACCENT:UiStyle.BORDER);
        String text=font.plainSubstrByWidth(getMessage().getString(),Math.max(0,getWidth()-8));
        g.drawString(font,text,getX()+(getWidth()-font.width(text))/2,getY()+(getHeight()-8)/2,active?UiStyle.TEXT:UiStyle.MUTED);
    }
    public static Builder builder(Component label,OnPress pressed){return new Builder(label,pressed);}
    static final class Builder extends Button.Builder {
        private final Component label;private final OnPress pressed;
        private int x,y,w=100,h=18;private Tooltip tooltip;private CreateNarration narration=DEFAULT_NARRATION;
        Builder(Component label,OnPress pressed){super(label,pressed);this.label=label;this.pressed=pressed;}
        @Override public Builder bounds(int x,int y,int w,int h){this.x=x;this.y=y;this.w=Math.max(1,w);this.h=Math.max(1,h);return this;}
        @Override public Builder pos(int x,int y){this.x=x;this.y=y;return this;}
        @Override public Builder width(int w){this.w=w;return this;}
        @Override public Builder size(int w,int h){this.w=w;this.h=h;return this;}
        @Override public Builder tooltip(Tooltip tooltip){this.tooltip=tooltip;return this;}
        @Override public Builder createNarration(CreateNarration narration){this.narration=narration;return this;}
        @Override public FlatButton build(){FlatButton button=new FlatButton(x,y,w,h,label,pressed,narration);button.setTooltip(tooltip);return button;}
    }
}

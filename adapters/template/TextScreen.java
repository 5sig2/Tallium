package dev.sig.tallium.adapter;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
public final class TextScreen extends ConfigScreen {
    private final Screen parent;private final String text;private int offset;private String error="";
    public TextScreen(Screen parent,String name,String text){super(Component.literal(name));this.parent=parent;this.text=text;navigation(parent,null);}
    protected void init(){initNavigation();EditBox box=new FlatEditBox(font,10,101,width-20,20,Component.literal("Selectable text"));box.setMaxLength(Math.max(131072,text.length()));box.setValue(text);addRenderableWidget(box);setInitialFocus(box);
        addRenderableWidget(FlatButton.builder(Component.literal("Copy"),b->{try{minecraft.keyboardHandler.setClipboard(text);error="Copied.";}catch(Exception e){error="Select the text and copy it manually.";}}).bounds(width-90,height-30,80,20).build());
        addRenderableWidget(FlatButton.builder(Component.literal("Back"),b->onClose()).bounds(10,height-30,80,20).build());}
    public void render(GuiGraphics g,int x,int y,float delta){
        g.fill(0,0,width,height,UiStyle.BACKGROUND);UiStyle.text(g,font,text.startsWith("TLM1:")?"Select and copy the code below. Importing requires a separate preview.":"Select the text below or use Copy.",10,84,width-20,UiStyle.MUTED);
        if(!text.startsWith("TLM1:")){
            var lines=font.split(Component.literal(text),width-40);int count=Math.max(1,(height-201)/12);offset=Math.min(offset,Math.max(0,lines.size()-count));
            UiStyle.frame(g,10,139,width-20,height-193,UiStyle.PANEL,UiStyle.BORDER);
            for(int n=offset;n<Math.min(lines.size(),offset+count);n++)g.drawString(font,lines.get(n),20,151+(n-offset)*12,UiStyle.TEXT);
            UiStyle.scrollbar(g,width-17,150,Math.max(1,height-217),lines.size(),count,offset);
        }
        UiStyle.text(g,font,error,98,height-24,width-198,UiStyle.MUTED);super.render(g,x,y,delta);
    }
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){if(super.mouseScrolled(x,y,horizontal,vertical))return true;offset=Math.max(0,offset+(vertical<0?3:-3));return vertical!=0;}
    public void onClose(){minecraft.setScreen(parent);}
}

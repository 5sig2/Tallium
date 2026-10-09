package dev.sig.tallium.adapter;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;


public final class RgbScreen extends ConfigScreen {
    private final ColorsScreen colors;private final int index;private final String original;
    private int red,green,blue,left,top,panelWidth,size,hueX,controlX,controlWidth,drag;
    private float hue,saturation,brightness;
    private boolean syncing;private String error="";
    private final Set<String> invalid=new HashSet<>();
    private final Map<String,FlatEditBox> fields=new LinkedHashMap<>();
    public RgbScreen(ColorsScreen colors,int index){super(Component.literal("Tallium - Color picker"));this.colors=colors;navigation(colors,"Colors");this.index=index;original=colors.working.rows.get(index).hex();setHex(original);}
    private String hex(){return String.format(Locale.ROOT,"#%02X%02X%02X",red,green,blue);}
    private void setHex(String value){if(!value.matches("#[0-9a-fA-F]{6}"))throw new IllegalArgumentException("Use #RRGGBB.");int rgb=Integer.parseInt(value.substring(1),16);red=rgb>>16&255;green=rgb>>8&255;blue=rgb&255;toHsv();}
    private void toHsv(){float r=red/255f,g=green/255f,b=blue/255f,max=Math.max(r,Math.max(g,b)),min=Math.min(r,Math.min(g,b)),range=max-min;brightness=max;saturation=max==0?0:range/max;if(range==0)return;float h=max==r?(g-b)/range:max==g?2+(b-r)/range:4+(r-g)/range;hue=(h/6+1)%1;}
    private static int rgb(float h,float s,float v){float hh=h*6;int segment=(int)hh;float f=hh-segment,p=v*(1-s),q=v*(1-s*f),t=v*(1-s*(1-f));float r,g,b;switch(segment%6){case 0->{r=v;g=t;b=p;}case 1->{r=q;g=v;b=p;}case 2->{r=p;g=v;b=t;}case 3->{r=p;g=q;b=v;}case 4->{r=t;g=p;b=v;}default->{r=v;g=p;b=q;}}return 0xff000000|(Math.round(r*255)<<16)|(Math.round(g*255)<<8)|Math.round(b*255);}
    private void fromHsv(){int rgb=rgb(hue,saturation,brightness);red=rgb>>16&255;green=rgb>>8&255;blue=rgb&255;sync();}
    private void sync(){syncing=true;fields.forEach((name,field)->{field.setValue(switch(name){case "Red"->Integer.toString(red);case "Green"->Integer.toString(green);case "Blue"->Integer.toString(blue);default->hex();});field.invalid(false);});invalid.clear();error="";syncing=false;}
    private FlatButton button(String text,int x,int y,int w,Runnable action){var b=FlatButton.builder(Component.literal(text),pressed->{try{action.run();}catch(Exception e){error=Objects.toString(e.getMessage(),"Invalid color.");}}).bounds(x,y,w,18).build();addRenderableWidget(b);return b;}
    private void field(String name,String value,int y,java.util.function.Consumer<String> changed){var field=new FlatEditBox(font,controlX+42,y,controlWidth-42,18,Component.literal(name));field.setMaxLength(name.equals("Hex")?7:3);field.setValue(value);field.setResponder(v->{if(syncing)return;try{changed.accept(v);invalid.remove(name);field.invalid(false);error="";syncOthers(name);}catch(Exception e){invalid.add(name);field.invalid(true);error=Objects.toString(e.getMessage(),"Invalid color.");}});fields.put(name,field);addRenderableWidget(field);}
    private void syncOthers(String changed){syncing=true;fields.forEach((name,field)->{if(!name.equals(changed)&&!invalid.contains(name))field.setValue(switch(name){case "Red"->Integer.toString(red);case "Green"->Integer.toString(green);case "Blue"->Integer.toString(blue);default->hex();});});syncing=false;}
    private int channel(String value){int n=Integer.parseInt(value);if(n<0||n>255)throw new IllegalArgumentException("RGB channels use 0-255.");return n;}
    @Override protected void init(){
        initNavigation();fields.clear();invalid.clear();panelWidth=Math.min(380,width-20);left=(width-panelWidth)/2;top=Math.max(82,(height-244)/2);size=Math.max(48,Math.min(136,Math.min(panelWidth/2-20,height-144)));hueX=left+size+17;controlX=hueX+22;controlWidth=left+panelWidth-controlX-8;
        field("Red",Integer.toString(red),top+40,v->{red=channel(v);toHsv();});field("Green",Integer.toString(green),top+62,v->{green=channel(v);toHsv();});field("Blue",Integer.toString(blue),top+84,v->{blue=channel(v);toHsv();});field("Hex",hex(),top+106,this::setHex);
        int y=top+40+size+12;
        button("Copy hex",left+8,y,76,()->minecraft.keyboardHandler.setClipboard(hex()));
        button("Paste",left+90,y,58,()->{setHex(minecraft.keyboardHandler.getClipboard().trim());sync();});
        button("Reset",left+154,y,58,()->{setHex(original);sync();});
        int recentX=left+8;for(String recent:ColorsScreen.recent){if(recentX+29>left+panelWidth-8)break;String value=recent;button(" ",recentX,y+23,25,()->{setHex(value);sync();}).setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(value)));recentX+=29;}
        button("Cancel",left+panelWidth-154,y+48,68,this::onClose);
        button("Apply color",left+panelWidth-80,y+48,72,()->{if(!invalid.isEmpty()){error="Correct the highlighted fields first.";return;}String value=hex();colors.update(index,colors.working.rows.get(index).lower(),value);ColorsScreen.recent.remove(value);ColorsScreen.recent.addFirst(value);while(ColorsScreen.recent.size()>8)ColorsScreen.recent.removeLast();onClose();}).primary();
    }
    @Override public void render(GuiGraphics g,int mx,int my,float delta){
        g.fill(0,0,width,height,UiStyle.BACKGROUND);int footer=top+40+size+12;
        UiStyle.frame(g,left,top,panelWidth,Math.min(height-top-4,size+118),UiStyle.PANEL,UiStyle.BORDER);
        UiStyle.text(g,font,title.getString(),left+8,top+10,panelWidth-78,UiStyle.TEXT);UiStyle.frame(g,left+panelWidth-62,top+8,54,20,rgb(hue,saturation,brightness),UiStyle.MUTED);
        int svX=left+8,svY=top+40;
        for(int x=0;x<size;x+=2)for(int y=0;y<size;y+=2)g.fill(svX+x,svY+y,svX+Math.min(size,x+2),svY+Math.min(size,y+2),rgb(hue,x/(float)(size-1),1-y/(float)(size-1)));
        for(int y=0;y<size;y+=2)g.fill(hueX,svY+y,hueX+12,svY+Math.min(size,y+2),rgb(y/(float)size,1,1));
        int sx=svX+Math.round(saturation*(size-1)),sy=svY+Math.round((1-brightness)*(size-1)),hy=svY+Math.round(hue*(size-1));
        UiStyle.frame(g,sx-2,sy-2,5,5,0x00000000,UiStyle.TEXT);g.fill(hueX-2,hy,hueX+14,hy+1,UiStyle.TEXT);
        int yy=top+45;for(String name:List.of("Red","Green","Blue","Hex")){UiStyle.text(g,font,name,controlX,yy,38,UiStyle.TEXT);yy+=22;}
        super.render(g,mx,my,delta);
        int rx=left+11;for(String value:ColorsScreen.recent){if(rx+23>left+panelWidth-8)break;g.fill(rx,footer+27,rx+19,footer+37,0xff000000|Integer.parseInt(value.substring(1),16));rx+=29;}
        if(!error.isEmpty())UiStyle.text(g,font,error,left+8,Math.min(height-12,footer+72),panelWidth-16,UiStyle.ERROR);
    }
    private void pick(double x,double y){if(drag==1){saturation=Math.max(0,Math.min(1,(float)(x-left-8)/(size-1)));brightness=1-Math.max(0,Math.min(1,(float)(y-top-40)/(size-1)));}else hue=Math.max(0,Math.min(.9999f,(float)(y-top-40)/(size-1)));fromHsv();}
    @Override public boolean mouseClicked(@CLICK_SIGNATURE@){@CLICK_VARS@ drag=0;if(button==0&&mouseY>=top+40&&mouseY<top+40+size){if(mouseX>=left+8&&mouseX<left+8+size)drag=1;else if(mouseX>=hueX&&mouseX<hueX+12)drag=2;if(drag!=0){pick(mouseX,mouseY);return true;}}return super.mouseClicked(@CLICK_SUPER@);}
    @Override public boolean mouseDragged(@DRAG_SIGNATURE@){@DRAG_VARS@ if(button==0&&drag!=0){pick(mouseX,mouseY);return true;}return super.mouseDragged(@DRAG_SUPER@);}
    @Override public boolean mouseReleased(@RELEASE_SIGNATURE@){@RELEASE_VARS@ if(button==0&&drag!=0){drag=0;return true;}return super.mouseReleased(@RELEASE_SUPER@);}
    @Override protected boolean leaveForNavigation(){if(!invalid.isEmpty()){error="Correct the highlighted fields first.";return false;}try{colors.update(index,colors.working.rows.get(index).lower(),hex());return super.leaveForNavigation();}catch(Exception e){error=Objects.toString(e.getMessage(),"Invalid color.");return false;}}
    @Override public void onClose(){minecraft.setScreen(colors);}
}

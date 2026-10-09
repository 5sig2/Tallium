package dev.sig.tallium.adapter;

import dev.sig.tallium.config.Config;
import dev.sig.tallium.display.Value;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;
import java.util.*;


public final class PlayerPopup {
    private static UUID player;
    private static String name="";
    private static long opened;
    public static void show(Player target){player=target.getUUID();name=target.getName().getString();opened=System.nanoTime();}
    public static void clear(){player=null;PlayerPortrait.clear();}
    public static boolean visible(){return player!=null&&elapsed()<TalliumClient.config.active().popup.seconds;}
    private static double elapsed(){return (System.nanoTime()-opened)/1_000_000_000.0;}
    private static List<Config.Definition> definitions(Config.Profile p){return p.definitions.stream().filter(d->d.enabled).toList();}
    public static int width(Config.Profile p,int screenWidth){return Math.min(204,Math.max(80,(int)(screenWidth/p.popup.scale)-20));}
    public static int height(Config.Profile p,int screenHeight){return Math.min(Math.min(116,26+Math.max(1,definitions(p).size())*18),Math.max(64,(int)(screenHeight/p.popup.scale)-20));}
    public static int x(Config.Profile p,int screenWidth){int w=(int)Math.ceil(width(p,screenWidth)*p.popup.scale);return Math.max(0,Math.min(screenWidth-w,p.popup.right?screenWidth-w-p.popup.x:p.popup.x));}
    public static int y(Config.Profile p,int screenHeight){int h=(int)Math.ceil(height(p,screenHeight)*p.popup.scale);return Math.max(0,Math.min(screenHeight-h,p.popup.y));}
    public static void render(GuiGraphics graphics,Config.Profile p,boolean preview){
        if(!preview&&(!p.popup.enabled||!visible()))return;
        Minecraft mc=Minecraft.getInstance();int x=x(p,graphics.guiWidth()),y=y(p,graphics.guiHeight());double renderScale=p.popup.scale;
        int w=width(p,graphics.guiWidth()),h=height(p,graphics.guiHeight());
        @POSE_PUSH@ @POSE_TRANSLATE@ @POSE_SCALE@
        if(p.popup.background)HudShapes.panel(graphics,0,0,w,h,p.popup.radius,((int)(p.popup.opacity*255)<<24)|p.popup.color);
        UUID idPlayer=player!=null?player:mc.player!=null?mc.player.getUUID():new UUID(0,0);
        String title=preview&&player==null?mc.player==null?"Player":mc.player.getName().getString():name;
        var defs=definitions(p);int count=Math.max(1,(h-26)/18),pages=Math.max(1,(defs.size()+count-1)/count);
        int page=preview?0:(int)(elapsed()/2)%pages,first=page*count;
        String pageText=(page+1)+"/"+pages;
        graphics.drawString(mc.font,mc.font.plainSubstrByWidth(title,Math.max(1,w-74-(pages>1?mc.font.width(pageText)+6:0))),66,8,0xfff3f3f3,true);
        if(pages>1)graphics.drawString(mc.font,pageText,w-8-mc.font.width(pageText),8,0xff9da8ac,false);
        graphics.fill(59,11,60,h-9,0x5a657d76);
        for(int n=first;n<Math.min(defs.size(),first+count);n++){
            var d=defs.get(n);int row=22+(n-first)*18;Value value=preview&&player==null?Value.known(new int[]{23,3,47,18,9}[(n-first)%5]):TalliumClient.usage(idPlayer,d);
            String text=consumed(value);int valueWidth=mc.font.width(text);
            graphics.renderItem(TalliumClient.representative(d),64,row);
            graphics.drawString(mc.font,mc.font.plainSubstrByWidth(label(d),Math.max(0,w-95-valueWidth)),83,row+4,0xffd7dfe2,false);
            graphics.drawString(mc.font,text,w-8-valueWidth,row+4,TalliumClient.usageColor(d,value),true);
        }
        double progress=preview?1:Math.max(0,1-elapsed()/p.popup.seconds);
        graphics.fill(6,h-3,6+(int)((w-12)*progress),h-2,0xff88b8a3);
        @POSE_POP@
        PlayerPortrait.draw(graphics,idPlayer,title,x,y,w,h,renderScale);
    }
    public static String consumed(Value value){return value.hasCount()?"-"+value.count():value.text();}
    private static String label(Config.Definition d){
        String item=d.selector.item();if(item==null||!item.startsWith("minecraft:"))return d.label;
        String key=item.substring(10);if(!d.label.equals(Config.starter(key).label))return d.label;
        return switch(key){case "ender_pearl"->"Pearls";case "totem_of_undying"->"Totems";case "wind_charge"->"Wind Charges";case "golden_apple"->"Golden Apples";case "experience_bottle"->"XP Bottles";default->d.label;};
    }
}

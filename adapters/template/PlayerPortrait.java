package dev.sig.tallium.adapter;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import java.util.UUID;


final class PlayerPortrait {
    private static Portrait portrait;
    private static final class Portrait extends RemotePlayer {

        Portrait(ClientLevel level,UUID id,String name){super(level,new GameProfile(id,name));setId(-1);@PORTRAIT_PARTS@}
        @Override public boolean isSpectator(){return false;}
        @Override public boolean isInvisible(){return false;}
    }
    static void clear(){portrait=null;}
    static void draw(GuiGraphics graphics,UUID uuid,String name,int x,int y,int w,int h,double scale){
        Minecraft mc=Minecraft.getInstance();
        if(mc.level==null){
            clear();var skin=net.minecraft.client.resources.DefaultPlayerSkin.get(uuid);
            var id=@SKIN_TEXTURE@;int dx=x+(int)Math.round(16*scale),dy=y+(int)Math.round((h-24)/2.0*scale),size=(int)Math.round(24*scale);
            @PORTRAIT_FALLBACK@
            return;
        }
        if(portrait==null||portrait.level()!=mc.level||!portrait.getUUID().equals(uuid))portrait=new Portrait(mc.level,uuid,name);
        int left=x+(int)Math.round(4*scale),right=x+(int)Math.round(56*scale);
        int top=y+(int)Math.round(8*scale),bottom=y+(int)Math.round((h-8)*scale);

        InventoryScreen.@PORTRAIT_DRAW@(graphics,left,top,right,bottom,Math.max(1,(int)Math.round(Math.min(50,(h-18)/1.9)*scale)),0,
            (left+right)/2f+40f,(top+bottom)/2f+14.6f,portrait);
    }
}

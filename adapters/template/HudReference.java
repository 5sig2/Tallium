package dev.sig.tallium.adapter;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.@IDENTIFIER@;


public final class HudReference {
    private static final @IDENTIFIER@ ID=@IDENTIFIER@.fromNamespaceAndPath("tallium","hud_reference");
    private static int generation,worldGeneration;
    private static boolean available;
    private static int guiWidth,guiHeight;
    public static void beforeScreen(Screen next){
        Minecraft mc=Minecraft.getInstance();if(next==null||mc.screen!=null||mc.level==null)return;
        int request=++generation,world=worldGeneration;
        int referenceWidth=mc.getWindow().getGuiScaledWidth(),referenceHeight=mc.getWindow().getGuiScaledHeight();
        try{@CAPTURE_REFERENCE@}catch(Exception ignored){clear();}
    }
    private static void accept(NativeImage image,int request,int world,int referenceWidth,int referenceHeight){
        Minecraft mc=Minecraft.getInstance();mc.execute(()->{
            if(request!=generation||world!=worldGeneration||mc.level==null){image.close();return;}
            if(referenceWidth!=mc.getWindow().getGuiScaledWidth()||referenceHeight!=mc.getWindow().getGuiScaledHeight()){image.close();clear();return;}
            try{mc.getTextureManager().release(ID);mc.getTextureManager().register(ID,@DYNAMIC_TEXTURE@);guiWidth=referenceWidth;guiHeight=referenceHeight;available=true;}
            catch(Exception e){image.close();available=false;}
        });
    }
    public static boolean available(){return available;}
    public static void clear(){generation++;worldGeneration++;if(available)Minecraft.getInstance().getTextureManager().release(ID);available=false;}
    public static boolean draw(GuiGraphics g,int width,int height){if(!available)return false;if(width!=guiWidth||height!=guiHeight){clear();return false;}var id=ID;@REFERENCE_BLIT@ return true;}
}

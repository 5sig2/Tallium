package dev.sig.tallium.adapter;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.@IDENTIFIER@;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.DataComponents;
import java.util.*;


public final class VanillaIcons {
    private static final Map<String,@IDENTIFIER@> cache=new HashMap<>();
    private static final Set<String> missing=new HashSet<>();
    public static void clear(){Minecraft mc=Minecraft.getInstance();cache.values().forEach(mc.getTextureManager()::release);cache.clear();missing.clear();}
    public static @IDENTIFIER@ cachedTexture(ItemStack stack){String item=BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();var p=stack.get(DataComponents.POTION_CONTENTS);return cache.get(item+"|"+(p==null?-1:p.getColor()));}
    public static void draw(GuiGraphics g,ItemStack stack,int x,int y){
        @IDENTIFIER@ id=texture(stack);if(id==null){fallback(g,x,y);return;}
        @BLIT@
    }
    public static @IDENTIFIER@ texture(ItemStack stack){
        String item=BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        if(!item.startsWith("minecraft:"))return null;
        var potion=stack.get(DataComponents.POTION_CONTENTS);int tint=potion==null?-1:potion.getColor();
        String cacheKey=item+"|"+tint;
        @IDENTIFIER@ id=cache.get(cacheKey);
        if(id==null&&missing.size()>=2048)return null;
        if(id==null&&!missing.contains(cacheKey)){
            String path=item.substring(10);String texture=path.equals("cobweb")?"textures/block/cobweb.png":"textures/item/"+path+".png";
            try{
                Minecraft mc=Minecraft.getInstance();var supplier=mc.getVanillaPackResources().getResource(PackType.CLIENT_RESOURCES,@IDENTIFIER@.withDefaultNamespace(texture));
                if(path.equals("tipped_arrow"))supplier=mc.getVanillaPackResources().getResource(PackType.CLIENT_RESOURCES,@IDENTIFIER@.withDefaultNamespace("textures/item/tipped_arrow_base.png"));
                if(supplier==null){missing.add(cacheKey);return null;}
                NativeImage image;try(var stream=supplier.get()){image=NativeImage.read(stream);}
                if(potion!=null){var overlay=mc.getVanillaPackResources().getResource(PackType.CLIENT_RESOURCES,@IDENTIFIER@.withDefaultNamespace("textures/item/"+(path.equals("tipped_arrow")?"tipped_arrow_head":"potion_overlay")+".png"));
                    if(overlay!=null)try(var stream=overlay.get();var layer=NativeImage.read(stream)){for(int yy=0;yy<Math.min(16,image.getHeight());yy++)for(int xx=0;xx<Math.min(16,image.getWidth());xx++){
                        int foreground=image.@PIXEL_GET@(xx,yy),color=layer.@PIXEL_GET@(xx,yy);color=@PIXEL_TO_ARGB@;int alpha=color>>>24;
                        if(alpha!=0&&(foreground>>>24)==0){int r=((color>>16)&255)*((tint>>16)&255)/255,b=(color&255)*(tint&255)/255,green=((color>>8)&255)*((tint>>8)&255)/255;
                            int argb=(alpha<<24)|(r<<16)|(green<<8)|b;image.@PIXEL_SET@(xx,yy,@PIXEL_FROM_ARGB@);}}}
                }
                if(cache.size()>=2048){image.close();missing.add(cacheKey);return null;}
                id=@IDENTIFIER@.fromNamespaceAndPath("tallium","vanilla/"+path+"_"+Integer.toHexString(tint));mc.getTextureManager().register(id,@DYNAMIC_TEXTURE@);cache.put(cacheKey,id);
            }catch(Exception ex){missing.add(cacheKey);return null;}
        }
        return id;
    }
    private static void fallback(GuiGraphics g,int x,int y){g.drawString(Minecraft.getInstance().font,"?",x+4,y+4,0xffaaaaaa,true);}
}

package dev.sig.tallium.adapter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.item.*;
import java.util.*;
public final class WorldItems {
    private static final Map<String,ItemStackRenderState> cache=new HashMap<>();
    public static void clear(){cache.clear();}
    public static ItemStackRenderState state(ItemStack stack){
        String id=TalliumClient.identity(stack).key();ItemStackRenderState state=cache.get(id);if(state!=null)return state;
        state=new ItemStackRenderState();Minecraft mc=Minecraft.getInstance();mc.getItemModelResolver().updateForTopItem(state,stack,ItemDisplayContext.GUI,mc.level,mc.player,0);
        if(cache.size()<2048)cache.put(id,state);return state;
    }
}

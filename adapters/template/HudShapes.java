package dev.sig.tallium.adapter;
import net.minecraft.client.gui.GuiGraphics;


public final class HudShapes {
    private HudShapes() {}
    public static void panel(GuiGraphics g,int x,int y,int width,int height,int radius,int color){
        int r=Math.max(0,Math.min(radius,Math.min(width,height)/2));
        if(r==0){g.fill(x,y,x+width,y+height,color);return;}
        for(int row=0;row<height;row++){
            double edge=row<r?r-row-.5:row>=height-r?row-(height-r)+.5:0;
            int inset=edge==0?0:(int)Math.ceil(r-Math.sqrt(Math.max(0,r*r-edge*edge)));
            g.fill(x+inset,y+row,x+width-inset,y+row+1,color);
        }
    }
}

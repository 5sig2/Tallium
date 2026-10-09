package dev.sig.tallium.adapter;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.function.Consumer;

final class ConfirmDialog extends ConfigScreen {
    private final Consumer<Boolean> answer;private final Component detail;private boolean answered;
    ConfirmDialog(Consumer<Boolean> answer,Component title,Component detail){super(title);this.answer=answer;this.detail=detail;navigation(currentScreen(),null);}
    private void respond(boolean confirmed){if(!answered){answered=true;answer.accept(confirmed);}}
    @Override protected void init(){
        initNavigation();int w=Math.min(420,width-20),left=(width-w)/2,y=height/2+35;
        addRenderableWidget(FlatButton.builder(Component.literal("Confirm"),b->respond(true)).bounds(left,y,(w-6)/2,18).build());
        addRenderableWidget(FlatButton.builder(Component.literal("Cancel"),b->respond(false)).bounds(left+(w+6)/2,y,(w-6)/2,18).build());
    }
    @Override public void render(GuiGraphics g,int mx,int my,float delta){
        g.fill(0,0,width,height,UiStyle.BACKGROUND);
        int w=Math.min(420,width-20),left=(width-w)/2,top=height/2-65;
        UiStyle.frame(g,left-6,top-8,w+12,126,UiStyle.PANEL,UiStyle.BORDER);
        UiStyle.text(g,font,title.getString(),left,top,w,UiStyle.TEXT);
        int y=top+22;for(var line:font.split(detail,w)){if(y>=height/2+28)break;g.drawString(font,line,left,y,UiStyle.MUTED);y+=11;}
        super.render(g,mx,my,delta);
    }
    @Override protected boolean navigationEnabled(){return false;}
    @Override public void onClose(){respond(false);}
}

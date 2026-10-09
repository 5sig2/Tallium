package dev.sig.tallium.adapter;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.List;


public abstract class ConfigScreen extends Screen {
    private Screen context;
    private String category;
    protected ConfigScreen(Component title){super(title);}
    protected static Screen currentScreen(){var mc=net.minecraft.client.Minecraft.getInstance();return mc.screen;}
    protected final void navigation(Screen context,String category){this.context=context;this.category=category;}
    final TalliumScreen rootEditor(){
        if(this instanceof TalliumScreen editor)return editor;
        return context instanceof ConfigScreen screen?screen.rootEditor():null;
    }
    protected String activeCategory(){
        if(this instanceof TalliumScreen editor)return editor.section;
        return category!=null?category:context instanceof ConfigScreen screen?screen.activeCategory():"Settings";
    }

    protected boolean leaveForNavigation(){return !(context instanceof ConfigScreen screen)||screen.leaveForNavigation();}
    protected boolean navigationEnabled(){return true;}
    protected final void initNavigation(){
        List<String> tabs=List.of("Counters","Groups","Layout","Nametags","Players","Colors","Settings");
        int tabWidth=(width-20)/tabs.size();
        for(int i=0;i<tabs.size();i++){
            String tab=tabs.get(i);
            var button=FlatButton.builder(Component.literal(tab),b->navigate(tab)).bounds(10+i*tabWidth,30,tabWidth-3,18).build().tab(()->activeCategory().equals(tab));
            button.active=navigationEnabled();addRenderableWidget(button);
        }
        var profiles=FlatButton.builder(Component.literal("Manage profiles"),b->{
            if(!leaveForNavigation())return;
            TalliumScreen editor=rootEditor();if(editor==null)editor=new TalliumScreen(context);
            minecraft.setScreen(new ProfilesScreen(editor));
        }).bounds(width-127,7,117,18).build();
        profiles.active=navigationEnabled();addRenderableWidget(profiles);
    }
    private void navigate(String tab){
        if(!leaveForNavigation())return;
        TalliumScreen editor=rootEditor();if(editor==null)editor=new TalliumScreen(context);
        if(editor!=this)minecraft.setScreen(editor);
        editor.openTab(tab);
    }

    @Override public void renderBackground(GuiGraphics g,int x,int y,float delta){}
    @Override public void render(GuiGraphics g,int x,int y,float delta){
        TalliumScreen editor=rootEditor();
        UiStyle.text(g,font,"Tallium - Profile: "+(editor==null?TalliumClient.config.active().name:editor.profile().name),10,12,width-147,UiStyle.TEXT);
        if(!(this instanceof TalliumScreen)&&!(this instanceof LayoutScreen)&&!(this instanceof ColorsScreen)){
            String name=title.getString().replaceFirst("^Tallium[ ·-]+","");
            UiStyle.text(g,font,name.equalsIgnoreCase(activeCategory())?name:activeCategory()+" / "+name,10,62,width-20,UiStyle.TEXT);
        }
        super.render(g,x,y,delta);
    }
}

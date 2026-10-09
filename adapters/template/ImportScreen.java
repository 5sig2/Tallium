package dev.sig.tallium.adapter;
import dev.sig.tallium.config.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
public final class ImportScreen extends ConfigScreen {
    private final Screen parent;private final TalliumScreen editor;private EditBox input;private ShareCode.Export preview;private String error="",code="";private FlatButton importButton;
    public ImportScreen(Screen parent,TalliumScreen editor){super(Component.literal("Tallium · Import Code"));this.parent=parent;this.editor=editor;navigation(parent,"Settings");}
    protected void init(){initNavigation();input=new FlatEditBox(font,10,101,width-20,20,Component.literal("Paste TLM1 code"));input.setMaxLength(ShareCode.MAX_TEXT);input.setValue(code);input.setResponder(v->{code=v;preview=null;error="";importButton.active=false;});addRenderableWidget(input);setInitialFocus(input);
        addRenderableWidget(FlatButton.builder(Component.literal("Validate & Preview"),b->{try{preview=ShareCode.decode(input.getValue());importButton.active=true;error="";}catch(Exception e){preview=null;importButton.active=false;error=java.util.Objects.toString(e.getMessage(),"Invalid settings.");}}).bounds(10,129,150,20).build());
        importButton=FlatButton.builder(Component.literal("Import Inactive Profile"),b->{if(preview==null)return;try{if(editor.working.profiles.size()>=128)throw new IllegalArgumentException("At most 128 profiles.");var p=ShareCode.remap(preview.profile());String base=p.name;int n=2;while(editor.working.profiles.stream().anyMatch(x->x.name.equals(p.name)))p.name=base+" "+n++;
            for(var d:p.definitions){String item=d.selector.item();if(item!=null)d.unavailable=TalliumClient.catalogue.stream().noneMatch(s->TalliumClient.identity(s).item().equals(item));}
            editor.working.profiles.add(p);preview=null;importButton.active=false;error="Imported inactive; Apply saves it. Activate is separate.";}catch(Exception e){error=java.util.Objects.toString(e.getMessage(),"Invalid settings.");}}).bounds(width-190,height-28,180,20).build();importButton.active=preview!=null;addRenderableWidget(importButton);
        addRenderableWidget(FlatButton.builder(Component.literal("Cancel"),b->onClose()).bounds(10,height-28,80,20).build());}
    public void render(GuiGraphics g,int x,int y,float delta){g.fill(0,0,width,height,UiStyle.BACKGROUND);UiStyle.text(g,font,"Paste a TLM1 code, then validate it before importing.",10,84,width-20,UiStyle.MUTED);UiStyle.frame(g,10,163,width-20,Math.max(80,height-219),UiStyle.PANEL,UiStyle.BORDER);UiStyle.text(g,font,"Import preview",20,174,width-40,UiStyle.TEXT);if(preview!=null){g.drawString(font,preview.profile().name+" · "+preview.kind(),20,198,0xffffffff);g.drawString(font,preview.profile().counters.size()+" counters · "+preview.profile().groups.size()+" groups",20,219,0xffaaaaaa);g.drawString(font,"Game context: "+preview.game()+(!TalliumClient.GAME.equals(preview.game())?" · Cross-version: check unavailable selectors":""),20,240,0xffffaa55);
        long missing=preview.profile().definitions.stream().filter(d->d.selector.item()!=null&&TalliumClient.catalogue.stream().noneMatch(s->TalliumClient.identity(s).item().equals(d.selector.item()))).count();g.drawString(font,"Unavailable items: "+missing+" · Preserved as disabled entries",20,261,0xffffaa55);
        }if(!error.isEmpty())g.drawString(font,error.substring(0,Math.min(error.length(),width/6)),10,height-72,0xffffaa55);super.render(g,x,y,delta);}
    public void onClose(){minecraft.setScreen(parent);}
}

package dev.sig.tallium.adapter;
import dev.sig.tallium.config.Config;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;
public final class PlayersScreen extends ConfigScreen {
    private final Screen parent;
    private UUID selected;
    private EditBox search;
    private int page,details;private String query="";
    public PlayersScreen(Screen parent,UUID selected){super(Component.literal("Tallium · Players"));this.parent=parent;this.selected=selected;navigation(parent,"Players");}
    protected void init(){
        initNavigation();search=new FlatEditBox(font,10,82,Math.max(100,width/2-20),20,Component.literal("Search players"));search.setHint(Component.literal("Search players"));search.setMaxLength(80);search.setValue(query);search.setResponder(v->{query=v;page=0;});addRenderableWidget(search);
        addRenderableWidget(FlatButton.builder(Component.literal("Back"),b->onClose()).bounds(10,height-54,60,20).build());
        addRenderableWidget(FlatButton.builder(Component.literal("Previous"),b->{page=Math.max(0,page-1);}).bounds(10,height-28,80,20).build());
        addRenderableWidget(FlatButton.builder(Component.literal("Next"),b->{page++;}).bounds(94,height-28,70,20).build());
        addRenderableWidget(FlatButton.builder(Component.literal(width<400?"Copy":"Copy Summary"),b->{if(selected!=null){String value=String.join("\n",TalliumClient.summary(selected,512));try{minecraft.keyboardHandler.setClipboard(value);}catch(Exception e){minecraft.setScreen(new TextScreen(this,"Copy Summary",value));}}}).bounds(width/2,height-54,Math.max(60,Math.min(120,width/2-88)),20).build());
        addRenderableWidget(FlatButton.builder(Component.literal(width<400?"Reset":"Reset Usage"),b->{if(selected!=null){TalliumClient.STORE.reset(selected::equals,List.of(new dev.sig.tallium.items.Selector(null,null,null,null,null,null,null)));TalliumClient.hint("Selected player's usage reset.");}}).bounds(width/2,height-28,Math.max(60,Math.min(120,width/2-88)),20).build());
        addRenderableWidget(FlatButton.builder(Component.literal("Rows ↑"),b->details=Math.max(0,details-8)).bounds(width-74,height-54,64,20).build());
        addRenderableWidget(FlatButton.builder(Component.literal("Rows ↓"),b->details+=8).bounds(width-74,height-28,64,20).build());
    }
    public void render(GuiGraphics g,int mx,int my,float delta){
        g.fill(0,0,width,height,UiStyle.BACKGROUND);UiStyle.frame(g,10,110,width/2-18,18,UiStyle.ALTERNATE,UiStyle.BORDER);UiStyle.text(g,font,"Player / Last seen",14,115,width/2-26,UiStyle.TEXT);UiStyle.frame(g,width/2,82,width/2-10,height-144,UiStyle.PANEL,UiStyle.BORDER);
        List<TalliumClient.SeenPlayer> players=TalliumClient.roster.values().stream().filter(p->p.name().toLowerCase(Locale.ROOT).contains(search.getValue().toLowerCase(Locale.ROOT))).toList();
        int count=Math.max(1,(height-164)/22);page=Math.min(page,Math.max(0,(players.size()-1)/count));
        for(int n=page*count;n<Math.min(players.size(),(page+1)*count);n++){var p=players.get(n);int yy=131+(n-page*count)*22;g.fill(10,yy,width/2-8,yy+21,p.uuid().equals(selected)?UiStyle.SELECTED:n%2==0?UiStyle.ROW:UiStyle.ALTERNATE);String label=(p.uuid().equals(selected)?"> ":"")+p.name()+(p.present()?" · visible":" · seen "+TalliumClient.lastSeenSeconds(p)+"s ago");g.drawString(font,font.plainSubstrByWidth(label,Math.max(60,width/2-22)),14,134+(n-page*count)*22,0xffffffff);}
        if(selected!=null){List<net.minecraft.util.FormattedCharSequence> lines=new ArrayList<>();for(Component line:TalliumClient.summaryComponents(selected,512))lines.addAll(font.split(line,Math.max(80,width/2-26)));int limit=Math.max(1,(height-166)/12);details=Math.min(details,Math.max(0,lines.size()-limit));for(int n=details;n<Math.min(lines.size(),details+limit);n++)g.drawString(font,lines.get(n),width/2+8,94+(n-details)*12,0xffdddddd);}
        super.render(g,mx,my,delta);
    }
    public boolean mouseClicked(@CLICK_SIGNATURE@){@CLICK_VARS@
        if(button==0&&mouseX>=10&&mouseX<width/2-8&&mouseY>=131&&mouseY<131+Math.max(1,(height-164)/22)*22){List<TalliumClient.SeenPlayer> players=TalliumClient.roster.values().stream().filter(p->p.name().toLowerCase(Locale.ROOT).contains(search.getValue().toLowerCase(Locale.ROOT))).toList();int count=Math.max(1,(height-164)/22);int n=page*count+(int)(mouseY-131)/22;if(n<players.size()){selected=players.get(n).uuid();details=0;return true;}}
        return super.mouseClicked(@CLICK_SUPER@);
    }
    public boolean mouseScrolled(double x,double y,double horizontal,double vertical){if(super.mouseScrolled(x,y,horizontal,vertical))return true;if(vertical==0)return false;if(x<width/2)page=Math.max(0,page+(vertical<0?1:-1));else details=Math.max(0,details+(vertical<0?3:-3));return true;}
    public void onClose(){minecraft.setScreen(parent);}
}

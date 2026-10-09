package dev.sig.tallium.adapter;
import dev.sig.tallium.config.*;
import dev.sig.tallium.display.*;
import dev.sig.tallium.items.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import java.util.*;
public final class ItemPickerScreen extends ConfigScreen {
    private final TalliumScreen editor;private EditBox search;private int page;private String query="",error="";
    public ItemPickerScreen(TalliumScreen editor){super(Component.literal("Tallium · Add Item"));this.editor=editor;navigation(editor,"Counters");}
    protected void init(){initNavigation();search=new FlatEditBox(font,10,82,width-20,20,Component.literal("Search name, ID, category"));search.setHint(Component.literal("Search name, ID or category"));search.setValue(query);search.setResponder(v->{query=v;page=0;});addRenderableWidget(search);
        addRenderableWidget(FlatButton.builder(Component.literal("Back"),b->onClose()).bounds(10,height-28,60,20).build());
        addRenderableWidget(FlatButton.builder(Component.literal("Previous"),b->page=Math.max(0,page-1)).bounds(74,height-28,80,20).build());
        addRenderableWidget(FlatButton.builder(Component.literal("Next"),b->page++).bounds(158,height-28,60,20).build());
        int x=10;for(String category:List.of("food","potions","tipped_arrows")){final String tag=category;addRenderableWidget(FlatButton.builder(Component.literal(categoryLabel(category)),b->add(null,tag)).bounds(x,106,Math.max(70,(width-20)/3-2),20).build());x+=(width-20)/3;}}
    private static String categoryLabel(String category){return switch(category){case "food"->"All Food";case "potions"->"All Potions";default->"All Tipped Arrows";};}
    private List<ItemStack> results(){String q=search.getValue().toLowerCase(Locale.ROOT);return TalliumClient.catalogue.stream().filter(s->{ItemIdentity id=TalliumClient.identity(s);return s.getHoverName().getString().toLowerCase(Locale.ROOT).contains(q)||id.item().contains(q)||id.categories().stream().anyMatch(t->t.contains(q))||id.potion().contains(q);}).toList();}
    public void render(GuiGraphics g,int x,int y,float delta){g.fill(0,0,width,height,UiStyle.BACKGROUND);List<ItemStack> list=results();int count=Math.max(1,(height-170)/36);page=Math.min(page,Math.max(0,(list.size()-1)/count));
        for(int n=page*count;n<Math.min(list.size(),(page+1)*count);n++){ItemStack stack=list.get(n);ItemIdentity identity=TalliumClient.identity(stack);Config.Definition d=Config.starter(identity.item().substring(identity.item().indexOf(':')+1));if(identity.categories().contains("food")||stack.getUseAnimation().name().equals("EAT")||stack.getUseAnimation().name().equals("DRINK"))d.family=stack.is(net.minecraft.world.item.Items.POTION)?"potions":"food";var cap=TalliumClient.CAPABILITIES.forFamily(d.family);int yy=132+(n-page*count)*36;g.fill(10,yy-2,width-10,yy+32,n%2==0?UiStyle.ROW:UiStyle.ALTERNATE);if(x>=10&&x<width-10&&y>=yy-2&&y<yy+32)g.fill(10,yy-2,width-10,yy+32,UiStyle.SELECTED);g.renderItem(stack,12,yy);g.drawString(font,font.plainSubstrByWidth(TalliumClient.variantLabel(stack)+" · "+identity.item(),width-42),32,yy,0xffffffff);UiStyle.text(g,font,"Remaining: You · Used: "+UiText.value(cap.local())+" / Others: "+UiText.value(cap.remote()),32,yy+12,width-42,UiStyle.MUTED);}
        super.render(g,x,y,delta);if(!error.isEmpty())UiStyle.text(g,font,error,10,height-43,width-20,UiStyle.ERROR);}
    public boolean mouseClicked(@CLICK_SIGNATURE@){@CLICK_VARS@
        if(button==0&&mouseX>=10&&mouseX<width-10&&mouseY>=132&&mouseY<132+Math.max(1,(height-170)/36)*36){List<ItemStack> list=results();int count=Math.max(1,(height-170)/36);int n=page*count+(int)(mouseY-132)/36;if(n<list.size()){add(list.get(n),null);return true;}}return super.mouseClicked(@CLICK_SUPER@);}
    private void add(ItemStack stack,String category){if(editor.profile().counters.size()>=512||editor.profile().definitions.size()>=512){error="At most 512 counters and item definitions.";return;}
        Config.Definition d;if(stack==null){d=new Config.Definition();d.label=categoryLabel(category);d.family=category.equals("food")?"food":category.equals("potions")?"potions":"arrows";d.selector=new Selector(null,category,null,null,null,null,null);}
        else{ItemIdentity identity=TalliumClient.identity(stack);String raw=identity.item().substring(identity.item().indexOf(':')+1);d=Config.starter(raw);if(identity.categories().contains("food")||stack.getUseAnimation().name().equals("EAT")||stack.getUseAnimation().name().equals("DRINK"))d.family=stack.is(net.minecraft.world.item.Items.POTION)?"potions":"food";String label=TalliumClient.variantLabel(stack);d.label=label.substring(0,Math.min(160,label.length()));d.selector=new Selector(identity.item(),null,identity.potion().isBlank()?null:identity.potion(),null,null,null,identity.exactComponents().isEmpty()?null:identity.exactComponents());}
        Config.Counter c=new Config.Counter();c.definition=d.id;c.used=ColorRules.used(d.family);c.remaining=ColorRules.remaining(d.family);if(d.family.equals("other")){c.used.enabled=false;c.remaining.enabled=false;}
        editor.profile().definitions.add(d);editor.profile().counters.add(c);editor.profile().nametagOrder.add(d.id);editor.selectCounter(c.id);minecraft.setScreen(editor);editor.refresh();}
    public boolean mouseScrolled(double x,double y,double horizontal,double vertical){if(super.mouseScrolled(x,y,horizontal,vertical))return true;if(vertical==0)return false;page=Math.max(0,page+(vertical<0?1:-1));return true;}
    public void onClose(){minecraft.setScreen(editor);}
}

package dev.sig.tallium.adapter;

import dev.sig.tallium.config.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;
import dev.sig.tallium.display.Snapping;


public final class LayoutScreen extends ConfigScreen {
    private boolean rebuildPending;
    private final TalliumScreen editor;private final Config.Profile working;
    private static final String POPUP="@popup";
    private String selected,error="";private boolean dragging,hideControls,snapshot=true;private double dragX,dragY;
    private int canvasX=0,canvasY=0,canvasWidth,canvasHeight,inspectorX,inspectorWidth,inspectorOffset,visibleRows,row;
    private final List<Runnable> rows=new ArrayList<>();
    private final List<Caption> captions=new ArrayList<>();
    private final Map<String,String> invalid=new HashMap<>();
    private record Caption(String text,int y) {}
    public LayoutScreen(TalliumScreen editor){super(Component.literal("Tallium - Layout"));this.editor=editor;navigation(editor,"Layout");working=ConfigStore.copy(editor.profile(),Config.Profile.class);selected=groups().isEmpty()?null:groups().getFirst().id;}
    private void refresh(){rebuildPending=true;}
    private boolean ready(){if(!invalid.isEmpty()){error="Correct the highlighted fields first.";return false;}return true;}
    private FlatButton button(String label,int x,int y,int w,Runnable action){var b=FlatButton.builder(Component.literal(label),pressed->{try{action.run();}catch(Exception e){error=Objects.toString(e.getMessage(),"Invalid layout.");}}).bounds(x,y,w,18).build();addRenderableWidget(b);return b;}
    @Override protected void init(){
        captions.clear();rows.clear();inspectorWidth=width>=460?Math.min(225,width/3):0;
        canvasWidth=width;canvasHeight=height;inspectorX=POPUP.equals(selected)&&working.popup.right?10:width-inspectorWidth-10;
        initNavigation();
        var group=working.group(selected);
        button(POPUP.equals(selected)?"Group: Player popup":group==null?"Select display":"Group: "+group.name,10,57,Math.max(95,width-inspectorWidth-116),()->{if(!ready())return;var ids=new ArrayList<>(groups().stream().map(g->g.id).toList());ids.add(POPUP);selected=ids.get((ids.indexOf(selected)+1)%ids.size());inspectorOffset=0;refresh();});
        button("Snap: "+(working.snap.enabled?"On":"Off"),width-inspectorWidth-100,57,88,()->{working.snap.enabled=!working.snap.enabled;refresh();});
        button("Reset selected",10,height-50,108,()->{if(group!=null)group.placement=new Config.Placement();else if(POPUP.equals(selected))resetPopup();invalid.clear();refresh();});
        button("Hide controls (H)",width<700?10:Math.max(240,width-390),height-(width<700?74:50),130,()->hideControls=true);
        button("HUD: "+(snapshot?"Snapshot":"Live"),width-150,height-(width<700?74:50),140,()->{snapshot=!snapshot;refresh();});
        button("Reset layout",124,height-50,100,()->minecraft.setScreen(new ConfirmDialog(ok->{if(ok){working.groups.forEach(g->g.placement=new Config.Placement());resetPopup();invalid.clear();}minecraft.setScreen(this);},Component.literal("Reset all HUD positions?"),Component.literal("The staged positions change. Counter settings and usage remain."))));
        if(inspectorWidth==0)button("Details",width-74,height-98,64,()->{if(!ready())return;if(group!=null)minecraft.setScreen(new PlacementScreen(this,group));else if(POPUP.equals(selected))minecraft.setScreen(new PopupPlacementScreen(this,working));});
        button("Discard",width-176,height-26,68,this::onClose);
        button("Apply layout",width-102,height-26,92,()->finish("Counters")).primary();
        if(inspectorWidth>0){if(group!=null)buildInspector(group);else if(POPUP.equals(selected))buildPopupInspector();}
    }
    private void resetPopup(){working.popup.x=10;working.popup.y=34;working.popup.scale=1;working.popup.right=true;working.popup.locked=false;}
    @Override protected boolean leaveForNavigation(){if(!ready())return false;try{Config.validateProfile(working,new HashSet<>());editor.working.profiles.set(editor.working.profiles.indexOf(editor.profile()),working);return true;}catch(Exception e){error=Objects.toString(e.getMessage(),"Invalid layout.");return false;}}
    private void finish(String tab){if(!ready())return;Config.validateProfile(working,new HashSet<>());editor.working.profiles.set(editor.working.profiles.indexOf(editor.profile()),working);minecraft.setScreen(editor);editor.openTab(tab);}
    private void field(String name,String value,java.util.function.Consumer<String> changed){rows.add(()->{
        captions.add(new Caption(name,row+5));int x=inspectorX+inspectorWidth/2,w=inspectorWidth/2-8;
        var box=new FlatEditBox(font,x,row,w,18,Component.literal(name));box.setMaxLength(12);box.setValue(invalid.getOrDefault(name,value));box.invalid(invalid.containsKey(name));box.setResponder(v->{try{changed.accept(v);invalid.remove(name);box.invalid(false);error="";}catch(Exception e){invalid.put(name,v);box.invalid(true);error=Objects.toString(e.getMessage(),"Invalid layout.");}});addRenderableWidget(box);
    });}
    private void choice(String name,String value,Runnable action){rows.add(()->{captions.add(new Caption(name,row+5));button(value,inspectorX+inspectorWidth/2,row,inspectorWidth/2-8,()->{if(ready()){action.run();refresh();}});});}
    private void toggle(String name,java.util.function.BooleanSupplier value,Runnable action){rows.add(()->{captions.add(new Caption(name,row+5));button(name,inspectorX+inspectorWidth-30,row,24,()->{action.run();refresh();}).check(value);});}
    private static int integer(String text,int min,int max){int n=Integer.parseInt(text);if(n<min||n>max)throw new IllegalArgumentException("Use "+min+" to "+max+".");return n;}
    private void buildInspector(Config.Group g){
        choice("Mode",UiText.value(g.mode),()->GroupScreen.nextMode(g));
        choice("Anchor",UiText.value(g.placement.anchor),()->g.placement.anchor=Config.Anchor.values()[(g.placement.anchor.ordinal()+1)%3]);
        field("X",Integer.toString(g.placement.x),v->g.placement.x=integer(v,-32768,32768));field("Y",Integer.toString(g.placement.y),v->g.placement.y=integer(v,-32768,32768));
        field("Scale",Double.toString(g.placement.scale),v->{double n=Double.parseDouble(v);if(!Double.isFinite(n)||n<.25||n>4)throw new IllegalArgumentException("Scale: 0.25-4.");g.placement.scale=n;});
        field("Spacing",Integer.toString(g.appearance.spacing),v->g.appearance.spacing=integer(v,0,16));
        toggle("Background",()->g.appearance.background,()->g.appearance.background=!g.appearance.background);
        field("Corner radius",Integer.toString(g.appearance.radius),v->g.appearance.radius=integer(v,0,24));
        choice("Number",GroupScreen.displayName(g.appearance.display),()->GroupScreen.nextDisplay(working,g));
        toggle("On icon",()->g.appearance.overlayNumber,()->g.appearance.overlayNumber=!g.appearance.overlayNumber);
        toggle("Labels",()->g.appearance.labels,()->g.appearance.labels=!g.appearance.labels);
        toggle("Locked",()->g.placement.locked,()->g.placement.locked=!g.placement.locked);
        if(g.mode==Config.Mode.GRID)field("Columns",Integer.toString(g.columns),v->g.columns=integer(v,1,16));
        if(g.mode==Config.Mode.CYCLE||g.appearance.display==Config.Display.CYCLE)field("Interval",Integer.toString(g.interval),v->g.interval=integer(v,1,60));
        field("Z order",Integer.toString(g.placement.z),v->g.placement.z=integer(v,0,128));
        snapRows();showRows();
    }
    private void snapRows(){
        toggle("Snap horizontal",()->working.snap.horizontal,()->working.snap.horizontal=!working.snap.horizontal);
        toggle("Snap vertical",()->working.snap.vertical,()->working.snap.vertical=!working.snap.vertical);
        toggle("Snap left",()->working.snap.left,()->working.snap.left=!working.snap.left);
        toggle("Snap right",()->working.snap.right,()->working.snap.right=!working.snap.right);
        toggle("Snap top",()->working.snap.top,()->working.snap.top=!working.snap.top);
        toggle("Snap bottom",()->working.snap.bottom,()->working.snap.bottom=!working.snap.bottom);
    }
    private void buildPopupInspector(){var p=working.popup;
        choice("Corner",p.right?"Top right":"Top left",()->p.right=!p.right);
        field("X",Integer.toString(p.x),v->p.x=integer(v,-32768,32768));field("Y",Integer.toString(p.y),v->p.y=integer(v,-32768,32768));
        field("Scale",Double.toString(p.scale),v->{double n=Double.parseDouble(v);if(!Double.isFinite(n)||n<.5||n>2)throw new IllegalArgumentException("Scale: 0.5–2.");p.scale=n;});
        field("Duration",Integer.toString(p.seconds),v->p.seconds=integer(v,1,60));
        field("Corner radius",Integer.toString(p.radius),v->p.radius=integer(v,0,24));
        toggle("Background",()->p.background,()->p.background=!p.background);
        toggle("Locked",()->p.locked,()->p.locked=!p.locked);
        snapRows();showRows();
    }
    private void showRows(){
        visibleRows=Math.max(1,(height-165)/21);inspectorOffset=Math.min(inspectorOffset,Math.max(0,rows.size()-visibleRows));
        for(int n=inspectorOffset;n<Math.min(rows.size(),inspectorOffset+visibleRows);n++){row=108+(n-inspectorOffset)*21;rows.get(n).run();}
    }
    private List<Config.Group> groups(){return working.groups.stream().filter(g->g.enabled&&g.surface==Config.Surface.HUD).sorted(Comparator.comparingInt(g->g.placement.z)).toList();}
    private int x(Config.Group g){return g.placement.anchor==Config.Anchor.SCREEN?g.placement.x:canvasWidth/2+g.placement.x;}
    private int y(Config.Group g){return TalliumClient.anchorY(g,canvasHeight)+g.placement.y;}
    private TalliumClient.GroupBounds bounds(Config.Group g){return TalliumClient.fittedBounds(working,g,minecraft.level==null,Math.max(1,canvasWidth-8),Math.max(1,canvasHeight-8));}
    private int inset(Config.Group g){return (int)Math.ceil(g.appearance.padding*bounds(g).scale());}
    private int px(Config.Group g){return Math.max(4+inset(g),Math.min(canvasWidth-4-bounds(g).width()+inset(g),x(g)-bounds(g).width()/2+inset(g)));}
    private int py(Config.Group g){return Math.max(4+inset(g),Math.min(canvasHeight-4-bounds(g).height()+inset(g),y(g)-bounds(g).height()/2+inset(g)));}
    private void clamp(Config.Group g){g.placement.x=px(g)+bounds(g).width()/2-inset(g)-(g.placement.anchor==Config.Anchor.SCREEN?0:canvasWidth/2);g.placement.y=py(g)+bounds(g).height()/2-inset(g)-TalliumClient.anchorY(g,canvasHeight);}
    @Override public void render(GuiGraphics graphics,int mx,int my,float delta){
        if(rebuildPending){rebuildPending=false;UiStyle.rebuild(this,()->{clearWidgets();init();});}
        if(snapshot&&minecraft.level!=null)HudReference.draw(graphics,width,height);
        if(minecraft.level==null)graphics.fill(0,0,width,height,UiStyle.BACKGROUND);
        graphics.enableScissor(canvasX+1,canvasY+1,canvasX+canvasWidth-1,canvasY+canvasHeight-1);
        for(Config.Group g:groups()){var b=bounds(g);int x=canvasX+px(g),y=canvasY+py(g),inset=inset(g);
            if(g.id.equals(selected))UiStyle.frame(graphics,x-inset,y-inset,b.width(),b.height(),UiStyle.SELECTED,UiStyle.ACCENT);
            TalliumClient.previewGroup(graphics,working,g,x,y,Math.max(1,canvasWidth-8),Math.max(1,canvasHeight-8));
            if(g.id.equals(selected)){int cx=canvasX+x(g),cy=canvasY+y(g);graphics.fill(cx-3,cy,cx+4,cy+1,UiStyle.ACCENT);graphics.fill(cx,cy-3,cx+1,cy+4,UiStyle.ACCENT);}
        }
        if(POPUP.equals(selected)){
            PlayerPopup.render(graphics,working,true);
            UiStyle.frame(graphics,PlayerPopup.x(working,width)-1,PlayerPopup.y(working,height)-1,(int)Math.ceil(PlayerPopup.width(working,width)*working.popup.scale)+2,(int)Math.ceil(PlayerPopup.height(working,height)*working.popup.scale)+2,0,UiStyle.ACCENT);
        }
        graphics.disableScissor();
        if(hideControls){graphics.drawString(font,"H: show controls · Drag to place",10,height-14,UiStyle.TEXT,true);return;}
        graphics.fill(0,0,width,78,UiStyle.BACKGROUND|0xff000000);
        graphics.fill(0,height-56,234,height,0xd0101517);
        graphics.fill(width-190,height-56,width,height,0xd0101517);
        if(inspectorWidth>0){UiStyle.frame(graphics,inspectorX,80,inspectorWidth,height-136,UiStyle.PANEL|0xff000000,UiStyle.BORDER);var g=working.group(selected);UiStyle.text(graphics,font,POPUP.equals(selected)?"Player popup":g==null?"Select a HUD group":g.name,inspectorX+8,90,inspectorWidth-16,UiStyle.TEXT);UiStyle.scrollbar(graphics,inspectorX+inspectorWidth-4,108,visibleRows*21,rows.size(),visibleRows,inspectorOffset);}
        super.render(graphics,mx,my,delta);
        for(Caption c:captions)UiStyle.text(graphics,font,c.text,inspectorX+8,c.y,inspectorWidth/2-14,UiStyle.TEXT);
        UiStyle.text(graphics,font,error.isEmpty()?minecraft.level==null?"Join a world to see your HUD.":"Drag to place. Arrow keys nudge.":error,10,height-21,220,error.isEmpty()?UiStyle.MUTED:UiStyle.ERROR);
    }
    @Override public boolean mouseClicked(@CLICK_SIGNATURE@){@CLICK_VARS@
        dragging=false;
        if(!hideControls&&super.mouseClicked(@CLICK_SUPER@))return true;
        if(button==0&&mouseX>=canvasX&&mouseX<canvasX+canvasWidth&&(hideControls||inspectorWidth==0||mouseX<inspectorX||mouseX>inspectorX+inspectorWidth)&&(hideControls||mouseY>=80)&&mouseY<height-8){if(!ready())return true;
            if(POPUP.equals(selected)){int x=PlayerPopup.x(working,width),y=PlayerPopup.y(working,height),w=(int)Math.ceil(PlayerPopup.width(working,width)*working.popup.scale),h=(int)Math.ceil(PlayerPopup.height(working,height)*working.popup.scale);if(mouseX>=x&&mouseX<x+w&&mouseY>=y&&mouseY<y+h){clearFocus();dragging=!working.popup.locked;dragX=x+w/2.0;dragY=y+h/2.0;return true;}}
            List<Config.Group> ordered=new ArrayList<>(groups());Collections.reverse(ordered);for(var g:ordered){var b=bounds(g);int x=canvasX+px(g)-inset(g),y=canvasY+py(g)-inset(g);if(mouseX>=x&&mouseX<x+b.width()&&mouseY>=y&&mouseY<y+b.height()){clearFocus();selected=g.id;dragging=!g.placement.locked;clamp(g);dragX=x(g);dragY=y(g);inspectorOffset=0;refresh();return true;}}return true;}
        return false;
    }
    @Override public boolean mouseDragged(@DRAG_SIGNATURE@){@DRAG_VARS@
        Config.Group g=working.group(selected);if(dragging&&button==0){dragX+=dx;dragY+=dy;var s=working.snap;
            if(POPUP.equals(selected)){int w=(int)Math.ceil(PlayerPopup.width(working,width)*working.popup.scale),h=(int)Math.ceil(PlayerPopup.height(working,height)*working.popup.scale);int x=Snapping.axis(dragX,w,width,s.enabled&&s.horizontal,s.left,s.right)-w/2,y=Snapping.axis(dragY,h,height,s.enabled&&s.vertical,s.top,s.bottom)-h/2;x=Math.max(0,Math.min(width-w,x));y=Math.max(0,Math.min(height-h,y));working.popup.x=working.popup.right?width-w-x:x;working.popup.y=y;refresh();return true;}
            if(g!=null){int px=Snapping.axis(dragX,bounds(g).width(),width,s.enabled&&s.horizontal,s.left,s.right),py=Snapping.axis(dragY,bounds(g).height(),height,s.enabled&&s.vertical,s.top,s.bottom);g.placement.x=px-(g.placement.anchor==Config.Anchor.SCREEN?0:canvasWidth/2);g.placement.y=py-TalliumClient.anchorY(g,canvasHeight);clamp(g);refresh();return true;}}
        return hideControls?false:super.mouseDragged(@DRAG_SUPER@);
    }
    @Override public boolean mouseReleased(@RELEASE_SIGNATURE@){@RELEASE_VARS@ if(button==0&&dragging){dragging=false;return true;}return super.mouseReleased(@RELEASE_SUPER@);}
    @Override public boolean keyPressed(@KEY_SIGNATURE@){@KEY_VARS@
        if(getFocused() instanceof net.minecraft.client.gui.components.EditBox)return super.keyPressed(@KEY_SUPER@);
        if(key==72||key==256&&hideControls){hideControls=!hideControls;return true;}
        if(POPUP.equals(selected)&&!working.popup.locked&&ready()){switch(key){case 262->working.popup.x+=working.popup.right?-1:1;case 263->working.popup.x+=working.popup.right?1:-1;case 264->working.popup.y++;case 265->working.popup.y--;default->{return super.keyPressed(@KEY_SUPER@);}}refresh();return true;}
        var g=working.group(selected);if(g!=null&&!g.placement.locked&&ready()){switch(key){case 262->g.placement.x++;case 263->g.placement.x--;case 264->g.placement.y++;case 265->g.placement.y--;default->{return super.keyPressed(@KEY_SUPER@);}}clamp(g);refresh();return true;}return super.keyPressed(@KEY_SUPER@);
    }
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){if(super.mouseScrolled(x,y,horizontal,vertical))return true;if(inspectorWidth>0&&x>=inspectorX&&vertical!=0){clearFocus();inspectorOffset=Math.max(0,inspectorOffset+(vertical<0?1:-1));refresh();return true;}return false;}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void onClose(){minecraft.setScreen(editor);}
}

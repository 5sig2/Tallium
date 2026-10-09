package dev.sig.tallium.adapter;
import dev.sig.tallium.config.Config;
public final class PopupPlacementScreen extends FormScreen {
    private final Config.Profile profile;
    public PopupPlacementScreen(LayoutScreen parent,Config.Profile profile){super(parent,"Tallium - Player popup");this.profile=profile;}
    protected void build(){var p=profile.popup;var s=profile.snap;
        field("X",Integer.toString(p.x),v->p.x=integer(v,-32768,32768));field("Y",Integer.toString(p.y),v->p.y=integer(v,-32768,32768));
        field("Popup Scale",Double.toString(p.scale),v->{double n=Double.parseDouble(v);if(!Double.isFinite(n)||n<.5||n>2)throw new IllegalArgumentException("Scale: 0.5–2.");p.scale=n;});
        field("Popup Duration (seconds)",Integer.toString(p.seconds),v->p.seconds=integer(v,1,60));
        action("Corner: "+(p.right?"Top right":"Top left"),()->{p.right=!p.right;refresh();});
        action("Locked: "+p.locked,()->{p.locked=!p.locked;refresh();});
        action("Background: "+p.background,()->{p.background=!p.background;refresh();});
        field("Corner Radius",Integer.toString(p.radius),v->p.radius=integer(v,0,24));
        action("Snap Horizontal: "+s.horizontal,()->{s.horizontal=!s.horizontal;refresh();});action("Snap Vertical: "+s.vertical,()->{s.vertical=!s.vertical;refresh();});
        action("Snap Left: "+s.left,()->{s.left=!s.left;refresh();});action("Snap Right: "+s.right,()->{s.right=!s.right;refresh();});action("Snap Top: "+s.top,()->{s.top=!s.top;refresh();});action("Snap Bottom: "+s.bottom,()->{s.bottom=!s.bottom;refresh();});
    }
    private static int integer(String text,int min,int max){int n=Integer.parseInt(text);if(n<min||n>max)throw new IllegalArgumentException("Use "+min+" to "+max+".");return n;}
}

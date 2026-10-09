package dev.sig.tallium.adapter;
import dev.sig.tallium.config.Config;
public final class PlacementScreen extends FormScreen {
    private final Config.Group group;
    public PlacementScreen(LayoutScreen parent,Config.Group group){super(parent,"Tallium · Position & Appearance");this.group=group;}
    protected void build(){var p=group.placement;
        field("X",Integer.toString(p.x),v->p.x=Integer.parseInt(v));field("Y",Integer.toString(p.y),v->p.y=Integer.parseInt(v));
        field("Scale (0.25–4)",Double.toString(p.scale),v->{double n=Double.parseDouble(v);if(!Double.isFinite(n)||n<.25||n>4)throw new IllegalArgumentException("Scale: 0.25–4.");p.scale=n;});
        action("Anchor: "+p.anchor,()->{p.anchor=Config.Anchor.values()[(p.anchor.ordinal()+1)%3];refresh();});
        action("Locked: "+p.locked,()->{p.locked=!p.locked;refresh();});action("Bring to Front",()->p.z=128);action("Send to Back",()->p.z=0);
    }
}

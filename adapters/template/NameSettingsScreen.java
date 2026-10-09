package dev.sig.tallium.adapter;
public final class NameSettingsScreen extends FormScreen {
    private final TalliumScreen editor;
    public NameSettingsScreen(TalliumScreen editor){super(editor,"Tallium · Name Appearance");this.editor=editor;}
    protected void build(){var p=editor.profile();
        action("Unmatched Labels: "+(p.nametagFallback?"Above player":"Hide counters"),()->{p.nametagFallback=!p.nametagFallback;refresh();});
        slider("Nametag Distance",p.nametagDistance,1,1024,v->p.nametagDistance=v);
        slider("Nametag Scale (%)",(int)(p.nametagScale*100),25,400,v->p.nametagScale=v/100d);
        slider("Nametag Horizontal Offset",p.nametagX,-512,512,v->p.nametagX=v);
        slider("Nametag Vertical Offset",p.nametagY,-512,512,v->p.nametagY=v);
        action("Nametag Colors: "+p.nametagColors,()->{p.nametagColors=!p.nametagColors;refresh();});
        slider("Nametag Entry Cap",p.nametagCap,1,30,v->p.nametagCap=v);
        slider("Nametag Spacing",p.nametagSpacing,0,8,v->p.nametagSpacing=v);
        action("Overflow: "+(p.cycleOverflow?"Cycle":"+N indicator"),()->{p.cycleOverflow=!p.cycleOverflow;refresh();});
        if(p.cycleOverflow)slider("Overflow Interval (seconds)",p.overflowInterval,1,60,v->p.overflowInterval=v);
        slider("Player List Entry Cap",p.playerListCap,1,30,v->p.playerListCap=v);
        slider("Player List Spacing",p.playerListSpacing,0,8,v->p.playerListSpacing=v);
        action("Player List Icons: "+p.playerListIconMode,()->{p.playerListIconMode=p.playerListIconMode.equals("DEFAULT")?"RESOURCE_PACK":"DEFAULT";refresh();});
        action("Player List Colors: "+p.playerListColors,()->{p.playerListColors=!p.playerListColors;refresh();});
        action("Player List Hide Zero: "+p.playerListHideZero,()->{p.playerListHideZero=!p.playerListHideZero;refresh();});
        action("Player List Separator: "+p.playerListSeparator,()->{p.playerListSeparator=!p.playerListSeparator;refresh();});
    }
}

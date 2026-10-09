package dev.sig.tallium.adapter;
public final class GlobalSettingsScreen extends FormScreen {
    private final TalliumScreen editor;
    public GlobalSettingsScreen(TalliumScreen editor){super(editor,"Tallium · Inventory, Inspection & XP");this.editor=editor;}
    protected void build(){var c=editor.working;var p=editor.profile();
        action("Inventory Scope: "+c.inventoryScope,()->{c.inventoryScope=c.inventoryScope.equals("HOTBAR")?"MAIN_OFFHAND":"HOTBAR";refresh();});
        action("Hold Reset All for 2 Seconds: "+c.holdResetAll,()->{c.holdResetAll=!c.holdResetAll;refresh();});
        action("Confirm Reset All: "+c.confirmResetAll,()->{c.confirmResetAll=!c.confirmResetAll;refresh();});
        action("Restore All Settings",()->minecraft.setScreen(new ConfirmDialog(ok->{if(ok){editor.working=dev.sig.tallium.config.Config.defaults();}minecraft.setScreen(editor);editor.refresh();},net.minecraft.network.chat.Component.literal("Restore all Tallium settings?"),net.minecraft.network.chat.Component.literal("Usage and native key assignments are preserved. Apply saves the restored settings."))));
        action("Inspect to Render Distance: "+c.inspectionRenderDistance,()->{c.inspectionRenderDistance=!c.inspectionRenderDistance;refresh();});
        field("Inspection Range",Integer.toString(c.inspectionRange),v->{int n=Integer.parseInt(v);if(n<1||n>1024)throw new IllegalArgumentException("Range: 1–1024 blocks. Used when render-distance targeting is off.");c.inspectionRange=n;});
        field("Aim Tolerance (degrees)",Double.toString(c.inspectionAngle),v->{double n=Double.parseDouble(v);if(!Double.isFinite(n)||n<1||n>30)throw new IllegalArgumentException("Angle: 1–30 degrees.");c.inspectionAngle=n;});
        action("No Target Sound: "+c.inspectionMissSound,()->{c.inspectionMissSound=!c.inspectionMissSound;refresh();});
        action("Player Popup: "+p.popup.enabled,()->{p.popup.enabled=!p.popup.enabled;refresh();});
        field("Popup Duration (seconds)",Integer.toString(p.popup.seconds),v->{int n=Integer.parseInt(v);if(n<1||n>60)throw new IllegalArgumentException("Duration: 1–60 seconds.");p.popup.seconds=n;});
        action("Popup Corner: "+(p.popup.right?"Top right":"Top left"),()->{p.popup.right=!p.popup.right;refresh();});
        field("Popup Scale",Double.toString(p.popup.scale),v->{double n=Double.parseDouble(v);if(!Double.isFinite(n)||n<.5||n>2)throw new IllegalArgumentException("Scale: 0.5–2.");p.popup.scale=n;});
        action("Popup Background: "+p.popup.background,()->{p.popup.background=!p.popup.background;refresh();});
        field("Popup Corner Radius",Integer.toString(p.popup.radius),v->{int n=Integer.parseInt(v);if(n<0||n>24)throw new IllegalArgumentException("Radius: 0–24.");p.popup.radius=n;});
        field("Popup Background Opacity",Double.toString(p.popup.opacity),v->{double n=Double.parseDouble(v);if(!Double.isFinite(n)||n<.05||n>1)throw new IllegalArgumentException("Opacity: 0.05–1.");p.popup.opacity=n;});
        field("Summary Rows",Integer.toString(c.summaryRows),v->{int n=Integer.parseInt(v);if(n<4||n>30)throw new IllegalArgumentException("Rows: 4–30.");c.summaryRows=n;});
        action("Colored XP Bar: "+p.coloredXp,()->{p.coloredXp=!p.coloredXp;refresh();});
        action("Default HUD Icons: "+p.hudIconMode,()->{p.hudIconMode=p.hudIconMode.equals("DEFAULT")?"RESOURCE_PACK":"DEFAULT";refresh();});
        action("Always Show Colored Bar: "+p.alwaysColoredXp,()->{p.alwaysColoredXp=!p.alwaysColoredXp;refresh();});
        action("Clear XP Source",()->{p.xpSource=null;refresh();});
        for(var counter:p.counters)action("XP Source: "+p.definition(counter.definition).label+" · "+counter.metric,()->{p.xpSource=counter.id;refresh();});
        for(var group:p.groups)if(group.mode==dev.sig.tallium.config.Config.Mode.CYCLE)action("XP Source: "+group.name+" Cycle",()->{p.xpSource=group.id;refresh();});
    }
}

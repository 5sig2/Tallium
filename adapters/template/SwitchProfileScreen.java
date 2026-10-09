package dev.sig.tallium.adapter;
import dev.sig.tallium.config.*;
public final class SwitchProfileScreen extends FormScreen {
    private final TalliumScreen editor;private final String id;
    public SwitchProfileScreen(ProfilesScreen parent,TalliumScreen editor,String id){super(parent,"Unsaved changes · Apply / Discard / Cancel");this.editor=editor;this.id=id;}
    protected void build(){action("Apply and Activate",()->{String previous=editor.working.activeProfile;try{editor.working.activeProfile=id;TalliumClient.apply(editor.working);editor.working=ConfigStore.copy(TalliumClient.config,Config.class);minecraft.setScreen(editor);}catch(Exception e){editor.working.activeProfile=previous;error=java.util.Objects.toString(e.getMessage(),"Invalid settings.");}});
        action("Discard and Activate",()->{try{Config base=ConfigStore.copy(TalliumClient.config,Config.class);if(base.profiles.stream().noneMatch(p->p.id.equals(id))){error="Save the new profile before discarding changes.";return;}base.activeProfile=id;TalliumClient.apply(base);editor.working=ConfigStore.copy(base,Config.class);minecraft.setScreen(editor);}catch(Exception e){error=java.util.Objects.toString(e.getMessage(),"Invalid settings.");}});action("Cancel",this::onClose);}
}

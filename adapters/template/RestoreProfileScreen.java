package dev.sig.tallium.adapter;
import dev.sig.tallium.config.Config;
public final class RestoreProfileScreen extends FormScreen {
    private final TalliumScreen editor;
    public RestoreProfileScreen(ProfilesScreen parent,TalliumScreen editor){super(parent,"Keep one profile · Restore defaults instead?");this.editor=editor;}
    protected void build(){action("Restore Profile Defaults",()->{Config defaults=Config.defaults();editor.working.profiles=defaults.profiles;editor.working.activeProfile=defaults.activeProfile;minecraft.setScreen(editor);});action("Cancel",this::onClose);}
}

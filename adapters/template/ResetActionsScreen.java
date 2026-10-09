package dev.sig.tallium.adapter;
import dev.sig.tallium.config.*;
import java.util.*;
public final class ResetActionsScreen extends FormScreen {
    private final TalliumScreen editor;
    public ResetActionsScreen(TalliumScreen editor){super(editor,"Tallium · Reset Actions · Bind through Controls");this.editor=editor;}
    static String problem(Config config,Config.ResetAction a){if(a.scope==Config.Scope.UNRESOLVED||a.scope==Config.Scope.SELECTED&&a.players.isEmpty())return "Choose players";if(a.all)return "";var p=config.profiles.stream().filter(x->x.id.equals(a.profileId)).findFirst().orElse(null);if(p==null)return "Missing profile";if(a.groupId!=null){var g=p.group(a.groupId);return g==null?"Missing group":g.members.isEmpty()?"Empty group":"";}if(a.definitions.isEmpty())return "Choose counters";return a.definitions.stream().anyMatch(id->p.definition(id)==null)?"Missing counter":"";}
    protected void build(){action("Create Reset Action (unbound)",()->{if(editor.working.resetActions.size()>=64){error="At most 64 actions.";return;}Config.ResetAction a=new Config.ResetAction();a.name="Reset Usage "+(editor.working.resetActions.size()+1);a.profileId=editor.profile().id;editor.working.resetActions.add(a);minecraft.setScreen(new ResetActionScreen(this,editor,a));});
        for(Config.ResetAction a:editor.working.resetActions)action(a.name+(problem(editor.working,a).isEmpty()?"":" · Disabled: "+problem(editor.working,a)),()->minecraft.setScreen(new ResetActionScreen(this,editor,a)));
        action("Reset All Usage Now",()->{TalliumClient.STORE.resetAll();TalliumClient.hint("All tracked usage reset.");});}
}

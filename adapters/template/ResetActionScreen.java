package dev.sig.tallium.adapter;
import dev.sig.tallium.config.*;
public final class ResetActionScreen extends FormScreen {
    private final TalliumScreen editor;private final Config.ResetAction action;
    public ResetActionScreen(ResetActionsScreen parent,TalliumScreen editor,Config.ResetAction action){super(parent,"Tallium · Reset Action");this.editor=editor;this.action=action;}
    protected void build(){
        error=ResetActionsScreen.problem(editor.working,action);
        field("Name",action.name,v->action.name=v);
        action("Hold for 2 Seconds: "+action.hold,()->{action.hold=!action.hold;refresh();});
        action("Confirm Reset: "+action.confirm,()->{action.confirm=!action.confirm;refresh();});
        action("Player Scope: "+action.scope,()->{action.scope=switch(action.scope){case EVERYONE->Config.Scope.YOURSELF;case YOURSELF->Config.Scope.SELECTED;default->Config.Scope.EVERYONE;};refresh();});
        if(action.scope==Config.Scope.SELECTED)for(var p:TalliumClient.roster.values())action((action.players.contains(p.uuid().toString())?"Remove ":"Select ")+p.name(),()->{String id=p.uuid().toString();if(action.players.contains(id))action.players.remove(id);else action.players.add(id);refresh();});
        action("All Counters: "+action.all,()->{action.all=!action.all;action.groupId=null;refresh();});
        if(!action.all){for(var d:editor.profile().definitions)action((action.definitions.contains(d.id)?"Remove ":"Select ")+d.label,()->{action.profileId=editor.profile().id;action.groupId=null;if(action.definitions.contains(d.id))action.definitions.remove(d.id);else action.definitions.add(d.id);refresh();});
            for(var g:editor.profile().groups)action("Target Group: "+g.name,()->{action.profileId=editor.profile().id;action.groupId=g.id;action.definitions.clear();refresh();});}
        action("Delete Action",()->{editor.working.resetActions.remove(action);onClose();});
    }
}

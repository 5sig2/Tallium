package dev.sig.tallium.adapter;
import dev.sig.tallium.config.*;
import java.util.*;
public final class ProfilesScreen extends FormScreen {
    private final TalliumScreen editor;private String name="New Profile";
    public ProfilesScreen(TalliumScreen editor){super(editor,"Tallium · Manage Profiles");this.editor=editor;}
    protected void build(){
        field("Profile name",name,v->name=v);
        action("Create",()->{if(editor.working.profiles.size()>=128){error="At most 128 profiles.";return;}Config.Profile p=new Config.Profile();p.name=unique(name);editor.working.profiles.add(p);refresh();});
        action("Rename Active",()->{editor.profile().name=unique(name,editor.profile().id);refresh();});
        action("Duplicate Active",()->{if(editor.working.profiles.size()>=128)return;Config.Profile p=ShareCode.remap(editor.profile());p.name=unique(editor.profile().name+" copy");editor.working.profiles.add(p);refresh();});
        action("Delete Active",()->{if(editor.working.profiles.size()==1){minecraft.setScreen(new RestoreProfileScreen(this,editor));}else{editor.working.profiles.remove(editor.profile());editor.working.activeProfile=editor.working.profiles.getFirst().id;refresh();}});
        action("Copy Profile Code",()->copy("PROFILE"));action("Copy Layout Code",()->copy("LAYOUT"));
        action("Import Code",()->minecraft.setScreen(new ImportScreen(this,editor)));
        for(Config.Profile p:editor.working.profiles)action((p.id.equals(editor.working.activeProfile)?"Active: ":"Activate: ")+p.name,()->minecraft.setScreen(new SwitchProfileScreen(this,editor,p.id)));
    }
    private String unique(String value){return unique(value,null);}
    private String unique(String value,String except){String n=value.isBlank()?"Profile":value;String candidate=n;int i=2;while(true){final String c=candidate;if(editor.working.profiles.stream().noneMatch(p->!p.id.equals(except)&&p.name.equals(c)))return candidate;candidate=n+" "+i++;}}
    private void copy(String kind){try{String code=ShareCode.encode(editor.profile(),kind,TalliumClient.GAME,editor.profile().groups.stream().map(g->g.id).toList());minecraft.keyboardHandler.setClipboard(code);minecraft.setScreen(new TextScreen(this,"Copy "+kind+" Code",code));}catch(Exception e){error=java.util.Objects.toString(e.getMessage(),"Invalid settings.");}}
}

package dev.sig.tallium.adapter;

import dev.sig.tallium.tracking.LabelAssociations;
import dev.sig.tallium.config.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import java.util.*;

public final class CustomLabels {
    public record Anchor(Tags.Layout layout,Vec3 attachment,boolean custom,boolean fallback) {}
    private static final LabelAssociations associations=new LabelAssociations();
    private static final Map<UUID,Entity> labels=new HashMap<>();
    private static final Map<UUID,String> texts=new HashMap<>();
    public static void clear(){associations.clear();labels.clear();texts.clear();}
    public static void remove(UUID id){associations.remove(id);labels.remove(id);texts.remove(id);}
    private static LabelAssociations.Point point(Entity e){return new LabelAssociations.Point(e.getX(),e.getY(),e.getZ());}
    public static void tick(long tick){
        var mc=Minecraft.getInstance();if(mc.level==null){clear();return;}
        List<LabelAssociations.Player> players=new ArrayList<>();for(Player p:mc.level.players()){List<String> names=new ArrayList<>(List.of(p.getName().getString(),p.getDisplayName().getString()));var info=mc.getConnection()==null?null:mc.getConnection().getPlayerInfo(p.getUUID());if(info!=null&&info.getTabListDisplayName()!=null&&!info.getTabListDisplayName().getString().isBlank())names.add(info.getTabListDisplayName().getString());players.add(new LabelAssociations.Player(p.getUUID(),List.copyOf(names),point(p)));}
        List<LabelAssociations.Label> samples=new ArrayList<>();labels.clear();texts.clear();
        for(Entity e:mc.level.entitiesForRendering()){
            if(samples.size()>=1024)break;Component text=null;
            if(e instanceof ArmorStand&&e.hasCustomName()&&e.isCustomNameVisible())text=e.getCustomName();
            else if(e instanceof Display.TextDisplay display)text=((dev.sig.tallium.mixin.TextDisplayAccessor)display).tallium$text();
            if(text==null||text.getString().isBlank())continue;
            String value=text.getString();if(value.length()>1024)continue;
            UUID explicit=e.getRootVehicle() instanceof Player p?p.getUUID():null;
            labels.put(e.getUUID(),e);texts.put(e.getUUID(),value);if(e instanceof Display.TextDisplay display&&!supported(display))continue;samples.add(new LabelAssociations.Label(e.getUUID(),value,point(e),explicit));
        }
        associations.update(players,samples,tick);
    }
    public static UUID owner(UUID label){return associations.owner(label);}
    public static Anchor anchor(Player player,boolean vanillaVisible,float delta){
        var mc=Minecraft.getInstance();Config.Profile cfg=TalliumClient.config.active();
        if(!cfg.nametags||mc.player==null||mc.level==null||mc.level.getEntity(player.getId())!=player||player==mc.player||mc.options.hideGui||player.isInvisibleTo(mc.player)||player.isRemoved()||player.isDeadOrDying()||player.distanceToSqr(mc.player)>cfg.nametagDistance*cfg.nametagDistance)return null;
        Entity label=null;NameLine line=null;int width=mc.font.width(player.getDisplayName());
        for(var entry:labels.entrySet())if(player.getUUID().equals(associations.owner(entry.getKey()))){
            Entity candidate=entry.getValue();NameLine candidateLine=nameLine(candidate,player,delta);
            if(candidateLine!=null&&(label==null||candidateLine.position().y>line.position().y||candidateLine.position().y==line.position().y&&candidate.getId()<label.getId())){label=candidate;line=candidateLine;}
        }
        boolean custom=label!=null,fallback=!custom&&!vanillaVisible;if(fallback&&!cfg.nametagFallback)return null;
        Vec3 position;
        if(custom){

            width=line.width();position=line.position().subtract(player.getPosition(delta)).add(0,-.5,0);
        }
        else {var attachment=player.getAttachments().getNullable(net.minecraft.world.entity.EntityAttachment.NAME_TAG,0,player.getViewYRot(delta));position=attachment==null?new Vec3(0,player.getBbHeight(),0):attachment;
            if(fallback){width=0;double clearance=player.getY()+player.getBbHeight()+.6;for(var entry:labels.entrySet()){Entity e=entry.getValue();if(e.position().subtract(player.position()).horizontalDistanceSqr()<2.25&&Math.abs(e.getY()-player.getY())<5)clearance=Math.max(clearance,top(e,texts.get(entry.getKey()))+.3);}position=new Vec3(0,clearance-player.getY()-.5,0);}}

        int space=Math.max(1,mc.font.width(" "));Component gap=Component.literal(" ".repeat((int)Math.ceil((width/cfg.nametagScale+2)/(double)space)));
        Tags.Layout layout=Tags.build(player.getUUID(),gap,false);if(layout.icons().isEmpty())return null;
        position=position.add(cameraVector(cfg.nametagX*.025,cfg.nametagY*.025,0));return new Anchor(layout,position,custom,fallback);
    }
    private static boolean supported(Display.TextDisplay d){var state=d.renderState();if(state==null||state.billboardConstraints()!=Display.BillboardConstraints.CENTER)return false;var transform=state.transformation().get(d.calculateInterpolationProgress(1));var scale=transform.getScale();return scale.x()>0&&scale.y()>0&&scale.x()<=4&&scale.y()<=4&&Math.abs(transform.getLeftRotation().w())>.9999&&Math.abs(transform.getRightRotation().w())>.9999;}
    public record NameLine(Vec3 position,int width,int index,int lines) {}
    private static List<String> names(Player player){var mc=Minecraft.getInstance();List<String> values=new ArrayList<>(List.of(player.getName().getString(),player.getDisplayName().getString()));var info=mc.getConnection()==null?null:mc.getConnection().getPlayerInfo(player.getUUID());if(info!=null&&info.getTabListDisplayName()!=null)values.add(info.getTabListDisplayName().getString());return values;}
    private static Vec3 cameraVector(double x,double y,double z){var v=new org.joml.Vector3f((float)x,(float)y,(float)z).rotate(Minecraft.getInstance().gameRenderer.@MAIN_CAMERA@().rotation());return new Vec3(v.x(),v.y(),v.z());}
    public static NameLine nameLine(Entity entity,Player player,float delta){
        var mc=Minecraft.getInstance();
        if(entity instanceof Display.TextDisplay d){
            if(!supported(d)||d.textRenderState()==null)return null;
            var state=d.textRenderState();var lines=mc.font.split(((dev.sig.tallium.mixin.TextDisplayAccessor)d).tallium$text(),state.lineWidth());
            var known=names(player);int selected=-1;
            for(int i=0;i<lines.size();i++){StringBuilder text=new StringBuilder();lines.get(i).accept((index,style,codepoint)->{text.appendCodePoint(codepoint);return true;});if(known.stream().anyMatch(name->LabelAssociations.named(text.toString(),name))){if(selected!=-1)return null;selected=i;}}
            if(selected<0){if(lines.size()==1&&entity.getRootVehicle()==player)selected=0;else return null;}
            int max=lines.stream().mapToInt(mc.font::width).max().orElse(0),name=mc.font.width(lines.get(selected));
            double align=switch(Display.TextDisplay.getAlign(state.flags())){case LEFT->0;case RIGHT->max-name;case CENTER->(max-name)/2.0;};
            var scale=d.renderState().transformation().get(d.calculateInterpolationProgress(delta)).getScale();
            double center=1-max/2.0+align+name/2.0;
            Vec3 baseline=labelPosition(d,delta).add(cameraVector(center*.025*scale.x(),(lines.size()*10-1-selected*10)*.025*scale.y(),0));
            return new NameLine(baseline,(int)Math.ceil(name*scale.x()),selected,lines.size());
        }
        if(entity instanceof ArmorStand&&entity.getCustomName()!=null){
            var attachment=entity.getAttachments().getNullable(net.minecraft.world.entity.EntityAttachment.NAME_TAG,0,entity.getViewYRot(delta));
            Vec3 baseline=entity.getPosition(delta).add(attachment==null?new Vec3(0,entity.getBbHeight(),0):attachment).add(0,.5,0);
            return new NameLine(baseline,mc.font.width(entity.getCustomName()),0,1);
        }
        return null;
    }
    private static Vec3 labelPosition(Entity e,float delta){Vec3 position=e.getPosition(delta);if(e instanceof Display.TextDisplay d&&d.renderState()!=null){var translation=d.renderState().transformation().get(d.calculateInterpolationProgress(delta)).getTranslation();position=position.add(cameraVector(-translation.x(),translation.y(),-translation.z()));}return position;}
    private static double top(Entity e,String text){if(e instanceof Display.TextDisplay d&&d.textRenderState()!=null&&d.renderState()!=null){int lines=Minecraft.getInstance().font.split(((dev.sig.tallium.mixin.TextDisplayAccessor)d).tallium$text(),d.textRenderState().lineWidth()).size();float scale=d.renderState().transformation().get(d.calculateInterpolationProgress(1)).getScale().y();return labelPosition(e,1).y+(lines*10+2)*.025*Math.abs(scale)+.15;}return e.getY()+e.getBbHeight()+.5;}
}

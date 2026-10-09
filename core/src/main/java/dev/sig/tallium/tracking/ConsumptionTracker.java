package dev.sig.tallium.tracking;

import dev.sig.tallium.items.ItemIdentity;
import java.util.*;


public final class ConsumptionTracker {
    private record Attempt(ItemIdentity item,String hand,int duration,long start,long last,long id,
                           UUID session,long epoch,boolean observedStart,long stopped) {}
    private final Map<UUID,Attempt> attempts=new HashMap<>();
    private final Map<UUID,Long> idle=new HashMap<>();
    private final Set<UUID> incomplete=new HashSet<>();
    public boolean incomplete(UUID player){return incomplete.contains(player);}
    private UUID session;
    private long epoch=-1,sequence;
    private void boundary(UUID current,long reset){if(!current.equals(session)||reset!=epoch){clear();session=current;epoch=reset;}}
    public void clear(){attempts.clear();idle.clear();incomplete.clear();}
    public void remove(UUID player){attempts.remove(player);idle.remove(player);incomplete.remove(player);}
    public void retain(Set<UUID> players){attempts.keySet().retainAll(players);idle.keySet().retainAll(players);incomplete.retainAll(players);}
    public Observation observe(UUID player,String hand,ItemIdentity item,int duration,boolean using,long tick,UUID current,long reset,String world){
        boundary(current,reset);Attempt a=attempts.get(player);
        if(!using){
            idle.put(player,tick);
            if(a==null||a.stopped>=0)return null;
            attempts.put(player,new Attempt(a.item,a.hand,a.duration,a.start,a.last,a.id,a.session,a.epoch,a.observedStart,tick));

            if(a.observedStart&&tick-a.last<=2&&tick-a.start>=a.duration-1)
                return event(player,a,Observation.Validation.ESTIMATED,world,"Uninterrupted observed consumption cycle");
            return null;
        }
        if(item==null||duration<=0||duration>72000){if(duration<0||a!=null&&a.stopped<0)incomplete.add(player);attempts.remove(player);idle.remove(player);return null;}
        if(a==null||a.stopped>=0||!a.hand.equals(hand)||!a.item.equals(item)){
            boolean start=idle.containsKey(player)&&tick-idle.get(player)<=2;
            if(a!=null&&a.stopped<0)start=false;
            if(!start)incomplete.add(player);
            if(attempts.size()<1024||attempts.containsKey(player))attempts.put(player,new Attempt(item,hand,duration,tick,tick,++sequence,current,reset,start,-1));
        }else{
            if(tick-a.last>2)incomplete.add(player);
            attempts.put(player,new Attempt(a.item,a.hand,a.duration,a.start,tick,a.id,a.session,a.epoch,a.observedStart&&tick-a.last<=2,-1));
        }
        idle.remove(player);return null;
    }

    public Observation confirm(UUID player,long tick,UUID current,long reset,String world){
        boundary(current,reset);Attempt a=attempts.get(player);
        if(a==null||tick-a.last>10||!a.observedStart||tick-a.start<a.duration-2)return null;
        return event(player,a,Observation.Validation.CONFIRMED,world,"Observer received server consumption completion");
    }
    private Observation event(UUID player,Attempt a,Observation.Validation confidence,String world,String evidence){
        return new Observation(player,a.item,"consumption",Observation.Action.CONSUME,Observation.Unit.CONSUMPTIONS,1,evidence,confidence,a.session,world,a.id,a.epoch,"remote-consume/"+player+"/"+a.id);
    }
}

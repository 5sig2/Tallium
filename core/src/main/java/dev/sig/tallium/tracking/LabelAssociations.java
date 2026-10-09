package dev.sig.tallium.tracking;

import java.util.*;
import java.util.regex.Pattern;


public final class LabelAssociations {
    public record Point(double x,double y,double z){
        public Point subtract(Point p){return new Point(x-p.x,y-p.y,z-p.z);}
        public double horizontal(){return x*x+z*z;}
        public double squared(){return x*x+y*y+z*z;}
    }
    public record Player(UUID id,List<String> names,Point position) {}
    public record Label(UUID id,String text,Point position,UUID explicitPlayer) {}
    private record Candidate(UUID player,Point offset,long since,long last) {}
    private final Map<UUID,Candidate> candidates=new HashMap<>();
    private final Map<UUID,UUID> owners=new HashMap<>();
    public void clear(){candidates.clear();owners.clear();}
    public void remove(UUID id){candidates.remove(id);owners.remove(id);candidates.entrySet().removeIf(e->e.getValue().player.equals(id));owners.values().removeIf(id::equals);}
    public UUID owner(UUID label){return owners.get(label);}
    public static String plain(String s){return s.replaceAll("§[0-9a-fk-orA-FK-OR]","").strip().toLowerCase(Locale.ROOT).replaceAll("\\s+"," ");}
    public static boolean named(String text,String name){String n=plain(name);return !n.isEmpty()&&Pattern.compile("(?<![a-z0-9_])"+Pattern.quote(n)+"(?![a-z0-9_])").matcher(plain(text)).find();}
    public void update(List<Player> players,List<Label> labels,long tick){
        owners.clear();Set<UUID> live=new HashSet<>();
        for(Label label:labels){
            live.add(label.id);List<Player> matches=players.stream().filter(p->{Point d=label.position.subtract(p.position);return d.horizontal()<=2.25&&d.y>=-.25&&d.y<=5;})
                .filter(p->label.explicitPlayer!=null?label.explicitPlayer.equals(p.id):p.names.stream().anyMatch(n->named(label.text,n))).toList();

            if(matches.size()!=1){candidates.remove(label.id);continue;}
            Player p=matches.getFirst();Point offset=label.position.subtract(p.position);Candidate old=candidates.get(label.id);
            if(label.explicitPlayer!=null){owners.put(label.id,p.id);candidates.put(label.id,new Candidate(p.id,offset,tick,tick));continue;}
            boolean stable=old!=null&&old.player.equals(p.id)&&tick-old.last<=2&&offset.subtract(old.offset).squared()<.0625;
            Candidate next=new Candidate(p.id,offset,stable?old.since:tick,tick);candidates.put(label.id,next);
            if(tick-next.since>=4)owners.put(label.id,p.id);
        }
        candidates.keySet().retainAll(live);
    }
}

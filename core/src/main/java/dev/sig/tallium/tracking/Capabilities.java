package dev.sig.tallium.tracking;
import java.util.*;
public final class Capabilities {
    public enum Support { SUPPORTED, UNKNOWN, UNSUPPORTED }
    public record Capability(String family,String unit,Support local,Support remote,String variants,String evidence,String gap,String verification) {}
    private final Map<String,Capability> entries=new HashMap<>();
    public Capabilities(boolean vanillaTotemEvent){
        add("totems","activations",Support.SUPPORTED,"Vanilla totems","Server activation animation",vanillaTotemEvent?"Counts visible activations.":"The vanilla totem must be visible in a hand. Custom death-protection items are excluded.");
        for(String family:List.of("pearls","wind","experience"))add(family,"throws",Support.SUPPORTED,"Vanilla items","Player-owned projectiles","Owners are retried for one second. Other players must be within 16 blocks of the projectile's first observed position. Counts start when you join.");
        for(String family:List.of("food","potions"))add(family,"consumptions",Support.SUPPORTED,"Observed item and potion type","Your server completion; other players' uninterrupted eating/drinking duration","Inventory quantities are ignored; cancelled or incompletely observed attempts are excluded. Thrown potions use projectile ownership.");
        add("arrows","shots",Support.SUPPORTED,"Exact local ammunition; arrow or spectral arrow for others","Player-owned arrows grouped by discharge","Multishot counts once. Remote arrows arriving within two ticks count as one volley; potion variants of remote tipped arrows are unavailable.");
        add("cobwebs","placements",Support.UNSUPPORTED,"Vanilla cobwebs","Your placement and server block update","Minecraft does not identify who placed a remote block.");
    }
    private void add(String family,String unit,Support remote,String variants,String evidence,String gap){entries.put(family,new Capability(family,unit,Support.SUPPORTED,remote,variants,evidence,gap,""));}
    public Capability forFamily(String family){return entries.getOrDefault(family,new Capability(family,"uses",Support.UNSUPPORTED,Support.UNSUPPORTED,"Inventory only","Remaining counts are available for all items.","Usage tracking is unavailable for this item.",""));}
    public List<Capability> all(){return entries.values().stream().sorted(Comparator.comparing(Capability::family)).toList();}
}

package dev.sig.tallium.tracking;

import dev.sig.tallium.items.*;
import java.util.*;
import java.util.function.Predicate;


public final class CounterStore {
    public static final int MAX_IDENTITIES = 65536, MAX_BUCKETS = 16384, MAX_DIAGNOSTICS = 128;
    public record Bucket(UUID player, ItemIdentity item, Observation.Unit unit) {}
    public record Diagnostic(long sequence, String reason) {}
    private UUID session = UUID.randomUUID();
    private long epoch;
    private boolean saturated;
    private final Set<String> seen = new HashSet<>();
    private final Map<Bucket, Long> counts = new HashMap<>();
    private record Estimate(Bucket bucket,int quantity,long epoch) {}
    private final Map<String,Estimate> estimates=new HashMap<>();
    private final Deque<Diagnostic> diagnostics = new ArrayDeque<>();
    private record Query(UUID player,List<Selector> selectors) {}
    private final Map<Query,Long> totals=new HashMap<>();
    private long revision;
    public long revision(){return revision;}
    public UUID session() { return session; }
    public long epoch() { return epoch; }
    public boolean saturated() { return saturated; }
    public List<Diagnostic> diagnostics() { return List.copyOf(diagnostics); }
    public void diagnostic(long sequence, String reason) {
        if (diagnostics.size() == MAX_DIAGNOSTICS) diagnostics.removeFirst();
        diagnostics.addLast(new Diagnostic(sequence, reason));
    }
    public boolean accept(Observation event) {
        if ((event.validation() != Observation.Validation.CONFIRMED && event.validation() != Observation.Validation.ESTIMATED) || event.item() == null || event.player() == null ||
            !session.equals(event.session()) || event.resetEpoch() != epoch || event.quantity() <= 0 || event.quantity() > 64) return false;
        String id = event.world() + "/" + event.deduplicationKey();
        Bucket bucket = new Bucket(event.player(), event.item(), event.unit());
        Estimate prior=estimates.get(id);
        if(prior!=null&&prior.epoch==epoch&&prior.bucket.equals(bucket)&&prior.quantity==event.quantity()&&event.validation()==Observation.Validation.CONFIRMED){
            estimates.remove(id);revision++;return true;
        }
        if (saturated || seen.contains(id)) return false;
        if (seen.size() >= MAX_IDENTITIES || (!counts.containsKey(bucket) && counts.size() >= MAX_BUCKETS)) {
            saturated = true; diagnostic(event.sequence(), "Session identity limit reached; new usage is Unknown until reconnect."); return false;
        }
        seen.add(id); counts.merge(bucket,(long)event.quantity(),Math::addExact);
        if(event.validation()==Observation.Validation.ESTIMATED)estimates.put(id,new Estimate(bucket,event.quantity(),epoch));
        totals.clear();revision++; return true;
    }
    public long total(UUID player, Collection<Selector> selectors) {
        Query key=new Query(player,List.copyOf(selectors));Long cached=totals.get(key);if(cached!=null)return cached;
        long total=counts.entrySet().stream().filter(e -> (player == null || player.equals(e.getKey().player())) &&
            selectors.stream().anyMatch(s -> s.matches(e.getKey().item()))).mapToLong(Map.Entry::getValue).sum();
        if(totals.size()<32768)totals.put(key,total);return total;
    }
    public Map<Bucket,Long> snapshot() { return Map.copyOf(counts); }
    public boolean estimated(UUID player,Collection<Selector> selectors){return estimates.values().stream().anyMatch(e->(player==null||player.equals(e.bucket.player()))&&selectors.stream().anyMatch(s->s.matches(e.bucket.item())));}
    public void reset(Predicate<UUID> players, Collection<Selector> selectors) {
        epoch++;
        totals.clear();revision++;
        counts.entrySet().removeIf(e -> players.test(e.getKey().player()) && selectors.stream().anyMatch(s -> s.matches(e.getKey().item())));
        estimates.entrySet().removeIf(e->players.test(e.getValue().bucket.player())&&selectors.stream().anyMatch(s->s.matches(e.getValue().bucket.item())));
    }
    public void resetAll() { epoch++; counts.clear();estimates.clear();totals.clear();revision++; }
    public void newSession() { session = UUID.randomUUID(); epoch = 0; seen.clear(); counts.clear();estimates.clear();totals.clear();revision++; diagnostics.clear(); saturated = false; }
}

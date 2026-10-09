package dev.sig.tallium.items;

import java.util.*;


public record ItemIdentity(String item, String potion, List<Effect> effects, String exactComponents, Set<String> categories) {
    public record Effect(String id, int amplifier, int duration) implements Comparable<Effect> {
        public int compareTo(Effect e) { return Comparator.comparing(Effect::id).thenComparingInt(Effect::amplifier).thenComparingInt(Effect::duration).compare(this,e); }
    }
    public ItemIdentity {
        Objects.requireNonNull(item);
        potion = potion == null ? "" : potion;
        effects = effects == null ? List.of() : effects.stream().sorted().toList();
        exactComponents = exactComponents == null ? "" : exactComponents;
        categories = categories == null ? Set.of() : Set.copyOf(categories);
    }
    public String key() { return item + "|" + potion + "|" + effects + "|" + exactComponents; }
}

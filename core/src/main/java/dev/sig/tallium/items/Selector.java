package dev.sig.tallium.items;

import java.util.*;

public record Selector(String item, String category, String potion, String effect, Integer amplifier,
                       Integer duration, String exactComponents) {
    public static Selector item(String id) { return new Selector(id,null,null,null,null,null,null); }
    public boolean matches(ItemIdentity stack) {
        if (item != null && !item.equals(stack.item())) return false;
        if (category != null && !stack.categories().contains(category)) return false;
        if (potion != null && !potion.equals(stack.potion())) return false;
        if ((effect != null || amplifier != null || duration != null) && stack.effects().stream().noneMatch(e -> (effect == null || effect.equals(e.id())) &&
                (amplifier == null || amplifier == e.amplifier()) && (duration == null || duration == e.duration()))) return false;
        return exactComponents == null || exactComponents.equals(stack.exactComponents());
    }
}

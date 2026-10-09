package dev.sig.tallium.inventory;

import dev.sig.tallium.items.*;
import java.util.*;

public record InventorySnapshot(List<Slot> slots) {
    public record Slot(int index, ItemIdentity item, int count, boolean hotbar, boolean offhand) {}
    public InventorySnapshot { slots = List.copyOf(slots);Set<Integer> ids=new HashSet<>();for(Slot slot:slots)if(!ids.add(slot.index())||slot.count()<=0)throw new IllegalArgumentException("Inventory slots must be unique with positive counts."); }
    public long total(Collection<Selector> selectors, boolean hotbarOnly) {
        return slots.stream().filter(s -> (!hotbarOnly || s.hotbar()) &&
                selectors.stream().anyMatch(v -> v.matches(s.item()))).mapToLong(Slot::count).sum();
    }
    public static InventorySnapshot empty() { return new InventorySnapshot(List.of()); }
}

package dev.sig.tallium.tracking;

import dev.sig.tallium.items.ItemIdentity;
import java.util.UUID;

public record Observation(UUID player, ItemIdentity item, String family, Action action, Unit unit, int quantity,
                          String evidence, Validation validation, UUID session, String world,
                          long sequence, long resetEpoch, String deduplicationKey) {
    public enum Action { THROW, CONSUME, PLACE, ACTIVATE, FIRE }
    public enum Unit { THROWS, CONSUMPTIONS, PLACEMENTS, ACTIVATIONS, SHOTS }
    public enum Validation { PENDING, ESTIMATED, CONFIRMED, REJECTED }
}

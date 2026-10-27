package de.grindpvp.backend.model;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class KitStatId implements Serializable {
    private UUID player;
    private Kit kit;

    public KitStatId() {
    }

    public KitStatId(UUID player, Kit kit) {
        this.player = player;
        this.kit = kit;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof KitStatId that)) return false;
        return Objects.equals(player, that.player) && kit == that.kit;
    }

    @Override
    public int hashCode() {
        return Objects.hash(player, kit);
    }
}

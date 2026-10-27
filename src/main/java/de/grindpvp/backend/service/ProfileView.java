package de.grindpvp.backend.service;

import de.grindpvp.backend.model.Kit;
import de.grindpvp.backend.model.Role;
import de.grindpvp.backend.model.Tier;

import java.util.Map;
import java.util.UUID;

public record ProfileView(
        UUID id,
        String username,
        Role role,
        Role title,
        boolean hasMod,
        Tier overallTier,
        Map<Kit, KitStatView> kits
) {
    public record KitStatView(Tier tier, int wins, int losses, int points) {
    }
}

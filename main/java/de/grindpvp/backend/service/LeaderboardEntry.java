package de.grindpvp.backend.service;

import de.grindpvp.backend.model.Tier;

import java.util.UUID;

public record LeaderboardEntry(
        int place,
        UUID playerId,
        String username,
        Tier tier,
        int wins,
        int losses,
        int points
) {
}

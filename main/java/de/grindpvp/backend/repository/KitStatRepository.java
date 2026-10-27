package de.grindpvp.backend.repository;

import de.grindpvp.backend.model.Kit;
import de.grindpvp.backend.model.KitStat;
import de.grindpvp.backend.model.KitStatId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface KitStatRepository extends JpaRepository<KitStat, KitStatId> {
    List<KitStat> findByKitOrderByWinsDesc(Kit kit);

    List<KitStat> findByPlayerId(UUID playerId);

    Optional<KitStat> findByPlayerIdAndKit(UUID playerId, Kit kit);
}

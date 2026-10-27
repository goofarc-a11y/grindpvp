package de.grindpvp.backend.repository;

import de.grindpvp.backend.model.PlayerProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PlayerProfileRepository extends JpaRepository<PlayerProfile, UUID> {
    Optional<PlayerProfile> findByUsernameIgnoreCase(String username);
}

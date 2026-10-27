package de.grindpvp.backend.api;

import de.grindpvp.backend.model.Kit;
import de.grindpvp.backend.service.GrindPvPService;
import de.grindpvp.backend.service.LeaderboardEntry;
import de.grindpvp.backend.service.ProfileView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class PlayerController {
    private final GrindPvPService service;

    public PlayerController(GrindPvPService service) {
        this.service = service;
    }

    @GetMapping("/players/{id}")
    public ProfileView profile(@PathVariable UUID id) {
        return service.getProfileById(id);
    }

    @GetMapping("/players/by-name")
    public ProfileView profileByName(@RequestParam String name) {
        return service.getProfileByName(name);
    }

    @GetMapping("/leaderboards/{kit}")
    public List<LeaderboardEntry> leaderboard(@PathVariable Kit kit) {
        return service.leaderboard(kit);
    }
}

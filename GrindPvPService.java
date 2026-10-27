package de.grindpvp.backend.service;

import de.grindpvp.backend.model.Kit;
import de.grindpvp.backend.model.KitStat;
import de.grindpvp.backend.model.MatchReport;
import de.grindpvp.backend.model.PlayerProfile;
import de.grindpvp.backend.model.Role;
import de.grindpvp.backend.model.Tier;
import de.grindpvp.backend.repository.KitStatRepository;
import de.grindpvp.backend.repository.MatchReportRepository;
import de.grindpvp.backend.repository.PlayerProfileRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class GrindPvPService {
    private final PlayerProfileRepository profiles;
    private final KitStatRepository stats;
    private final MatchReportRepository reports;
    private final MinecraftProfileResolver profileResolver;

    public GrindPvPService(
            PlayerProfileRepository profiles,
            KitStatRepository stats,
            MatchReportRepository reports,
            MinecraftProfileResolver profileResolver
    ) {
        this.profiles = profiles;
        this.stats = stats;
        this.reports = reports;
        this.profileResolver = profileResolver;
    }

    @Transactional
    public ProfileView getOrCreateProfile(UUID id, String username) {
        validateUsername(username);
        PlayerProfile profile = profiles.findById(id).orElseGet(() -> {
            PlayerProfile namedProfile = profiles.findByUsernameIgnoreCase(username).orElse(null);
            if (namedProfile != null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "This username is already linked to another player UUID.");
            }
            return profiles.save(new PlayerProfile(id, username));
        });
        if (!profile.getUsername().equals(username)) {
            profile.setUsername(username);
        }

        EnumMap<Kit, ProfileView.KitStatView> kitViews = new EnumMap<>(Kit.class);
        int totalPoints = 0;
        int kitCount = 0;
        for (Kit kit : Kit.values()) {
            if (kit == Kit.OVERALL) {
                continue;
            }
            KitStat stat = stats.findByPlayerIdAndKit(id, kit)
                    .orElseGet(() -> stats.save(new KitStat(profile, kit)));
            kitViews.put(kit, toView(stat));
            totalPoints += stat.getTier().points();
            kitCount++;
        }
        Tier overallTier = Tier.fromAverage((double) totalPoints / kitCount);
        return new ProfileView(profile.getId(), profile.getUsername(), profile.getRole(),
                profile.getTitle(), profile.hasMod(), overallTier, kitViews);
    }

    @Transactional
    public ProfileView registerServerPlayer(UUID id, String username, boolean hasMod) {
        getOrCreateProfile(id, username);
        PlayerProfile profile = profiles.getReferenceById(id);
        profile.setHasMod(hasMod);
        return getProfileById(id);
    }

    @Transactional(readOnly = true)
    public ProfileView getProfileById(UUID id) {
        PlayerProfile profile = profiles.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Player not found."));
        return profileView(profile);
    }

    @Transactional(readOnly = true)
    public ProfileView getProfileByName(String username) {
        validateUsername(username);
        PlayerProfile profile = profiles.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Player not found."));
        return profileView(profile);
    }

    @Transactional
    public List<LeaderboardEntry> leaderboard(Kit kit) {
        if (kit == Kit.OVERALL) {
            List<ProfileView> ordered = profiles.findAll().stream()
                    .map(this::profileView)
                    .sorted((left, right) -> Integer.compare(
                            right.overallTier().points(), left.overallTier().points()))
                    .toList();
            java.util.ArrayList<LeaderboardEntry> result = new java.util.ArrayList<>(ordered.size());
            for (int i = 0; i < ordered.size(); i++) {
                ProfileView view = ordered.get(i);
                result.add(new LeaderboardEntry(i + 1, view.id(), view.username(), view.overallTier(),
                        totalWins(view), totalLosses(view), view.overallTier().points()));
            }
            return result;
        }
        List<KitStat> ordered = stats.findByKitOrderByWinsDesc(kit).stream()
                .sorted((left, right) -> {
                    int tier = Integer.compare(right.getTier().points(), left.getTier().points());
                    if (tier != 0) return tier;
                    int wins = Integer.compare(right.getWins(), left.getWins());
                    if (wins != 0) return wins;
                    return Integer.compare(left.getLosses(), right.getLosses());
                })
                .toList();
        java.util.ArrayList<LeaderboardEntry> result = new java.util.ArrayList<>(ordered.size());
        for (int i = 0; i < ordered.size(); i++) {
            KitStat stat = ordered.get(i);
            result.add(new LeaderboardEntry(i + 1, stat.getPlayer().getId(),
                    stat.getPlayer().getUsername(), stat.getTier(), stat.getWins(),
                    stat.getLosses(), stat.getTier().points()));
        }
        return result;
    }

    @Transactional
    public void assignRole(String username, Role role) {
        validateUsername(username);
        if (role == Role.MEMBER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Use a staff or cosmetic role; MEMBER is assigned by default.");
        }
        PlayerProfile profile = profiles.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Player must join once before a role can be assigned."));
        profile.setRole(role);
    }

    @Transactional
    public void submitResult(ResultSubmission submission) {
        if (submission.kit() == Kit.OVERALL) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OVERALL is not a playable kit.");
        }
        if (submission.winnerId().equals(submission.loserId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Winner and loser must be different players.");
        }
        if (submission.matchesPlayed() < 1 || submission.matchesPlayed() > 1000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "matchesPlayed must be between 1 and 1000.");
        }
        validateUsername(submission.winnerName());
        validateUsername(submission.loserName());
        if (submission.report() == null || submission.report().isBlank() || submission.report().length() > 500) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A report of at most 500 characters is required.");
        }
        getOrCreateProfile(submission.winnerId(), submission.winnerName());
        getOrCreateProfile(submission.loserId(), submission.loserName());
        PlayerProfile winner = profiles.getReferenceById(submission.winnerId());
        PlayerProfile loser = profiles.getReferenceById(submission.loserId());
        KitStat winnerStat = stats.findByPlayerIdAndKit(winner.getId(), submission.kit())
                .orElseGet(() -> stats.save(new KitStat(winner, submission.kit())));
        KitStat loserStat = stats.findByPlayerIdAndKit(loser.getId(), submission.kit())
                .orElseGet(() -> stats.save(new KitStat(loser, submission.kit())));

        for (int i = 0; i < submission.matchesPlayed(); i++) {
            winnerStat.addWin();
            loserStat.addLoss();
        }
        if (loserStat.getTier() == Tier.HT1) {
            winnerStat.setTier(Tier.HT1);
            loserStat.setTier(Tier.LT1);
        } else if (winnerStat.getTier().points() < loserStat.getTier().points()) {
            winnerStat.setTier(winnerStat.getTier().promoteTo(loserStat.getTier()));
        } else if (winnerStat.getTier().points() > loserStat.getTier().points()) {
            loserStat.setTier(loserStat.getTier().demoteOne());
        } else if (higherTierPlayerCount(submission.kit(), winner, winnerStat.getTier()) < 2) {
            Tier promotedTier = winnerStat.getTier().promoteOne();
            boolean ht1Occupied = promotedTier == Tier.HT1
                    && stats.findByKitOrderByWinsDesc(submission.kit()).stream()
                    .anyMatch(stat -> !stat.getPlayer().getId().equals(winner.getId())
                            && stat.getTier() == Tier.HT1);
            if (!ht1Occupied) {
                winnerStat.setTier(promotedTier);
            }
        }
        reports.save(new MatchReport(submission.kit(), winner.getId(), winner.getUsername(),
                loser.getId(), loser.getUsername(), submission.matchesPlayed(), submission.report()));
    }

    @Transactional
    public void submitResultByNames(NameResultSubmission submission) {
        if (submission.kit() == null || submission.kit() == Kit.OVERALL) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A playable kit is required.");
        }
        validateUsername(submission.winnerName());
        validateUsername(submission.loserName());
        if (submission.winnerName().equalsIgnoreCase(submission.loserName())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Winner and loser must be different players.");
        }
        if (submission.matchesPlayed() < 1 || submission.matchesPlayed() > 1000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "matchesPlayed must be between 1 and 1000.");
        }

        MinecraftProfileResolver.ResolvedProfile winner = resolveProfile(submission.winnerName());
        MinecraftProfileResolver.ResolvedProfile loser = resolveProfile(submission.loserName());
        if (winner.id().equals(loser.id())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Winner and loser must be different Minecraft accounts.");
        }
        String report = "Kit: " + submission.kit()
                + " | Sieger: " + winner.username()
                + " | Verlierer: " + loser.username()
                + " | Matches: " + submission.matchesPlayed();
        submitResult(new ResultSubmission(submission.kit(), winner.id(), winner.username(),
                loser.id(), loser.username(), submission.matchesPlayed(), report));
    }

    private ProfileView profileView(PlayerProfile profile) {
        Map<Kit, ProfileView.KitStatView> kitViews = new EnumMap<>(Kit.class);
        int totalPoints = 0;
        int kitCount = 0;
        for (Kit kit : Kit.values()) {
            if (kit == Kit.OVERALL) continue;
            KitStat stat = stats.findByPlayerIdAndKit(profile.getId(), kit)
                    .orElse(new KitStat(profile, kit));
            kitViews.put(kit, toView(stat));
            totalPoints += stat.getTier().points();
            kitCount++;
        }
        return new ProfileView(profile.getId(), profile.getUsername(), profile.getRole(), profile.getTitle(),
                profile.hasMod(), Tier.fromAverage((double) totalPoints / kitCount), kitViews);
    }

    private static ProfileView.KitStatView toView(KitStat stat) {
        return new ProfileView.KitStatView(stat.getTier(), stat.getWins(), stat.getLosses(),
                stat.getTier().points());
    }

    private long higherTierPlayerCount(Kit kit, PlayerProfile player, Tier tier) {
        return stats.findByKitOrderByWinsDesc(kit).stream()
                .filter(stat -> !stat.getPlayer().getId().equals(player.getId()))
                .filter(stat -> stat.getTier().points() > tier.points())
                .count();
    }

    private static int totalWins(ProfileView profile) {
        return profile.kits().values().stream().mapToInt(ProfileView.KitStatView::wins).sum();
    }

    private static int totalLosses(ProfileView profile) {
        return profile.kits().values().stream().mapToInt(ProfileView.KitStatView::losses).sum();
    }

    private static void validateUsername(String username) {
        if (username == null || !username.matches("[A-Za-z0-9_]{3,16}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Minecraft username.");
        }
    }

    private MinecraftProfileResolver.ResolvedProfile resolveProfile(String username) {
        return profiles.findByUsernameIgnoreCase(username)
                .map(profile -> new MinecraftProfileResolver.ResolvedProfile(
                        profile.getId(), profile.getUsername()))
                .orElseGet(() -> profileResolver.resolve(username));
    }

    public record NameResultSubmission(
            Kit kit,
            String winnerName,
            String loserName,
            int matchesPlayed
    ) {
    }

    public record ResultSubmission(
            Kit kit,
            UUID winnerId,
            String winnerName,
            UUID loserId,
            String loserName,
            int matchesPlayed,
            String report
    ) {
        public ResultSubmission {
            if (kit == null || winnerId == null || loserId == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "kit, winnerId and loserId are required.");
            }
        }
    }
}

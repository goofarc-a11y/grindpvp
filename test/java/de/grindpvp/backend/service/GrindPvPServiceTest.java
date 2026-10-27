package de.grindpvp.backend.service;

import de.grindpvp.backend.model.Kit;
import de.grindpvp.backend.model.KitStat;
import de.grindpvp.backend.model.PlayerProfile;
import de.grindpvp.backend.model.Role;
import de.grindpvp.backend.model.Tier;
import de.grindpvp.backend.repository.KitStatRepository;
import de.grindpvp.backend.repository.MatchReportRepository;
import de.grindpvp.backend.repository.PlayerProfileRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:grindpvp-test;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "grindpvp.server-token=test-server-token"
})
class GrindPvPServiceTest {
    @Autowired
    private GrindPvPService service;

    @Autowired
    private MatchReportRepository reports;

    @Autowired
    private KitStatRepository stats;

    @Autowired
    private PlayerProfileRepository profiles;

    @Test
    void profilesStartAsMembersAndResultUpdatesStatsAndPersistsReport() {
        UUID winnerId = UUID.randomUUID();
        UUID loserId = UUID.randomUUID();
        ProfileView initial = service.getOrCreateProfile(winnerId, "GrinderOne");

        assertEquals(Role.MEMBER, initial.role());
        assertEquals(Tier.LT5, initial.overallTier());
        assertEquals(14, initial.kits().size());

        service.submitResult(new GrindPvPService.ResultSubmission(
                Kit.UHC,
                winnerId,
                "GrinderOne",
                loserId,
                "GrinderTwo",
                3,
                "Kit: UHC | Sieger: GrinderOne | Verlierer: GrinderTwo | Matches: 3"
        ));

        ProfileView winner = service.getProfileById(winnerId);
        ProfileView loser = service.getProfileById(loserId);
        assertEquals(3, winner.kits().get(Kit.UHC).wins());
        assertEquals(3, loser.kits().get(Kit.UHC).losses());
        assertEquals(Tier.HT5, winner.kits().get(Kit.UHC).tier());
        assertEquals(1, reports.count());
    }

    @Test
    void sameTierWinDoesNotPromoteWhenTwoPlayersAreAlreadyAbove() {
        UUID winnerId = UUID.randomUUID();
        UUID loserId = UUID.randomUUID();
        ProfileView winner = service.getOrCreateProfile(winnerId, "GrinderThree");
        service.getOrCreateProfile(loserId, "GrinderFour");
        PlayerProfile higherOne = profilesForTest("HigherOne");
        PlayerProfile higherTwo = profilesForTest("HigherTwo");
        KitStat higherOneStat = stats.findByPlayerIdAndKit(higherOne.getId(), Kit.UHC).orElseThrow();
        higherOneStat.setTier(Tier.HT5);
        stats.save(higherOneStat);
        KitStat higherTwoStat = stats.findByPlayerIdAndKit(higherTwo.getId(), Kit.UHC).orElseThrow();
        higherTwoStat.setTier(Tier.HT5);
        stats.save(higherTwoStat);

        service.submitResult(new GrindPvPService.ResultSubmission(
                Kit.UHC, winnerId, "GrinderThree", loserId, "GrinderFour", 1,
                "Kit: UHC | Sieger: GrinderThree | Verlierer: GrinderFour | Matches: 1"
        ));

        assertEquals(Tier.LT5, service.getProfileById(winner.id()).kits().get(Kit.UHC).tier());
    }

    @Test
    void serverRegistrationTracksWhetherClientHasTheMod() {
        UUID id = UUID.randomUUID();
        assertFalse(service.registerServerPlayer(id, "ModdedPlayer", false).hasMod());
        assertTrue(service.registerServerPlayer(id, "ModdedPlayer", true).hasMod());
    }

    private PlayerProfile profilesForTest(String username) {
        UUID id = UUID.randomUUID();
        service.getOrCreateProfile(id, username);
        return profiles.getReferenceById(id);
    }
}

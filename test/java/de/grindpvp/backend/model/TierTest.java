package de.grindpvp.backend.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TierTest {
    @Test
    void mapsEveryTierToItsConfiguredPoints() {
        assertEquals(1, Tier.LT5.points());
        assertEquals(2, Tier.HT5.points());
        assertEquals(3, Tier.LT4.points());
        assertEquals(4, Tier.HT4.points());
        assertEquals(6, Tier.LT3.points());
        assertEquals(10, Tier.HT3.points());
        assertEquals(20, Tier.LT2.points());
        assertEquals(30, Tier.HT2.points());
        assertEquals(45, Tier.LT1.points());
        assertEquals(60, Tier.HT1.points());
    }

    @Test
    void promotingToDefeatedHigherTierAndDemotingOneStepWork() {
        assertEquals(Tier.HT3, Tier.LT3.promoteTo(Tier.HT3));
        assertEquals(Tier.HT5, Tier.LT5.promoteOne());
        assertEquals(Tier.LT3, Tier.HT3.demoteOne());
        assertEquals(Tier.LT1, Tier.HT1.demoteOne());
        assertEquals(Tier.LT5, Tier.LT5.demoteOne());
    }

    @Test
    void averageUsesNearestConfiguredTier() {
        assertEquals(Tier.HT3, Tier.fromAverage(9.0));
        assertEquals(Tier.LT2, Tier.fromAverage(15.0));
        assertEquals(Tier.LT5, Tier.fromAverage(1.0));
    }
}

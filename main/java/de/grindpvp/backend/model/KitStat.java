package de.grindpvp.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "kit_stats")
@IdClass(KitStatId.class)
public class KitStat {
    @Id
    @ManyToOne(optional = false)
    @JoinColumn(name = "player_id", nullable = false)
    private PlayerProfile player;

    @Id
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Kit kit;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private Tier tier = Tier.LT5;

    @Column(nullable = false)
    private int wins;

    @Column(nullable = false)
    private int losses;

    protected KitStat() {
    }

    public KitStat(PlayerProfile player, Kit kit) {
        this.player = player;
        this.kit = kit;
    }

    public PlayerProfile getPlayer() {
        return player;
    }

    public Kit getKit() {
        return kit;
    }

    public Tier getTier() {
        return tier;
    }

    public void setTier(Tier tier) {
        this.tier = tier;
    }

    public int getWins() {
        return wins;
    }

    public void addWin() {
        wins++;
    }

    public int getLosses() {
        return losses;
    }

    public void addLoss() {
        losses++;
    }
}

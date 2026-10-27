package de.grindpvp.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "match_reports")
public class MatchReport {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Kit kit;

    @Column(nullable = false)
    private UUID winnerId;

    @Column(nullable = false, length = 16)
    private String winnerName;

    @Column(nullable = false)
    private UUID loserId;

    @Column(nullable = false, length = 16)
    private String loserName;

    @Column(nullable = false)
    private int matchesPlayed;

    @Column(nullable = false, length = 500)
    private String report;

    @Column(nullable = false)
    private Instant submittedAt = Instant.now();

    protected MatchReport() {
    }

    public MatchReport(
            Kit kit, UUID winnerId, String winnerName, UUID loserId,
            String loserName, int matchesPlayed, String report
    ) {
        this.kit = kit;
        this.winnerId = winnerId;
        this.winnerName = winnerName;
        this.loserId = loserId;
        this.loserName = loserName;
        this.matchesPlayed = matchesPlayed;
        this.report = report;
    }
}

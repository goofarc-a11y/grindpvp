package de.grindpvp.backend.model;

import java.util.Arrays;

public enum Tier {
    LT5(1),
    HT5(2),
    LT4(3),
    HT4(4),
    LT3(6),
    HT3(10),
    LT2(20),
    HT2(30),
    LT1(45),
    HT1(60);

    private final int points;

    Tier(int points) {
        this.points = points;
    }

    public int points() {
        return points;
    }

    public Tier promoteTo(Tier defeatedTier) {
        return defeatedTier.points > points ? defeatedTier : this;
    }

    public Tier promoteOne() {
        return Arrays.stream(values())
                .filter(tier -> tier.points > points)
                .min((left, right) -> Integer.compare(left.points, right.points))
                .orElse(this);
    }

    public Tier demoteOne() {
        return Arrays.stream(values())
                .filter(tier -> tier.points < points)
                .max((left, right) -> Integer.compare(left.points, right.points))
                .orElse(this);
    }

    public static Tier fromAverage(double points) {
        return Arrays.stream(values())
                .min((left, right) -> {
                    int distance = Double.compare(Math.abs(left.points - points),
                            Math.abs(right.points - points));
                    return distance != 0 ? distance : Integer.compare(right.points, left.points);
                })
                .orElse(LT5);
    }
}

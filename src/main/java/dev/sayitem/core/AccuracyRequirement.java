package dev.sayitem.core;

public final class AccuracyRequirement {
    public static final int DEFAULT = 90, MINIMUM = 60, MAXIMUM = 100;
    private AccuracyRequirement() {}
    public static int clamp(int percent) { return Math.clamp(percent, MINIMUM, MAXIMUM); }
    public static boolean valid(int percent) { return percent >= MINIMUM && percent <= MAXIMUM; }
    public static boolean accepts(double score, int percent) {
        if (!valid(percent)) throw new IllegalArgumentException("Accuracy must be between 60% and 100%");
        return score + 1e-12 >= percent / 100.0;
    }
}

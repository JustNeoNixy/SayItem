package dev.sayitem.core;

public final class TimerLimit {
    public static final int DEFAULT = 15, MINIMUM = 10, MAXIMUM = 30;
    private TimerLimit() {}
    public static boolean valid(int seconds) { return seconds >= MINIMUM && seconds <= MAXIMUM; }
    public static int clamp(int seconds) { return Math.clamp(seconds, MINIMUM, MAXIMUM); }
    public static long millis(int seconds) {
        if (!valid(seconds)) throw new IllegalArgumentException("Timer must be between 10 and 30 seconds");
        return seconds * 1000L;
    }
}

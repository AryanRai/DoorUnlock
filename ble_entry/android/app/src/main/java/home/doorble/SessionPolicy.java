package home.doorble;

/** Timing shared with host-side tests; no Android dependencies. */
public final class SessionPolicy {
    public static final long NORMAL_MS = 3000;
    public static final long CALIBRATION_MS = 11000;
    public static final long HARD_LIMIT_MS = 13000;
    public static final long MIN_SCAN_INTERVAL_MS = 8000;
    // Keep continuous away evidence long enough to rearm; never join evidence across links.
    public static long sessionBudget(boolean calibrating, boolean confirmingAway) {
        return calibrating ? CALIBRATION_MS : confirmingAway ? 5500 : NORMAL_MS;
    }
    public static long retryDelay(int failures, int jitter) {
        return Math.min(15000L, 1000L << Math.min(4, Math.max(0, failures)))
                + Math.floorMod(jitter, 2001);
    }
    public static long scanDelay(long now, long lastScan, long desired) {
        return Math.max(desired, MIN_SCAN_INTERVAL_MS - (now - lastScan));
    }
}

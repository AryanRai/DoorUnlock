package home.doorble;

/** Separate from authentication: suppress repeated approach feedback and expire stale UI. */
public final class FeedbackPolicy {
    private long previous=-10000;
    public boolean begin(long now){if(now-previous<10000)return false;previous=now;return true;}
    public static boolean fresh(long at,long now){return at>0&&now>=at&&now-at<15000;}
}

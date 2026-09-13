package home.doorble;

/** Presentation only. Signed probe samples drive this; it never authorizes or rearms entry. */
public final class ProximityPolicy {
    public static final String CLOSE="CLOSE",OUT="OUT";
    public static final long LOST_MS=25000;
    private boolean nearby;
    private long lastSeen=-1,candidateAt=-1;
    private int candidate=0,samples=0;
    public String observe(int rssi,int unlockThreshold,long now){
        if(unlockThreshold==0||rssi>=0||rssi< -127)return null;
        if(lastSeen>=0&&(now<lastSeen||now-lastSeen>8000))resetCandidate();
        lastSeen=now;
        int direction=!nearby&&rssi>=unlockThreshold-14?1:nearby&&rssi<unlockThreshold-20?-1:0;
        if(direction==0){resetCandidate();return null;}
        if(candidate!=direction){candidate=direction;candidateAt=now;samples=1;return null;}
        samples++;
        if(samples>=2&&now-candidateAt>=(direction==1?500:2000)){
            nearby=direction==1;resetCandidate();return nearby?CLOSE:OUT;
        }
        return null;
    }
    public String tick(long now){if(nearby&&lastSeen>=0&&now-lastSeen>=LOST_MS){nearby=false;resetCandidate();return OUT;}return null;}
    public void reset(){nearby=false;lastSeen=-1;resetCandidate();}
    public boolean nearby(){return nearby;}
    private void resetCandidate(){candidate=0;samples=0;candidateAt=-1;}
}

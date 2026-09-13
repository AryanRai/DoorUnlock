package home.doorble;
public class SessionPolicyTest {
    public static void main(String[] args) {
        for(int failures=0;failures<8;failures++)for(int j=0;j<=2000;j++){
            long delay=SessionPolicy.retryDelay(failures,j);
            if(delay<1000||delay>17000)throw new AssertionError("Unbounded retry");
            if(failures>0&&delay<SessionPolicy.retryDelay(failures-1,j))throw new AssertionError("Backoff decreased");
        }
        if(SessionPolicy.scanDelay(1000,0,0)!=7000)throw new AssertionError("Android scan throttle");
        if(SessionPolicy.scanDelay(20000,0,5000)!=5000)throw new AssertionError("Minimum release period");
        if(SessionPolicy.retryDelay(0,0)==SessionPolicy.retryDelay(0,2000))throw new AssertionError("Missing jitter");
        if(SessionPolicy.NORMAL_MS!=3000||SessionPolicy.CALIBRATION_MS>=SessionPolicy.HARD_LIMIT_MS||SessionPolicy.HARD_LIMIT_MS>=15000)throw new AssertionError("Session budget invalid");
        if(SessionPolicy.retryDelay(0,0)!=1000||SessionPolicy.retryDelay(0,2000)!=3000)throw new AssertionError("Normal retry must be 1-3 seconds");
        if(SessionPolicy.sessionBudget(false,false)!=3000||SessionPolicy.sessionBudget(false,true)<5000||SessionPolicy.sessionBudget(true,false)!=11000)throw new AssertionError("Calibration/rearm budget");
        System.out.println("PASS: bounded randomized retry, contention backoff, scan throttle, session budgets");
    }
}

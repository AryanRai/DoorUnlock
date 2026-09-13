package home.doorble;
public class ProximityPolicyTest {
    static void eq(String want,String got){if(!java.util.Objects.equals(want,got))throw new AssertionError("Expected "+want+", got "+got);}
    public static void main(String[] args){
        ProximityPolicy p=new ProximityPolicy();
        eq(null,p.observe(-80,-42,0));eq(null,p.tick(30000)); // No departure before arrival.
        eq(null,p.observe(-53,-42,31000));eq(ProximityPolicy.CLOSE,p.observe(-52,-42,31500));
        eq(null,p.observe(-60,-42,32000)); // Hysteresis keeps nearby state.
        eq(null,p.observe(-65,-42,32500));eq(null,p.observe(-50,-42,33000)); // A single weak reading cannot depart.
        eq(null,p.observe(-66,-42,34000));eq(null,p.observe(-65,-42,35000));eq(ProximityPolicy.OUT,p.observe(-67,-42,36000));
        eq(null,p.observe(-75,-42,37000));eq(null,p.tick(70000));
        eq(null,p.observe(-50,-42,71000));eq(ProximityPolicy.CLOSE,p.observe(-51,-42,72000));
        eq(null,p.tick(82000)); // Normal household connection gap must not depart.
        eq(null,p.observe(-49,-42,84000));eq(null,p.tick(108999));eq(ProximityPolicy.OUT,p.tick(109000));eq(null,p.tick(120000));
        p.reset();eq(null,p.observe(-50,-42,130000));eq(null,p.observe(-49,-42,140000)); // Stale evidence cannot confirm arrival.
        eq(ProximityPolicy.CLOSE,p.observe(-49,-42,140500));
        p.reset();eq(null,p.tick(999999));eq(null,p.observe(-30,0,999999));
        System.out.println("PASS: proximity transitions, hysteresis, jitter, session gaps, signal loss and reset");
    }
}

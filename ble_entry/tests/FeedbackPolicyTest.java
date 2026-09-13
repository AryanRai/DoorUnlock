package home.doorble;
public class FeedbackPolicyTest {
    public static void main(String[] args){
        FeedbackPolicy policy=new FeedbackPolicy();
        if(!policy.begin(100)||policy.begin(200)||policy.begin(10099)||!policy.begin(10100))throw new AssertionError("Approach feedback spam gate");
        if(!FeedbackPolicy.fresh(100,101)||FeedbackPolicy.fresh(0,1)||FeedbackPolicy.fresh(100,15100)||FeedbackPolicy.fresh(1000,1))throw new AssertionError("Stale feedback after delay/reboot");
        System.out.println("PASS: feedback rate limit and stale/reboot state rejection");
    }
}

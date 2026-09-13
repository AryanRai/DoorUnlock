package home.doorble;
import javax.crypto.spec.SecretKeySpec;
import java.util.*;

public class ProtocolTest {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static void rejected(Runnable r){boolean failed=false;try{r.run();}catch(IllegalArgumentException e){failed=true;}check(failed,"Expected rejection");}
    public static void main(String[] args) throws Exception {
        byte[] raw=new byte[32];for(int i=0;i<32;i++)raw[i]=(byte)i;
        var key=new SecretKeySpec(raw,"HmacSHA256");String board="123456abcdef",phone="a1b2c3d4",nonce="00112233445566778899aabbccddeeff";
        String request=Protocol.request(key,board,phone,nonce,"U","0");
        check(request.equals("a1b2c3d4|00112233445566778899aabbccddeeff|U|0|"+args[0]),"Cross-language HMAC vector failed");
        String msg="doorble-result-v1|"+board+"|"+phone+"|"+nonce+"|WOULD_UNLOCK|-42";
        String response="WOULD_UNLOCK|-42|"+Protocol.mac(key,msg);
        check(Protocol.verify(key,board,phone,nonce,response)[0].equals("WOULD_UNLOCK"),"Signed response rejected");
        for(String bad:List.of(response.replace("-42","-20"),"WOULD_UNLOCK|-42|",response.replace("WOULD_UNLOCK","PROBE"))){boolean failed=false;try{Protocol.verify(key,board,phone,nonce,bad);}catch(Exception e){failed=true;}check(failed,"Tampered response accepted");}
        boolean replay=false;try{Protocol.verify(key,board,phone,"ffeeddccbbaa99887766554433221100",response);}catch(Exception e){replay=true;}check(replay,"Response replay accepted");
        check(Protocol.threshold(Collections.nCopies(15,-40),Collections.nCopies(15,-70))==-55,"Threshold calculation");
        rejected(()->Protocol.threshold(Collections.nCopies(15,-50),Collections.nCopies(15,-55)));
        rejected(()->Protocol.threshold(Collections.nCopies(14,-40),Collections.nCopies(15,-70)));
        rejected(()->Protocol.threshold(Collections.nCopies(15,-80),Collections.nCopies(15,-100)));
        rejected(()->Protocol.unhex("zz"));
        System.out.println("PASS: HMAC vector, signed response, tampering, response replay, calibration separation/count/range, malformed key");
    }
}

package home.doorble;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
public class LegacyProtocolTest {
    public static void main(String[] args)throws Exception{
        byte[] secret="12345678901234567890".getBytes(StandardCharsets.US_ASCII);
        SecretKeySpec key=new SecretKeySpec(secret,"HmacSHA1");
        long[] times={59,1111111109,1111111111,1234567890,2000000000,20000000000L};
        String[] expected={"287082","081804","050471","005924","279037","353130"};
        for(int i=0;i<times.length;i++)if(!LegacyProtocol.code(key,times[i]).equals(expected[i]))throw new AssertionError("RFC 6238 vector "+i);
        if(!Arrays.equals(secret,LegacyProtocol.base32("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ")))throw new AssertionError("Base32 decoding");
        if(!LegacyProtocol.testRequest("001234").equals("{ Action: Test, TOTP: 001234 }"))throw new AssertionError("Legacy wire format");
        for(String invalid:new String[]{"12345","1234567","12 456","123456, Action: Unlock","１２３４５６"}){
            try{LegacyProtocol.testRequest(invalid);throw new AssertionError("Invalid code accepted");}catch(IllegalArgumentException expectedError){}
        }
        try{LegacyProtocol.base32("not-a-secret!");throw new AssertionError("Malformed key accepted");}catch(IllegalArgumentException expectedError){}
        System.out.println("PASS: six RFC 6238 vectors, Base32 key, leading zeros and safe legacy Test payload");
    }
}

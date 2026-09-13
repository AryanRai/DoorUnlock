package home.doorble;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.util.Locale;

/** Dad's existing ESP8266 protocol. This preview has no network permission or transport. */
public final class LegacyProtocol {
    private LegacyProtocol(){}
    public static String testRequest(String code){
        if(code==null||!code.matches("[0-9]{6}"))throw new IllegalArgumentException("Enter a six-digit code");
        // The legacy parser expects unquoted labels rather than JSON.
        return "{ Action: Test, TOTP: "+code+" }";
    }
    public static byte[] base32(String source){
        String s=source.replace(" ","").replace("-","").toUpperCase(Locale.ROOT);
        if(!s.matches("[A-Z2-7]{16,103}={0,6}"))throw new IllegalArgumentException("Enter a valid Authenticator setup key");
        s=s.replace("=","");java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();int value=0,bits=0;
        for(char c:s.toCharArray()){int digit=c>='A'&&c<='Z'?c-'A':c-'2'+26;value=(value<<5)|digit;bits+=5;if(bits>=8){bits-=8;out.write((value>>bits)&255);}}
        if(bits>0&&(value&((1<<bits)-1))!=0)throw new IllegalArgumentException("Invalid setup key padding");
        return out.toByteArray();
    }
    public static String code(java.security.Key key,long seconds)throws Exception{
        if(seconds<0)throw new IllegalArgumentException("Phone clock is invalid");
        Mac mac=Mac.getInstance("HmacSHA1");mac.init(key);byte[] digest=mac.doFinal(ByteBuffer.allocate(8).putLong(seconds/30).array());
        int i=digest[digest.length-1]&15;int value=((digest[i]&127)<<24)|((digest[i+1]&255)<<16)|((digest[i+2]&255)<<8)|(digest[i+3]&255);
        return String.format(Locale.ROOT,"%06d",value%1000000);
    }
}

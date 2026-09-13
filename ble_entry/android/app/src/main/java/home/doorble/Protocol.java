package home.doorble;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import javax.crypto.Mac;
import java.security.Key;

public final class Protocol {
    public static final UUID SERVICE = UUID.fromString("9e8b0001-7e4a-4d70-a7a1-9b771da83d01");
    public static final UUID CHALLENGE = UUID.fromString("9e8b0002-7e4a-4d70-a7a1-9b771da83d01");
    public static final UUID REQUEST = UUID.fromString("9e8b0003-7e4a-4d70-a7a1-9b771da83d01");
    public static final UUID RESULT = UUID.fromString("9e8b0004-7e4a-4d70-a7a1-9b771da83d01");
    public static byte[] unhex(String s) {
        if(!s.matches("[0-9a-f]+") || s.length()%2!=0) throw new IllegalArgumentException("Invalid key");
        byte[] b=new byte[s.length()/2]; for(int i=0;i<b.length;i++)b[i]=(byte)Integer.parseInt(s.substring(i*2,i*2+2),16); return b;
    }
    public static String mac(Key key,String message) throws Exception {
        Mac mac=Mac.getInstance("HmacSHA256");mac.init(key);StringBuilder out=new StringBuilder();
        for(byte b:mac.doFinal(message.getBytes(StandardCharsets.UTF_8)))out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString();
    }
    public static String request(Key key,String board,String phone,String nonce,String op,String value) throws Exception {
        return phone+"|"+nonce+"|"+op+"|"+value+"|"+mac(key,"doorble-v1|"+board+"|"+phone+"|"+nonce+"|"+op+"|"+value);
    }
    public static String[] verify(Key key,String board,String phone,String nonce,String response) throws Exception {
        String[] p=response.split("\\|",-1);if(p.length!=3||!p[2].matches("[0-9a-f]{64}"))throw new SecurityException("Board rejected authentication");
        String expected=mac(key,"doorble-result-v1|"+board+"|"+phone+"|"+nonce+"|"+p[0]+"|"+p[1]);
        if(!MessageDigest.isEqual(unhex(expected),unhex(p[2])))throw new SecurityException("Board signature invalid");
        int r=Integer.parseInt(p[1]);if(r < -127||r>0)throw new SecurityException("Invalid RSSI");return p;
    }
    public static int threshold(List<Integer> near,List<Integer> away) {
        if(near.size()<15||away.size()<15)throw new IllegalArgumentException("Need 15 samples at both positions");
        List<Integer> n=new ArrayList<>(near),a=new ArrayList<>(away);Collections.sort(n);Collections.sort(a);
        int weakNear=n.get(n.size()/5),strongAway=a.get(a.size()*4/5);
        if(weakNear-strongAway<10)throw new IllegalArgumentException("Near and away signals overlap. Move farther away and retry.");
        int t=(weakNear+strongAway)/2;
        if(t < -75||t > -25)throw new IllegalArgumentException("Signal outside useful calibration range; reposition board.");return t;
    }
}

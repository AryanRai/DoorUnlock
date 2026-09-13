package home.doorble;
import android.content.*;
import android.security.keystore.*;
import java.security.*;
import javax.crypto.spec.SecretKeySpec;
import java.util.Arrays;

public final class Keys {
    public static SharedPreferences prefs(Context c){return c.getSharedPreferences("entry",Context.MODE_PRIVATE);}
    public static void enroll(Context c,String code) throws Exception {
        String[] p=code.trim().split(":");
        if(p.length!=4||!p[0].equals("doorble1")||!p[1].matches("[0-9a-f]{12}")||!p[2].matches("[0-9a-f]{8}")||!p[3].matches("[0-9a-f]{64}"))throw new IllegalArgumentException("Paste the complete setup code from the board page");
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);
        String alias="doorble-"+p[1]+"-"+p[2],old=prefs(c).getString("alias","");byte[] bytes=Protocol.unhex(p[3]);
        try { ks.setEntry(alias,new KeyStore.SecretKeyEntry(new SecretKeySpec(bytes,"HmacSHA256")),new KeyProtection.Builder(KeyProperties.PURPOSE_SIGN|KeyProperties.PURPOSE_VERIFY).setDigests(KeyProperties.DIGEST_SHA256).build()); } finally { Arrays.fill(bytes,(byte)0); }
        prefs(c).edit().clear().putString("board",p[1]).putString("phone",p[2]).putString("alias",alias).commit();
        if(!old.isEmpty()&&!old.equals(alias))ks.deleteEntry(old);
    }
    public static Key key(Context c) throws Exception {KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);Key k=ks.getKey(prefs(c).getString("alias",""),null);if(k==null)throw new IllegalStateException("Enroll this phone first");return k;}
}

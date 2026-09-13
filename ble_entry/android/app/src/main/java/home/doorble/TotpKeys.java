package home.doorble;

import android.security.keystore.*;
import java.security.*;
import java.util.Arrays;
import javax.crypto.spec.SecretKeySpec;

public final class TotpKeys {
    private static final String ALIAS="door-totp-v1";
    public static void save(String source)throws Exception{
        byte[] bytes=LegacyProtocol.base32(source);
        try{KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
            store.setEntry(ALIAS,new KeyStore.SecretKeyEntry(new SecretKeySpec(bytes,"HmacSHA1")),
                new KeyProtection.Builder(KeyProperties.PURPOSE_SIGN).setDigests(KeyProperties.DIGEST_SHA1).build());
        }finally{Arrays.fill(bytes,(byte)0);}
    }
    public static java.security.Key key()throws Exception{KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);return store.getKey(ALIAS,null);}
    public static void remove()throws Exception{KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);store.deleteEntry(ALIAS);}
}

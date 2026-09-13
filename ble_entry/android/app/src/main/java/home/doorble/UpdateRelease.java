package home.doorble;

import java.net.URI;
import org.json.JSONObject;

/** Only this repository's release APKs can be offered. No arbitrary download URLs. */
public final class UpdateRelease {
    public static final String REPOSITORY="https://github.com/AryanRai/DoorUnlock";
    public static final String FEED=REPOSITORY+"/releases/latest/download/door-update.json";
    public final int versionCode,minSdk;
    public final String versionName,notes,apkUrl,sha256;
    public final long bytes;
    public UpdateRelease(String json)throws Exception{
        JSONObject o=new JSONObject(json);
        if(o.getInt("schema")!=1||!"home.doorble".equals(o.getString("packageName")))throw new IllegalArgumentException("Wrong update feed");
        versionCode=o.getInt("versionCode");minSdk=o.getInt("minSdk");versionName=o.getString("versionName");notes=o.getString("notes");apkUrl=o.getString("apkUrl");sha256=o.getString("sha256");bytes=o.getLong("bytes");
        URI u=new URI(apkUrl);
        if(versionCode<=0||minSdk<26||versionName.length()>80||notes.length()>6000||bytes<10000||bytes>50000000||!sha256.matches("[0-9a-f]{64}")||
            !"https".equals(u.getScheme())||!"github.com".equals(u.getHost())||u.getPort()!=-1||u.getUserInfo()!=null||u.getQuery()!=null||u.getFragment()!=null||
            !u.getRawPath().matches("/AryanRai/DoorUnlock/releases/download/door-android-[A-Za-z0-9._-]+/Door-[A-Za-z0-9._-]+\\.apk"))throw new IllegalArgumentException("Invalid update metadata");
    }
    public boolean newerThan(long installed,int sdk){return versionCode>installed&&minSdk<=sdk;}
}

package home.doorble;
import android.content.Context;
import android.content.SharedPreferences;

public final class DoorSettings {
    private DoorSettings(){}
    public static SharedPreferences prefs(Context c){return c.getSharedPreferences("door-settings",Context.MODE_PRIVATE);}
    public static boolean enabled(Context c){return prefs(c).getBoolean("background",false);}
    public static void enabled(Context c,boolean value){prefs(c).edit().putBoolean("background",value).apply();DoorWidget.updateAll(c);}
}

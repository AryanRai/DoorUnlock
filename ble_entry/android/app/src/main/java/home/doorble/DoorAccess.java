package home.doorble;
import android.app.*;
import android.appwidget.AppWidgetManager;
import android.content.*;
import android.content.pm.*;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.widget.Toast;
import java.util.*;

public final class DoorAccess {
    public static final String[] LABELS={"Door button","Door code","Door nearby","Door face"};
    private static ShortcutInfo shortcut(Context c,int n){return new ShortcutInfo.Builder(c,"door-method-"+n).setShortLabel(LABELS[n]).setLongLabel(LABELS[n]+" · dry run").setIcon(Icon.createWithResource(c,R.drawable.ic_door)).setIntent(DoorIntents.open(c,n)).build();}
    public static void install(Context c){try{List<ShortcutInfo> list=new ArrayList<>();for(int i=0;i<4;i++)list.add(shortcut(c,i));c.getSystemService(ShortcutManager.class).setDynamicShortcuts(list);}catch(RuntimeException ignored){}}
    public static void pinShortcut(Context c,int n){ShortcutManager m=c.getSystemService(ShortcutManager.class);if(m.isRequestPinShortcutSupported())m.requestPinShortcut(shortcut(c,n),null);else Toast.makeText(c,"Long-press Door in your launcher to find its shortcuts",Toast.LENGTH_LONG).show();}
    public static void pinWidget(Context c){AppWidgetManager m=AppWidgetManager.getInstance(c);if(m.isRequestPinAppWidgetSupported())m.requestPinAppWidget(new ComponentName(c,DoorWidget.class),null,null);else Toast.makeText(c,"Add Door from your launcher's Widgets menu",Toast.LENGTH_LONG).show();}
    public static void addTile(Activity a,int n){
        Class<?>[] types={DoorTile.Tap.class,DoorTile.Code.class,DoorTile.class,DoorTile.Face.class};
        if(Build.VERSION.SDK_INT>=33)a.getSystemService(StatusBarManager.class).requestAddTileService(new ComponentName(a,types[n]),LABELS[n],Icon.createWithResource(a,R.drawable.ic_door),a.getMainExecutor(),result->{});
        else Toast.makeText(a,"Edit Quick Settings and drag a Door tile into your panel",Toast.LENGTH_LONG).show();
    }
}

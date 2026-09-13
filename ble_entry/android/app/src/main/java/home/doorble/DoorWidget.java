package home.doorble;
import android.appwidget.*;
import android.content.*;
import android.widget.RemoteViews;

public class DoorWidget extends AppWidgetProvider {
    public static void updateAll(Context c){AppWidgetManager m=AppWidgetManager.getInstance(c);int[] ids=m.getAppWidgetIds(new ComponentName(c,DoorWidget.class));update(c,m,ids);}
    private static void update(Context c,AppWidgetManager manager,int[] ids){
        for(int id:ids){RemoteViews v=new RemoteViews(c.getPackageName(),R.layout.door_widget);v.setTextViewText(R.id.widget_status,DoorSettings.enabled(c)?"BLE listening · dry run":"BLE paused · dry run");
            int[] buttons={R.id.widget_tap,R.id.widget_code,R.id.widget_ble,R.id.widget_face};for(int i=0;i<4;i++)v.setOnClickPendingIntent(buttons[i],DoorIntents.pending(c,i));manager.updateAppWidget(id,v);}
    }
    @Override public void onUpdate(Context c,AppWidgetManager manager,int[] ids){update(c,manager,ids);}
}

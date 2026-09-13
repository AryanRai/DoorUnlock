package home.doorble;
import android.content.*;

/** Runs after normal boot/first unlock or package update, only if the user enabled BLE. */
public class ResumeReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent intent){String a=intent.getAction();if(!Intent.ACTION_BOOT_COMPLETED.equals(a)&&!Intent.ACTION_MY_PACKAGE_REPLACED.equals(a))return;
        if(DoorSettings.enabled(c))try{c.startForegroundService(new Intent(c,EntryService.class).setAction("RESUME"));}catch(RuntimeException e){DoorSettings.prefs(c).edit().putString("resume_error","Android prevented restart. Open Door and tap Start.").apply();}
        DoorWidget.updateAll(c);
    }
}

package home.doorble;
import android.bluetooth.le.*;
import android.content.*;
import java.util.ArrayList;

public class BleWakeReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent i){if(!DoorSettings.enabled(c))return;
        DoorSettings.prefs(c).edit().putInt("scan_wakes",DoorSettings.prefs(c).getInt("scan_wakes",0)+1).apply();
        int error=i.getIntExtra(BluetoothLeScanner.EXTRA_ERROR_CODE,0);
        ArrayList<ScanResult> results=i.getParcelableArrayListExtra(BluetoothLeScanner.EXTRA_LIST_SCAN_RESULT);
        Intent wake=new Intent(c,EntryService.class).setAction("BLE_WAKE").putExtra("scan_error",error);
        if(results!=null&&!results.isEmpty())wake.putExtra("device",results.get(0).getDevice());
        try{c.startForegroundService(wake);}catch(RuntimeException e){DoorSettings.prefs(c).edit().putString("resume_error","Background Bluetooth needs attention. Open Door and tap Start.").apply();}
    }
}

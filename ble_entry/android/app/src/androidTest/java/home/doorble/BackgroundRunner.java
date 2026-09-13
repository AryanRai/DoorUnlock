package home.doorble;

import android.app.*;
import android.content.*;
import android.os.*;
import android.service.notification.StatusBarNotification;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Physical tests keep enrollment/calibration intact and never call a door endpoint. */
public class BackgroundRunner extends PreviewRunner {
    protected void background(boolean screenOff)throws Exception{
        Bundle result=new Bundle();Context c=getTargetContext();
        String phone=Keys.prefs(c).getString("phone","");int calibration=Keys.prefs(c).getInt("threshold",0);
        MainActivity activity=(MainActivity)startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        runOnMainSync(()->c.startForegroundService(new Intent(c,EntryService.class).setAction("START")));
        CountDownLatch connected=new CountDownLatch(1);EntryService[] service={null};
        ServiceConnection connection=new ServiceConnection(){public void onServiceConnected(ComponentName n,IBinder b){service[0]=((EntryService.LocalBinder)b).service();connected.countDown();}public void onServiceDisconnected(ComponentName n){}};
        c.bindService(new Intent(c,EntryService.class),connection,Context.BIND_AUTO_CREATE);
        if(!connected.await(8,TimeUnit.SECONDS))throw new IllegalStateException("Service binding timed out");
        try{
            Thread.sleep(2500);runOnMainSync(()->activity.moveTaskToBack(true));Thread.sleep(1000);
            int before=EntryService.authenticatedResponses;
            if(screenOff)getUiAutomation().executeShellCommand("input keyevent 223").close();
            Thread.sleep(18000);
            if(MainActivity.visible||EntryService.authenticatedResponses<=before)throw new IllegalStateException("No authenticated responses while backgrounded");
            result.putInt("background_authenticated_responses",EntryService.authenticatedResponses-before);
            if(screenOff){if(c.getSystemService(PowerManager.class).isInteractive())throw new IllegalStateException("Screen was not off");result.putString("screen_off","PASS");}
            int prior=EntryService.authenticatedResponses;
            runOnMainSync(()->{service[0].releaseSlot("Passive discovery regression",false);service[0].h.removeCallbacks(service[0].scanTask);service[0].passiveScan();});
            Thread.sleep(12000);
            if(EntryService.authenticatedResponses<=prior)throw new IllegalStateException("Passive discovery failed: "+EntryService.currentStatus+"; wakes="+DoorSettings.prefs(c).getInt("scan_wakes",0));
            result.putString("passive_discovery","PASS: PendingIntent scan wakes and authenticates");
            // This only exercises feedback presentation; it is explicitly not a BLE success.
            runOnMainSync(()->DoorFeedback.publish(c,DoorFeedback.VERIFYING,"UI test only · no door operation"));
            Thread.sleep(600);
            java.lang.reflect.Field overlay=FloatingDoor.class.getDeclaredField("root");overlay.setAccessible(true);
            if(screenOff){
                if(overlay.get(null)!=null)throw new IllegalStateException("Overlay appeared while screen off");
                result.putString("locked_feedback","PASS: notification only; overlay suppressed");
            }else if(android.provider.Settings.canDrawOverlays(c)&&!c.getSystemService(KeyguardManager.class).isKeyguardLocked()){
                runOnMainSync(()->{DoorSettings.prefs(c).edit().putBoolean("floating",true).apply();DoorFeedback.publish(c,DoorFeedback.VERIFYING,"UI test only · no door operation");});
                Thread.sleep(500);captureOverlay(overlay,"verifying");
                runOnMainSync(()->DoorFeedback.publish(c,DoorFeedback.SUCCESS,"UI test animation · no BLE acceptance"));
                Thread.sleep(500);captureOverlay(overlay,"success");
                runOnMainSync(FloatingDoor::dismiss);
                if(overlay.get(null)!=null)throw new IllegalStateException("Overlay did not dismiss");
                result.putString("floating_card","PASS: verifying/result cards rendered and dismissed; UI fixture only");
            }
            boolean found=false;for(StatusBarNotification n:c.getSystemService(NotificationManager.class).getActiveNotifications())if(n.getId()==DoorFeedback.ALERT_ID)found=true;
            if(!found)throw new IllegalStateException("Background approach notification missing");
            result.putString("notifications","PASS: approach notification posted while backgrounded");
            runOnMainSync(()->DoorFeedback.clear(c));
            if(!phone.equals(Keys.prefs(c).getString("phone",""))||calibration!=Keys.prefs(c).getInt("threshold",0))throw new IllegalStateException("Enrollment/calibration changed");
            result.putString("enrollment","PASS: original enrollment and calibration preserved");
        }finally{runOnMainSync(()->DoorFeedback.clear(c));c.unbindService(connection);if(screenOff)getUiAutomation().executeShellCommand("input keyevent 224").close();}
        finish(Activity.RESULT_OK,result);
    }
    private void captureOverlay(java.lang.reflect.Field field,String name)throws Exception{
        Exception[] error={null};
        runOnMainSync(()->{try{
            android.view.View view=(android.view.View)field.get(null);
            if(view==null||!view.isAttachedToWindow())throw new IllegalStateException("Floating card missing");
            android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(view.getWidth(),view.getHeight(),android.graphics.Bitmap.Config.ARGB_8888);
            view.draw(new android.graphics.Canvas(bitmap));
            try(java.io.FileOutputStream stream=new java.io.FileOutputStream(new java.io.File(getTargetContext().getFilesDir(),"preview-floating-"+name+".png"))){bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,stream);}bitmap.recycle();
        }catch(Exception e){error[0]=e;}});
        if(error[0]!=null)throw error[0];
    }
}

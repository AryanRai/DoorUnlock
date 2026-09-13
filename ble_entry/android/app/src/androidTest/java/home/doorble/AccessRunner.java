package home.doorble;

import android.app.*;
import android.content.*;
import android.content.pm.ShortcutManager;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.Flow;

/** Verifies Android entry-point routing and widget inflation without pinning user UI. */
public class AccessRunner extends BackgroundRunner {
    protected void access()throws Exception{
        Context c=getTargetContext();Bundle out=new Bundle();
        MainActivity activity=(MainActivity)startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        runOnMainSync(()->DoorFeedback.clear(c));
        if(c.getSystemService(ShortcutManager.class).getDynamicShortcuts().size()!=4)throw new IllegalStateException("Expected four launcher shortcuts");
        java.lang.reflect.Field selected=MainActivity.class.getDeclaredField("selected");selected.setAccessible(true);
        for(int i=0;i<4;i++){
            final int method=i;runOnMainSync(()->{try{DoorIntents.pending(c,method).send();}catch(PendingIntent.CanceledException e){throw new RuntimeException(e);}});
            Thread.sleep(500);waitForIdleSync();
            if(selected.getInt(activity)!=i)throw new IllegalStateException("Wrong destination for method "+i);
        }
        runOnMainSync(()->{
            View widget=new RemoteViews(c.getPackageName(),R.layout.door_widget).apply(c,new FrameLayout(c));
            for(int id:new int[]{R.id.widget_tap,R.id.widget_code,R.id.widget_ble,R.id.widget_face})if(widget.findViewById(id)==null)throw new IllegalStateException("Widget button missing");
        });
        class Controls extends DoorControls {void attach(Context context){attachBaseContext(context);}}
        Controls controls=new Controls();controls.attach(c);List<android.service.controls.Control> entries=new ArrayList<>();
        controls.createPublisherFor(Arrays.asList("door-0","door-1","door-2","door-3")).subscribe(new Flow.Subscriber<android.service.controls.Control>(){
            public void onSubscribe(Flow.Subscription s){s.request(4);}public void onNext(android.service.controls.Control value){entries.add(value);}public void onError(Throwable t){throw new IllegalStateException(t);}public void onComplete(){}
        });
        if(entries.size()!=4)throw new IllegalStateException("Device controls missing");
        for(android.service.controls.Control entry:entries)if(!entry.isAuthRequired()||entry.getAppIntent()==null)throw new IllegalStateException("Invalid lock-screen control");
        runOnMainSync(activity::showBackgroundSetup);Thread.sleep(600);capture(activity,"background",true);
        runOnMainSync(()->c.startService(new Intent(c,EntryService.class).setAction("STOP")));Thread.sleep(700);
        if(DoorSettings.enabled(c))throw new IllegalStateException("Stop did not clear opt-in");
        runOnMainSync(()->{new ResumeReceiver().onReceive(c,new Intent(Intent.ACTION_BOOT_COMPLETED));c.startService(new Intent(c,EntryService.class).setAction("RESUME"));});Thread.sleep(700);
        if(DoorSettings.enabled(c))throw new IllegalStateException("Resume re-enabled a stopped service");
        runOnMainSync(()->c.startForegroundService(new Intent(c,EntryService.class).setAction("START")));Thread.sleep(700);
        if(!DoorSettings.enabled(c))throw new IllegalStateException("Explicit start did not restore checking");
        out.putString("stop_resume","PASS: Stop persists through resume/boot callback; explicit Start restores opt-in (no physical reboot)");
        out.putString("access","PASS: four PendingIntent destinations, launcher shortcuts, widget inflation, authenticated Device controls");
        out.putString("scope","OS tile/widget placement and lock-screen presentation still require user setup");
        finish(Activity.RESULT_OK,out);
    }
}

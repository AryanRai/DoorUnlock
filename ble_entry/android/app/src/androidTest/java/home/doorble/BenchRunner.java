package home.doorble;

import android.app.Instrumentation;
import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;

/** USB development harness; only packaged in the separate test APK. */
public class BenchRunner extends UpdateRunner {
    private Bundle args;
    public void onCreate(Bundle arguments) { super.onCreate(arguments); args=arguments; start(); }
    public void onStart() {
        if("1".equals(args.getString("live_update",""))){try{liveUpdate();}catch(Exception e){Bundle result=new Bundle();result.putString("error",e.toString());finish(Activity.RESULT_CANCELED,result);}return;}
        if("1".equals(args.getString("updates",""))){try{updates();}catch(Exception e){Bundle result=new Bundle();result.putString("error",e.toString());finish(Activity.RESULT_CANCELED,result);}return;}
        if("1".equals(args.getString("proximity_ui",""))){try{proximityUi();}catch(Exception e){Bundle result=new Bundle();result.putString("error",e.toString());finish(Activity.RESULT_CANCELED,result);}return;}
        if("1".equals(args.getString("popup",""))){
            Bundle out=new Bundle();
            try{
                MainActivity a=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                runOnMainSync(a::previewBackgroundFeedback);Thread.sleep(2400);
                if(MainActivity.visible)throw new IllegalStateException("Activity still foreground after popup test");
                java.lang.reflect.Field field=FloatingDoor.class.getDeclaredField("root");field.setAccessible(true);
                android.view.View overlay=(android.view.View)field.get(null);
                if(overlay==null||!overlay.isAttachedToWindow())throw new IllegalStateException("Popup missing: "+DoorSettings.prefs(getTargetContext()).getString("last_feedback_route","none"));
                out.putString("popup","PASS: actual activity background transition renders the test card");
                out.putString("route",DoorSettings.prefs(getTargetContext()).getString("last_feedback_route","none"));
                runOnMainSync(()->DoorFeedback.clear(getTargetContext()));finish(Activity.RESULT_OK,out);
            }catch(Exception e){out.putString("error",e.toString());finish(Activity.RESULT_CANCELED,out);}return;
        }
        if("1".equals(args.getString("access",""))){try{access();}catch(Exception e){Bundle result=new Bundle();result.putString("error",e.toString());finish(Activity.RESULT_CANCELED,result);}return;}
        if("1".equals(args.getString("background",""))){try{background("1".equals(args.getString("screen_off","")));}catch(Exception e){Bundle result=new Bundle();result.putString("error",e.toString());finish(Activity.RESULT_CANCELED,result);}return;}
        if("1".equals(args.getString("preview",""))){super.onStart();return;}
        Bundle result=new Bundle();
        try {
            String code=args.getString("setup","");
            if(!code.isEmpty()) Keys.enroll(getTargetContext(),code);
            Keys.key(getTargetContext());
            startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            runOnMainSync(()->getTargetContext().startForegroundService(new Intent(getTargetContext(),EntryService.class).setAction("START")));
            Thread.sleep(Math.min(55000,Long.parseLong(args.getString("duration","8000"))));
            result.putInt("sessions_started",EntryService.sessionsStarted);
            result.putInt("sessions_completed",EntryService.sessionsCompleted);
            result.putInt("authenticated_responses",EntryService.authenticatedResponses);
            result.putString("status",EntryService.currentStatus);
            result.putString("board",Keys.prefs(getTargetContext()).getString("board",""));
            finish(Activity.RESULT_OK,result);
        } catch(Exception e) {
            result.putString("error",e.getClass().getSimpleName()+": "+e.getMessage());
            finish(Activity.RESULT_CANCELED,result);
        }
    }
}

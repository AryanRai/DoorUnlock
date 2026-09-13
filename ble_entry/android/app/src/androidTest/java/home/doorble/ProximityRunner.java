package home.doorble;

import android.app.*;
import android.content.*;
import android.os.Bundle;
import android.view.View;
import java.lang.reflect.Field;

/** Exercises display states only; fixture data never reaches the BLE decision code. */
public class ProximityRunner extends AccessRunner {
    private Field rootField,minField;
    private View root()throws Exception{return (View)rootField.get(null);}
    private boolean compact()throws Exception{return minField.getBoolean(null);}
    private void publish(String stage){runOnMainSync(()->DoorFeedback.publish(getTargetContext(),stage,"UI fixture only · no door operation"));}
    protected void proximityUi()throws Exception{
        Bundle out=new Bundle();Context c=getTargetContext();
        String phone=Keys.prefs(c).getString("phone","");int threshold=Keys.prefs(c).getInt("threshold",0);
        boolean wasEnabled=DoorSettings.enabled(c);
        MainActivity activity=(MainActivity)startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        runOnMainSync(()->{c.stopService(new Intent(c,EntryService.class));DoorFeedback.clear(c);activity.moveTaskToBack(true);});Thread.sleep(700);
        rootField=FloatingDoor.class.getDeclaredField("root");rootField.setAccessible(true);minField=FloatingDoor.class.getDeclaredField("minimized");minField.setAccessible(true);
        try{
            publish(DoorFeedback.CLOSE);Thread.sleep(500);if(root()==null||compact())throw new IllegalStateException("Close card missing");captureCard("close");
            runOnMainSync(FloatingDoor::minimize);if(root()==null||!compact())throw new IllegalStateException("Manual minimize failed");
            publish(DoorFeedback.CLOSE);if(!compact())throw new IllegalStateException("Repeated nearby update expanded minimized card");Thread.sleep(350);captureCard("compact");
            View chip=root();runOnMainSync(chip::performClick);if(compact())throw new IllegalStateException("Expand failed");
            publish(DoorFeedback.VERIFYING);if(compact())throw new IllegalStateException("Verification stayed minimized");
            publish(DoorFeedback.SUCCESS);Thread.sleep(4400);if(root()==null||!compact())throw new IllegalStateException("Success did not auto-minimize");
            publish(DoorFeedback.OUT);if(compact())throw new IllegalStateException("Departure card did not expand");
            Thread.sleep(4400);if(root()==null||!compact())throw new IllegalStateException("Departure did not minimize");
            Thread.sleep(6000);if(root()!=null)throw new IllegalStateException("Departure did not hide");
            publish(DoorFeedback.OUT);if(root()!=null)throw new IllegalStateException("Repeated departure reopened dismissed card");
            publish(DoorFeedback.CLOSE);if(root()==null)throw new IllegalStateException("New arrival failed to reopen card");
            if(!phone.equals(Keys.prefs(c).getString("phone",""))||threshold!=Keys.prefs(c).getInt("threshold",0))throw new IllegalStateException("Enrollment changed");
            out.putString("proximity_ui","PASS: close, minimize/expand, verifying, success auto-minimize, departure expiry and no repeated reopen; enrollment preserved");
        }finally{runOnMainSync(()->{DoorFeedback.clear(c);if(wasEnabled)c.startForegroundService(new Intent(c,EntryService.class).setAction("RESUME"));});}
        finish(Activity.RESULT_OK,out);
    }
    private void captureCard(String name)throws Exception{
        Exception[] error={null};runOnMainSync(()->{try{
            View view=root();android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(view.getWidth(),view.getHeight(),android.graphics.Bitmap.Config.ARGB_8888);view.draw(new android.graphics.Canvas(bitmap));
            try(java.io.FileOutputStream stream=new java.io.FileOutputStream(new java.io.File(getTargetContext().getFilesDir(),"preview-proximity-"+name+".png"))){bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,stream);}bitmap.recycle();
        }catch(Exception e){error[0]=e;}});if(error[0]!=null)throw error[0];
    }
}

package home.doorble;

import android.app.*;
import android.content.Intent;
import android.graphics.*;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;

/** Local USB visual checks. Captures only the app's views, never other apps or saved keys. */
public class PreviewRunner extends Instrumentation {
    public void onStart(){
        Bundle out=new Bundle();
        try{
            if(TotpKeys.key()==null){try{TotpKeys.save("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ");if(!LegacyProtocol.code(TotpKeys.key(),59).equals("287082"))throw new AssertionError("Keystore TOTP mismatch");}finally{TotpKeys.remove();}out.putString("totp_keystore","PASS: imported key signs RFC 6238 vector; test key removed");}
            MainActivity activity=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            Thread.sleep(1000);capture(activity,"home",false);
            for(int kind=0;kind<4;kind++){
                final int choice=kind;runOnMainSync(()->activity.showMethod(choice));Thread.sleep(700);capture(activity,"method-"+kind,true);
                if(kind==0||kind==3){
                    Field f=MainActivity.class.getDeclaredField("sheet");f.setAccessible(true);Dialog dialog=(Dialog)f.get(activity);
                    String label=kind==0?"Preview tap animation":"Preview face animation";
                    runOnMainSync(()->click(dialog.getWindow().getDecorView(),label));Thread.sleep(2000);capture(activity,"complete-"+kind,true);
                }
            }
            runOnMainSync(activity::showBleSetup);Thread.sleep(700);capture(activity,"setup",true);
            out.putString("result","PASS: home, four method sheets, and preserved BLE setup rendered");
            out.putString("directory",getTargetContext().getFilesDir().getAbsolutePath());
            finish(Activity.RESULT_OK,out);
        }catch(Exception e){out.putString("error",e.toString());finish(Activity.RESULT_CANCELED,out);}
    }
    private boolean click(View view,String label){
        if(view instanceof android.widget.Button&&label.contentEquals(((android.widget.Button)view).getText()))return view.performClick();
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)if(click(group.getChildAt(i),label))return true;}
        return false;
    }
    protected void capture(MainActivity activity,String name,boolean dialog)throws Exception{
        // Skip code screenshots if a real TOTP key has been enrolled.
        if(name.equals("method-1")&&TotpKeys.key()!=null)return;
        final Exception[] error={null};
        runOnMainSync(()->{try{
            View view=activity.getWindow().getDecorView();
            if(dialog){Field f=MainActivity.class.getDeclaredField("sheet");f.setAccessible(true);Dialog d=(Dialog)f.get(activity);if(d==null)throw new IllegalStateException("Sheet missing");view=d.getWindow().getDecorView();}
            Bitmap bitmap=Bitmap.createBitmap(view.getWidth(),view.getHeight(),Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(bitmap);canvas.drawColor(0xffeef1e9);view.draw(canvas);
            try(FileOutputStream stream=new FileOutputStream(new File(getTargetContext().getFilesDir(),"preview-"+name+".png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,stream);}bitmap.recycle();
        }catch(Exception e){error[0]=e;}});
        if(error[0]!=null)throw error[0];
    }
}

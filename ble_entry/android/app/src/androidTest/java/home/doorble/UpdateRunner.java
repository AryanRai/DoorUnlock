package home.doorble;

import android.app.Activity;
import android.content.*;
import android.os.*;
import java.io.*;
import org.json.JSONObject;

/** Uses signed local fixture APKs; never installs them or alters enrollment. */
public class UpdateRunner extends ProximityRunner {
    protected void liveUpdate()throws Exception{
        Context c=getTargetContext();Bundle result=new Bundle();
        MainActivity a=(MainActivity)startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        runOnMainSync(()->AppUpdates.check(a,true));
        java.lang.reflect.Field field=AppUpdates.class.getDeclaredField("currentDialog");field.setAccessible(true);
        androidx.appcompat.app.AlertDialog dialog=null;
        for(int i=0;i<25;i++){Thread.sleep(1000);dialog=(androidx.appcompat.app.AlertDialog)((java.lang.ref.WeakReference<?>)field.get(null)).get();if(dialog!=null&&dialog.isShowing())break;}
        if(dialog==null||!dialog.isShowing())throw new IllegalStateException("Update offer missing: "+AppUpdates.prefs(c).getString("status",""));
        androidx.appcompat.app.AlertDialog offer=dialog;
        runOnMainSync(()->offer.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick());
        for(int i=0;i<35;i++){Thread.sleep(1000);if(!AppUpdates.prefs(c).getString("pending","").isEmpty())break;}
        UpdateRelease release=new UpdateRelease(AppUpdates.prefs(c).getString("pending",""));UpdateFiles.verify(c,UpdateFiles.apk(c),release);
        result.putString("github_update","PASS: actual GitHub feed prompted a newer version, Update downloaded it, checksum/signing verified; installer handoff prepared");
        result.putString("enrollment_fingerprint",Keys.prefs(c).getString("board","")+":"+Keys.prefs(c).getString("phone","")+":"+Keys.prefs(c).getInt("threshold",0));
        finish(Activity.RESULT_OK,result);
    }
    private byte[] shellFile(String name)throws Exception{
        try(ParcelFileDescriptor fd=getUiAutomation().executeShellCommand("cat /data/local/tmp/"+name);FileInputStream in=new FileInputStream(fd.getFileDescriptor());ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[32768];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return out.toByteArray();}
    }
    private File copy(String name)throws Exception{File file=new File(getTargetContext().getCacheDir(),name);try(FileOutputStream out=new FileOutputStream(file)){out.write(shellFile(name));}return file;}
    interface Check {void run()throws Exception;}
    private void rejects(Check check)throws Exception{try{check.run();}catch(Exception expected){return;}throw new AssertionError("Unsafe update accepted");}
    protected void updates()throws Exception{
        Context c=getTargetContext();Bundle result=new Bundle();String json=new String(shellFile("door-update-valid.json"),java.nio.charset.StandardCharsets.UTF_8);UpdateRelease release=new UpdateRelease(json);
        File valid=copy("door-update-valid.apk"),wrong=copy("door-update-wrong.apk");
        try{
            UpdateFiles.verify(c,valid,release);
            rejects(()->new UpdateRelease(new JSONObject(json).put("apkUrl","http://github.com/AryanRai/DoorUnlock/evil.apk").toString()));
            rejects(()->new UpdateRelease(new JSONObject(json).put("apkUrl","https://github.com/attacker/DoorUnlock/releases/download/door-android-1.5/Door-1.5.apk").toString()));
            rejects(()->new UpdateRelease(new JSONObject(json).put("packageName","other.app").toString()));
            rejects(()->UpdateFiles.verify(c,valid,new UpdateRelease(new JSONObject(json).put("versionCode",UpdateFiles.installed(c)).toString())));
            rejects(()->UpdateFiles.verify(c,valid,new UpdateRelease(new JSONObject(json).put("sha256",new String(new char[64]).replace('\0','0')).toString())));
            String wrongJson=new String(shellFile("door-update-wrong.json"),java.nio.charset.StandardCharsets.UTF_8);
            rejects(()->UpdateFiles.verify(c,wrong,new UpdateRelease(wrongJson)));
            if(!release.newerThan(UpdateFiles.installed(c),Build.VERSION.SDK_INT)||release.newerThan(release.versionCode,Build.VERSION.SDK_INT))throw new AssertionError("Upgrade comparison failed");
            UpdateFiles.apk(c).getParentFile().mkdirs();try(InputStream in=new FileInputStream(valid);OutputStream out=new FileOutputStream(UpdateFiles.apk(c))){byte[] b=new byte[32768];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}
            Intent installer=AppUpdates.installIntent(c);
            if(!"content".equals(installer.getData().getScheme())||(installer.getFlags()&Intent.FLAG_GRANT_READ_URI_PERMISSION)==0)throw new AssertionError("Installer grant missing");
            result.putString("update_security","PASS: real newer APK accepted; wrong repository/package/version/hash/signing certificate rejected; content URI read grant checked");
        }finally{valid.delete();wrong.delete();UpdateFiles.apk(c).delete();}
        finish(Activity.RESULT_OK,result);
    }
}

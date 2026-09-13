package home.doorble;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.lang.ref.WeakReference;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

/** Foreground, optional GitHub release checks. Never holds enrollment keys or sends door requests. */
public final class AppUpdates {
    private static final long INTERVAL=6*60*60*1000L;
    private static final ExecutorService worker=Executors.newSingleThreadExecutor();
    private static final Handler main=new Handler(Looper.getMainLooper());
    private static final AtomicBoolean busy=new AtomicBoolean();
    private static boolean dialogOpen,readyOffered;
    private static java.lang.ref.WeakReference<androidx.appcompat.app.AlertDialog> currentDialog=new java.lang.ref.WeakReference<>(null);
    static SharedPreferences prefs(Context c){return c.getSharedPreferences("app-updates",Context.MODE_PRIVATE);}
    public static void onDestroy(Activity a){androidx.appcompat.app.AlertDialog dialog=currentDialog.get();if(dialog!=null&&dialog.getContext() instanceof android.view.ContextThemeWrapper){android.content.Context base=((android.view.ContextThemeWrapper)dialog.getContext()).getBaseContext();if(base==a)dialog.dismiss();}}
    private static boolean active(Activity a){return a!=null&&!a.isFinishing()&&!a.isDestroyed()&&MainActivity.visible;}
    private static void toast(Activity a,String text){if(active(a))Toast.makeText(a,text,Toast.LENGTH_LONG).show();}
    public static void onResume(Activity a){
        if(prefs(a).getBoolean("permission_pending",false)){
            prefs(a).edit().remove("permission_pending").apply();
            if(a.getPackageManager().canRequestPackageInstalls())install(a);else toast(a,"Update saved. You can install it later from App updates.");return;
        }
        if(!readyOffered&&UpdateFiles.apk(a).isFile()&&!prefs(a).getString("pending","").isEmpty()){
            try{UpdateRelease release=new UpdateRelease(prefs(a).getString("pending",""));
                if(release.newerThan(UpdateFiles.installed(a),Build.VERSION.SDK_INT)){readyOffered=true;ready(a);return;}
                discard(a);
            }catch(Exception e){discard(a);}
        }
        check(a,false);
    }
    public static void check(Activity activity,boolean manual){
        Context c=activity.getApplicationContext();SharedPreferences p=prefs(c);long now=System.currentTimeMillis(),last=p.getLong("last_check",0);
        if(!manual&&(!p.getBoolean("automatic",true)||(now>=last&&now-last<INTERVAL)||now<p.getLong("later_until",0)))return;
        if(dialogOpen||!busy.compareAndSet(false,true)){if(manual)toast(activity,"An update check or download is already running.");return;}
        if(manual)toast(activity,"Checking GitHub for updates…");
        WeakReference<Activity> ref=new WeakReference<>(activity);
        worker.execute(()->{try{
            String json=UpdateFiles.manifest();UpdateRelease release=new UpdateRelease(json);long installed=UpdateFiles.installed(c);
            p.edit().putLong("last_check",now).putString("status","Checked GitHub: "+release.versionName).apply();
            main.post(()->{busy.set(false);Activity a=ref.get();if(!active(a)){
                    if(release.newerThan(installed,Build.VERSION.SDK_INT))p.edit().remove("last_check").apply();return;}
                if(release.versionCode<=installed){if(manual)toast(a,"You have the latest Door app ("+UpdateFiles.installedName(c)+").");return;}
                if(release.minSdk>Build.VERSION.SDK_INT){if(manual)toast(a,"This update needs a newer Android version.");return;}
                if(!manual&&p.getInt("skip_version",0)==release.versionCode)return;
                offer(a,release,json);
            });
        }catch(Exception e){p.edit().putLong("last_check",now).putString("status","Update check failed: "+e.getMessage()).apply();main.post(()->{busy.set(false);if(manual)toast(ref.get(),"Could not check updates: "+e.getMessage());});}});
    }
    private static void offer(Activity a,UpdateRelease release,String json){
        if(!active(a)||dialogOpen)return;dialogOpen=true;
        androidx.appcompat.app.AlertDialog dialog=new MaterialAlertDialogBuilder(a).setTitle("Door "+release.versionName+" is available")
            .setMessage(release.notes+"\n\nYour enrollment and calibration stay saved. Android will ask before installing.")
            .setPositiveButton("Update",(d,w)->download(a,release,json))
            .setNegativeButton("Later",(d,w)->prefs(a).edit().putLong("later_until",System.currentTimeMillis()+24*60*60*1000L).apply())
            .setNeutralButton("Skip this version",(d,w)->prefs(a).edit().putInt("skip_version",release.versionCode).apply())
            .setOnDismissListener(d->dialogOpen=false).show();
        currentDialog=new java.lang.ref.WeakReference<>(dialog);
    }
    private static void download(Activity activity,UpdateRelease release,String json){
        if(!busy.compareAndSet(false,true))return;Context c=activity.getApplicationContext();WeakReference<Activity> ref=new WeakReference<>(activity);
        toast(activity,"Downloading update. BLE checks can keep running.");
        worker.execute(()->{try{
            UpdateFiles.download(c,release);prefs(c).edit().putString("pending",json).putString("status","Update downloaded and verified").apply();readyOffered=false;
            main.post(()->{busy.set(false);if(active(ref.get())){readyOffered=true;ready(ref.get());}});
        }catch(Exception e){prefs(c).edit().putString("status","Update download failed: "+e.getMessage()).apply();main.post(()->{busy.set(false);toast(ref.get(),"Update not installed: "+e.getMessage());});}});
    }
    public static void ready(Activity a){
        if(!UpdateFiles.apk(a).isFile()||prefs(a).getString("pending","").isEmpty()){toast(a,"No downloaded update. Tap Check for updates.");return;}
        if(!active(a)||dialogOpen)return;dialogOpen=true;
        androidx.appcompat.app.AlertDialog dialog=new MaterialAlertDialogBuilder(a).setTitle("Update ready to install")
            .setMessage("The APK passed its checksum and signing checks. Installing briefly restarts Door; your saved setup stays in place.")
            .setPositiveButton("Install",(d,w)->install(a)).setNegativeButton("Later",null).setOnDismissListener(d->dialogOpen=false).show();
        currentDialog=new java.lang.ref.WeakReference<>(dialog);
    }
    static Intent installIntent(Context c){
        Uri uri=FileProvider.getUriForFile(c,c.getPackageName()+".updates",UpdateFiles.apk(c));
        Intent intent=new Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.setClipData(ClipData.newRawUri("Door update",uri));return intent;
    }
    private static void install(Activity activity){
        if(!activity.getPackageManager().canRequestPackageInstalls()){
            new MaterialAlertDialogBuilder(activity).setTitle("Allow updates from Door")
                .setMessage("Android needs Allow from this source enabled for Door. Return here afterward to open the installer.")
                .setPositiveButton("Open settings",(d,w)->{prefs(activity).edit().putBoolean("permission_pending",true).apply();try{activity.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+activity.getPackageName())));}catch(ActivityNotFoundException e){prefs(activity).edit().remove("permission_pending").apply();toast(activity,"Open Android settings → Apps → Special app access → Install unknown apps → Door.");}})
                .setNegativeButton("Later",null).show();return;
        }
        if(!busy.compareAndSet(false,true))return;Context c=activity.getApplicationContext();WeakReference<Activity> ref=new WeakReference<>(activity);
        worker.execute(()->{try{
            UpdateRelease release=new UpdateRelease(prefs(c).getString("pending",""));UpdateFiles.verify(c,UpdateFiles.apk(c),release);
            main.post(()->{busy.set(false);Activity a=ref.get();if(!active(a)){readyOffered=false;return;}try{a.startActivity(installIntent(c));}catch(ActivityNotFoundException e){toast(a,"No APK installer is available on this phone.");}});
        }catch(Exception e){discard(c);main.post(()->{busy.set(false);toast(ref.get(),"Update not installed: "+e.getMessage());});}});
    }
    private static void discard(Context c){prefs(c).edit().remove("pending").apply();UpdateFiles.apk(c).delete();}
    public static void settings(Activity a){
        String[] choices={(prefs(a).getBoolean("automatic",true)?"Disable":"Enable")+" automatic update checks","Check for updates now","Install downloaded update","View GitHub releases"};
        new MaterialAlertDialogBuilder(a).setTitle("App updates · "+UpdateFiles.installedName(a)).setItems(choices,(d,n)->{
            if(n==0){boolean value=!prefs(a).getBoolean("automatic",true);prefs(a).edit().putBoolean("automatic",value).apply();toast(a,value?"Automatic checks enabled":"Automatic checks disabled");}
            else if(n==1)check(a,true);else if(n==2)ready(a);else try{a.startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(UpdateRelease.REPOSITORY+"/releases")));}catch(ActivityNotFoundException e){toast(a,"No browser available");}
        }).show();
    }
}

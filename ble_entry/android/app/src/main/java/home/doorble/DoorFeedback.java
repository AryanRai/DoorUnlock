package home.doorble;

import android.app.*;
import android.content.*;
import android.media.AudioAttributes;
import android.os.*;

public final class DoorFeedback {
    public static final int ALERT_ID=22;
    public static final String VERIFYING="VERIFYING",SUCCESS="SUCCESS",HELD="HELD",CLOSE="CLOSE",OUT="OUT";
    public static boolean proximity(String stage){return CLOSE.equals(stage)||OUT.equals(stage);}
    private static final long[] ARRIVE={0,25},ACCEPT={0,45,65,75};
    public static void channels(Context c){
        NotificationManager nm=c.getSystemService(NotificationManager.class);
        NotificationChannel service=new NotificationChannel("entry","Background BLE & debug",NotificationManager.IMPORTANCE_LOW);service.setShowBadge(false);nm.createNotificationChannel(service);
        NotificationChannel proximity=new NotificationChannel("door-proximity","Nearby status",NotificationManager.IMPORTANCE_LOW);proximity.setSound(null,null);proximity.enableVibration(false);nm.createNotificationChannel(proximity);
        for(String id:new String[]{"door-approach","door-result"}){
            NotificationChannel channel=new NotificationChannel(id,id.equals("door-approach")?"Nearby verification":"Door test result",NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Dry-run approach feedback, including on the lock screen");channel.setSound(null,null);channel.enableVibration(true);channel.setVibrationPattern(id.equals("door-approach")?ARRIVE:ACCEPT);channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);nm.createNotificationChannel(channel);
        }
    }
    public static void publish(Context c,String stage,String detail){
        long now=SystemClock.elapsedRealtime();DoorSettings.prefs(c).edit().putString("feedback_stage",stage).putString("feedback_detail",detail).putLong("feedback_at",now).apply();
        String title=title(stage);boolean alerts=DoorSettings.prefs(c).getBoolean("alerts",true);
        String route="Approach alerts switched off";
        if(alerts){
            if(MainActivity.visible){if(!proximity(stage))haptic(c,SUCCESS.equals(stage));route="Door in foreground: in-app feedback";}
            else{
                channels(c);String channel=proximity(stage)?"door-proximity":SUCCESS.equals(stage)?"door-result":"door-approach";
                Notification n=new Notification.Builder(c,channel).setSmallIcon(R.drawable.ic_door).setContentTitle(title).setContentText(detail)
                    .setStyle(new Notification.BigTextStyle().bigText(detail)).setContentIntent(DoorIntents.pending(c,2)).setAutoCancel(true)
                    .setCategory(Notification.CATEGORY_STATUS).setVisibility(Notification.VISIBILITY_PUBLIC).setTimeoutAfter(SUCCESS.equals(stage)?30000:12000).build();
                boolean allowed=c.getSystemService(NotificationManager.class).areNotificationsEnabled();
                try{c.getSystemService(NotificationManager.class).notify(ALERT_ID,n);}catch(SecurityException ignored){allowed=false;}
                route=FloatingDoor.show(c,stage,detail)+(allowed?"; notification posted":"; notifications blocked");
            }
        }
        String stamp=new java.text.SimpleDateFormat("HH:mm:ss",java.util.Locale.getDefault()).format(new java.util.Date());
        String diagnostic=stamp+" "+stage+" · "+route;
        String history=diagnostic+"\n"+DoorSettings.prefs(c).getString("feedback_routes","");
        DoorSettings.prefs(c).edit().putString("last_feedback_route",diagnostic).putString("feedback_routes",history.substring(0,Math.min(2400,history.length()))).apply();
        DoorWidget.updateAll(c);
    }
    public static String title(String stage){return SUCCESS.equals(stage)?"Would unlock":VERIFYING.equals(stage)?"Verifying nearby phone…":CLOSE.equals(stage)?"Close by":OUT.equals(stage)?"Out of range":"Entry held";}
    public static void clear(Context c){DoorSettings.prefs(c).edit().remove("feedback_stage").remove("feedback_at").remove("feedback_detail").apply();c.getSystemService(NotificationManager.class).cancel(ALERT_ID);FloatingDoor.reset();}
    public static void haptic(Context c,boolean success){
        if(!DoorSettings.prefs(c).getBoolean("alerts",true))return;
        NotificationManager nm=c.getSystemService(NotificationManager.class);
        if(nm.getCurrentInterruptionFilter()!=NotificationManager.INTERRUPTION_FILTER_ALL)return;
        Vibrator v=Build.VERSION.SDK_INT>=31?c.getSystemService(VibratorManager.class).getDefaultVibrator():(Vibrator)c.getSystemService(Context.VIBRATOR_SERVICE);
        if(v!=null&&v.hasVibrator())v.vibrate(VibrationEffect.createWaveform(success?ACCEPT:ARRIVE,-1),new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build());
    }
}

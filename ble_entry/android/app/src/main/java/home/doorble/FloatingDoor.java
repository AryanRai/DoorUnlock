package home.doorble;

import android.app.*;
import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;

/** Bottom proximity card with an expandable compact state. Never covers the keyguard. */
public final class FloatingDoor {
    private static WindowManager manager;
    private static LinearLayout root;
    private static Context context;
    private static String stage="",detail="";
    private static boolean minimized,suppressed;
    private static final Handler handler=new Handler(Looper.getMainLooper());
    private static final Runnable collapse=FloatingDoor::minimize;
    private static final Runnable close=FloatingDoor::dismiss;
    private static int dp(Context c,int v){return Math.round(v*c.getResources().getDisplayMetrics().density);}
    private static String blocked(Context c){
        if(!DoorSettings.prefs(c).getBoolean("floating",false))return "Floating cards switched off";
        if(!Settings.canDrawOverlays(c))return "Overlay permission missing";
        if(!c.getSystemService(PowerManager.class).isInteractive())return "Screen off: notification only";
        if(c.getSystemService(KeyguardManager.class).isKeyguardLocked())return "Phone locked: notification only";
        return null;
    }
    public static String show(Context original,String next,String text){
        Context c=original.getApplicationContext();String reason=blocked(c);
        if(reason!=null){dismiss();return reason;}
        boolean changed=!next.equals(stage);context=c;detail=text;
        if(!changed&&suppressed)return "Card dismissed for this state";
        if(changed){stage=next;minimized=false;suppressed=false;handler.removeCallbacksAndMessages(null);}
        String result=render();
        if(changed){
            if(DoorFeedback.OUT.equals(stage)||DoorFeedback.SUCCESS.equals(stage)||DoorFeedback.HELD.equals(stage)){
                handler.postDelayed(collapse,4000);handler.postDelayed(close,10000);
            }else if(DoorFeedback.VERIFYING.equals(stage))handler.postDelayed(close,15000);
        }
        return result;
    }
    private static TextView action(Context c,String text,String label){
        TextView v=new TextView(c);v.setText(text);v.setTextSize(25);v.setTextColor(0xffe4fff5);v.setGravity(Gravity.CENTER);v.setContentDescription(label);return v;
    }
    private static String render(){
        Context c=context;if(c==null)return "No card state";
        String reason=blocked(c);if(reason!=null){dismiss();return reason;}
        removeView();manager=c.getSystemService(WindowManager.class);
        root=new LinearLayout(c);root.setOrientation(LinearLayout.HORIZONTAL);root.setGravity(Gravity.CENTER_VERTICAL);root.setPadding(dp(c,14),dp(c,minimized?4:12),dp(c,8),dp(c,minimized?4:12));
        GradientDrawable bg=new GradientDrawable();bg.setColor(DoorFeedback.OUT.equals(stage)?0xf5323c42:0xf51c3733);bg.setCornerRadius(dp(c,30));root.setBackground(bg);root.setElevation(dp(c,12));
        if(!minimized){
            DoorMotionView motion=new DoorMotionView(c,2,0xffd8f3e9,0xff28514a);motion.complete(DoorFeedback.SUCCESS.equals(stage));motion.animate(DoorFeedback.VERIFYING.equals(stage));root.addView(motion,new LinearLayout.LayoutParams(dp(c,64),dp(c,64)));
        }
        LinearLayout copy=new LinearLayout(c);copy.setOrientation(LinearLayout.VERTICAL);
        TextView title=new TextView(c);title.setText((minimized?"Door · ":"")+DoorFeedback.title(stage));title.setTextColor(0xffe4fff5);title.setTextSize(minimized?15:18);copy.addView(title);
        if(!minimized){TextView sub=new TextView(c);sub.setText(detail+"\nDry run · tap for controls");sub.setTextColor(0xffc0dcd5);sub.setTextSize(12);copy.addView(sub);}
        root.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
        LinearLayout actions=new LinearLayout(c);actions.setOrientation(minimized?LinearLayout.HORIZONTAL:LinearLayout.VERTICAL);
        if(!minimized){TextView minus=action(c,"−","Minimize door card");actions.addView(minus,new LinearLayout.LayoutParams(dp(c,48),dp(c,48)));minus.setOnClickListener(v->minimize());}
        TextView x=action(c,"×","Dismiss door card");actions.addView(x,new LinearLayout.LayoutParams(dp(c,48),dp(c,48)));x.setOnClickListener(v->dismiss());root.addView(actions);
        root.setContentDescription(DoorFeedback.title(stage)+(minimized?". Tap to expand":". Dry run"));
        root.setOnClickListener(v->{if(minimized){minimized=false;render();if(!DoorFeedback.CLOSE.equals(stage))handler.postDelayed(collapse,4000);}else{try{DoorIntents.pending(c,2).send();}catch(PendingIntent.CanceledException ignored){}dismiss();}});
        int width=Math.min(dp(c,minimized?270:420),c.getResources().getDisplayMetrics().widthPixels-dp(c,32));
        WindowManager.LayoutParams p=new WindowManager.LayoutParams(width,-2,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.TRANSLUCENT);p.gravity=Gravity.BOTTOM|(minimized?Gravity.END:Gravity.CENTER_HORIZONTAL);p.y=dp(c,24);p.x=minimized?dp(c,16):0;
        if(Build.VERSION.SDK_INT>=30)p.setFitInsetsTypes(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
        try{manager.addView(root,p);if(android.animation.ValueAnimator.areAnimatorsEnabled()){root.setTranslationY(dp(c,14));root.setAlpha(0);root.animate().translationY(0).alpha(1).setDuration(250).start();}return minimized?"Compact card attached":"Floating card attached";}
        catch(RuntimeException e){dismiss();return "Overlay failed: "+e.getClass().getSimpleName();}
    }
    public static void minimize(){if(root==null)return;handler.removeCallbacks(collapse);minimized=true;render();}
    private static void removeView(){if(root!=null&&manager!=null){try{manager.removeView(root);}catch(RuntimeException ignored){}}root=null;manager=null;}
    public static void dismiss(){handler.removeCallbacksAndMessages(null);removeView();suppressed=true;}
    public static void reset(){dismiss();context=null;stage="";detail="";minimized=false;suppressed=false;}
}
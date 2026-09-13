package home.doorble;

import android.Manifest;
import android.animation.ValueAnimator;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.*;
import android.text.InputType;
import android.view.*;
import android.view.animation.OvershootInterpolator;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.*;
import java.util.*;

public class MainActivity extends AppCompatActivity {
    public static volatile boolean visible=false;
    private long observedFeedback=0;
    private boolean routed=false;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private TextView liveStatus,history,bleDetails,totpNumber,totpRemaining;
    private BottomSheetDialog sheet;
    private DoorMotionView sheetMotion;
    private TextView sheetTitle,sheetSubtitle;
    private int selected=-1,previewSequence=0;
    private String observedEvent="";
    private boolean resumed;
    private int ink,surface,muted,primary,container;
    private final Runnable refresh=new Runnable(){public void run(){renderStatus();if(resumed)handler.postDelayed(this,500);}};

    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        ink=theme(com.google.android.material.R.attr.colorOnSurface,0xff1a251e);
        surface=theme(com.google.android.material.R.attr.colorSurface,0xfff6f8f0);
        muted=theme(com.google.android.material.R.attr.colorOnSurfaceVariant,0xff586158);
        primary=theme(androidx.appcompat.R.attr.colorPrimary,0xff42634a);
        container=theme(com.google.android.material.R.attr.colorPrimaryContainer,0xffc3edc9);
        getWindow().setStatusBarColor(Color.TRANSPARENT);getWindow().setNavigationBarColor(surface);
        boolean light=ColorUtils.calculateLuminance(surface)>.5;
        androidx.core.view.WindowCompat.getInsetsController(getWindow(),getWindow().getDecorView()).setAppearanceLightStatusBars(light);
        androidx.core.view.WindowCompat.getInsetsController(getWindow(),getWindow().getDecorView()).setAppearanceLightNavigationBars(light);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(surface);
        LinearLayout page=column();page.setPadding(dp(24),dp(14),dp(24),dp(24));scroll.addView(page);setContentView(scroll);
        ViewCompat.setOnApplyWindowInsetsListener(page,(v,insets)->{androidx.core.graphics.Insets b=insets.getInsets(WindowInsetsCompat.Type.systemBars());v.setPadding(dp(24)+b.left,dp(14)+b.top,dp(24)+b.right,dp(24)+b.bottom);return insets;});
        LinearLayout top=row();TextView brand=text("door",25,ink);brand.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));top.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        TextView badge=text("TEST SPACE",11,primary);badge.setLetterSpacing(.1f);top.addView(badge);page.addView(top);
        TextView greeting=text("A little closer.\nYou're home.",36,ink);greeting.setLineSpacing(0,1.04f);space(page,28);page.addView(greeting);
        space(page,8);page.addView(text("One door. Your way in.",15,muted));space(page,24);
        MaterialCardView home=card(container,32);LinearLayout homeContent=column();homeContent.setPadding(dp(22),dp(18),dp(22),dp(24));home.addView(homeContent);
        LinearLayout homeHeader=row();TextView front=text("Front door",20,ink);front.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));homeHeader.addView(front,new LinearLayout.LayoutParams(0,-2,1));homeHeader.addView(text("HOME",10,ink));homeContent.addView(homeHeader);
        DoorMotionView hero=new DoorMotionView(this,DoorMotionView.TAP,ink,ColorUtils.blendARGB(container,surface,.28f));hero.animate(false);homeContent.addView(hero,new LinearLayout.LayoutParams(-1,dp(142)));
        liveStatus=text("Ready when you are",17,ink);liveStatus.setGravity(Gravity.CENTER);homeContent.addView(liveStatus);
        TextView note=text("Dry run · your door stays closed",12,ink);note.setGravity(Gravity.CENTER);space(homeContent,6);homeContent.addView(note);page.addView(home);space(page,26);
        page.addView(text("How would you like to enter?",17,ink));space(page,14);
        LinearLayout first=row(),second=row();method(first,0,"Tap to open","Animation preview");method(first,1,"Use a code","TOTP · inside this app");method(second,2,"Come closer","BLE · ready to test");method(second,3,"Say hello","Face animation preview");page.addView(first);space(page,12);page.addView(second);space(page,22);
        page.addView(button("Set up nearby entry",this::showBleSetup));page.addView(button("Background, shortcuts & debug",this::showBackgroundSetup));page.addView(button("App updates",()->AppUpdates.settings(this)));space(page,14);
        TextView recent=text("RECENT ACTIVITY",11,muted);recent.setLetterSpacing(.1f);page.addView(recent);space(page,8);
        history=text("Your test arrivals will appear here.",13,muted);page.addView(history);space(page,18);
        TextView disclaimer=text("BLE makes real proximity checks. Button and face show animation previews. TOTP stays on this phone; no internet unlock request is sent.",12,muted);disclaimer.setLineSpacing(dp(3),1);page.addView(disclaimer);
        observedEvent=Keys.prefs(this).getString("history","");DoorAccess.install(this);
    }
    private int theme(int attr,int fallback){return MaterialColors.getColor(this,attr,fallback);}
    private int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    private LinearLayout column(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}
    private LinearLayout row(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.HORIZONTAL);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    private void space(LinearLayout p,int size){p.addView(new View(this),new LinearLayout.LayoutParams(1,dp(size)));}
    private TextView text(String s,int sp,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(color);return t;}
    private MaterialCardView card(int color,int radius){MaterialCardView c=new MaterialCardView(this);c.setCardBackgroundColor(color);c.setRadius(dp(radius));c.setCardElevation(0);c.setStrokeWidth(0);return c;}
    private MaterialButton button(String label,Runnable action){MaterialButton b=new MaterialButton(this);b.setText(label);b.setAllCaps(false);b.setCornerRadius(dp(28));b.setMinHeight(dp(54));b.setOnClickListener(v->action.run());return b;}
    private int methodColor(int kind){int target=kind==0?0xffead7fa:kind==1?0xfff3dfaa:kind==2?container:0xffd2e5fc;return ColorUtils.blendARGB(surface,target,ColorUtils.calculateLuminance(surface)>.5?.65f:.16f);}
    private void method(LinearLayout parent,int kind,String title,String subtitle){
        MaterialCardView c=card(methodColor(kind),26);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);if(parent.getChildCount()>0)lp.leftMargin=dp(12);parent.addView(c,lp);
        LinearLayout content=column();content.setPadding(dp(14),dp(12),dp(14),dp(20));c.addView(content);
        DoorMotionView icon=new DoorMotionView(this,kind,ink,methodColor(kind));icon.animate(false);content.addView(icon,new LinearLayout.LayoutParams(dp(65),dp(65)));
        TextView name=text(title,17,ink);name.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));content.addView(name);space(content,5);TextView caption=text(subtitle,11,muted);caption.setMinHeight(dp(28));content.addView(caption);
        c.setClickable(true);c.setFocusable(true);c.setContentDescription(title+". "+subtitle);c.setOnClickListener(v->showMethod(kind));
        for(int i=0;i<content.getChildCount();i++)content.getChildAt(i).setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    }
    private LinearLayout openSheet(){
        if(sheet!=null)sheet.dismiss();bleDetails=null;totpNumber=null;totpRemaining=null;sheetMotion=null;selected=-1;sheet=new BottomSheetDialog(this);LinearLayout body=column();body.setPadding(dp(26),dp(18),dp(26),dp(30));
        TextView handle=text("━━━━",15,muted);handle.setGravity(Gravity.CENTER);body.addView(handle);space(body,8);
        ScrollView scroll=new ScrollView(this);scroll.addView(body);sheet.setContentView(scroll);
        sheet.setOnDismissListener(d->{if(sheet!=d)return;sheet=null;sheetMotion=null;bleDetails=null;totpNumber=null;totpRemaining=null;selected=-1;});
        sheet.show();sheet.getBehavior().setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED);
        sheet.getBehavior().setMaxWidth(dp(560));
        View panel=sheet.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if(panel!=null){ViewGroup.MarginLayoutParams margins=(ViewGroup.MarginLayoutParams)panel.getLayoutParams();margins.leftMargin=dp(12);margins.rightMargin=dp(12);margins.bottomMargin=dp(12);panel.setLayoutParams(margins);
            if(panel.getBackground() instanceof com.google.android.material.shape.MaterialShapeDrawable){com.google.android.material.shape.MaterialShapeDrawable bg=(com.google.android.material.shape.MaterialShapeDrawable)panel.getBackground();bg.setShapeAppearanceModel(bg.getShapeAppearanceModel().toBuilder().setAllCornerSizes(dp(30)).build());}}
        Window w=sheet.getWindow();if(w!=null){w.addFlags(WindowManager.LayoutParams.FLAG_SECURE);w.setDimAmount(.24f);if(Build.VERSION.SDK_INT>=31&&getWindowManager().isCrossWindowBlurEnabled()){w.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);WindowManager.LayoutParams a=w.getAttributes();a.setBlurBehindRadius(dp(22));w.setAttributes(a);}}
        if(ValueAnimator.areAnimatorsEnabled()){body.setTranslationY(dp(20));body.setAlpha(0);body.animate().translationY(0).alpha(1).setDuration(450).setInterpolator(new OvershootInterpolator(.55f)).start();}
        return body;
    }
    private void center(LinearLayout body,String title,String subtitle){sheetTitle=text(title,28,ink);sheetTitle.setGravity(Gravity.CENTER);body.addView(sheetTitle);space(body,9);sheetSubtitle=text(subtitle,14,muted);sheetSubtitle.setGravity(Gravity.CENTER);sheetSubtitle.setLineSpacing(dp(3),1);body.addView(sheetSubtitle);space(body,22);}
    public void showMethod(int kind){
        LinearLayout body=openSheet();selected=kind;sheetMotion=new DoorMotionView(this,kind,ink,methodColor(kind));body.addView(sheetMotion,new LinearLayout.LayoutParams(-1,dp(190)));
        if(kind==2){
            center(body,"A little closer","Hold your phone near the door.\nThis is a BLE dry run.");
            bleDetails=text("",14,muted);bleDetails.setGravity(Gravity.CENTER);body.addView(bleDetails);space(body,15);
            body.addView(button("Start BLE test",()->command("START")));body.addView(button("Set up & calibrate",this::showBleSetup));body.addView(button("Stop testing",this::stopBle));
        }else if(kind==1){
            center(body,"Your little secret","Use an Authenticator code, or save its setup key to generate codes here. Nothing is sent to the door.");
            totpNumber=text("",38,ink);totpNumber.setTypeface(Typeface.MONOSPACE);totpNumber.setGravity(Gravity.CENTER);body.addView(totpNumber);
            totpRemaining=text("",12,muted);totpRemaining.setGravity(Gravity.CENTER);body.addView(totpRemaining);
            TextInputEditText input=field(body,"Six-digit Authenticator code",InputType.TYPE_CLASS_NUMBER);input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(6)});
            body.addView(button("Preview code flow",()->{try{String entered=input.getText().toString();java.security.Key key=TotpKeys.key();if(entered.isEmpty()&&key!=null)entered=LegacyProtocol.code(key,System.currentTimeMillis()/1000);LegacyProtocol.testRequest(entered);playPreview("Preparing your code…","Code flow preview complete","No code was sent or verified by the home server.");}catch(Exception e){toast(e.getMessage());}}));
            body.addView(button("Set up in-app codes",this::showTotpSetup));
        }else{
            center(body,kind==0?"One little tap":"Oh, hello you",kind==0?"A preview of the button unlock animation.\nThe door will stay closed.":"A preview of the face verification animation.\nNo camera or face check runs here yet.");
            body.addView(button(kind==0?"Preview tap animation":"Preview face animation",()->playPreview(kind==0?"Opening, in the preview…":"Looking for that familiar face…","Animation complete","Preview only · the door stayed closed")));
        }
        renderStatus();
    }
    private void playPreview(String loading,String title,String detail){
        int sequence=++previewSequence;BottomSheetDialog target=sheet;DoorMotionView motion=sheetMotion;TextView heading=sheetTitle,sub=sheetSubtitle;
        motion.complete(false);heading.setText(loading);sub.setText("Animation preview · no door operation");
        handler.postDelayed(()->{if(sheet!=target||sequence!=previewSequence)return;motion.complete(true);heading.setText(title);sub.setText(detail);heading.announceForAccessibility(title+". "+detail);},ValueAnimator.areAnimatorsEnabled()?1700:0);
    }
    private TextInputEditText field(LinearLayout body,String label,int type){
        TextInputLayout wrap=new TextInputLayout(this);wrap.setHint(label);wrap.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);wrap.setBoxCornerRadii(dp(18),dp(18),dp(18),dp(18));
        TextInputEditText input=new TextInputEditText(wrap.getContext());input.setInputType(type);input.setSingleLine(true);input.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);wrap.addView(input);body.addView(wrap);space(body,8);return input;
    }
    public void showBleSetup(){
        LinearLayout body=openSheet();body.addView(text("Make yourself at home",27,ink));space(body,8);body.addView(text("One setup code per phone. Calibration helps the door learn what close means for you.",14,muted));space(body,18);
        boolean enrolled=!Keys.prefs(this).getString("phone","").isEmpty();body.addView(text(enrolled?"This phone is enrolled":"1 · Enroll this phone",16,primary));space(body,8);
        TextInputEditText code=field(body,"Phone setup code",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        body.addView(button(enrolled?"Replace enrollment":"Enroll phone",()->{
            Runnable save=()->{try{stopBle();Keys.enroll(this,code.getText().toString());code.setText("");toast("Enrolled. Start the BLE test next.");}catch(Exception e){toast(e.getMessage());}};
            if(enrolled)new MaterialAlertDialogBuilder(this).setTitle("Replace this phone's setup?").setMessage("This clears its saved BLE calibration. In-app TOTP codes stay intact.").setNegativeButton("Keep current",null).setPositiveButton("Replace",(d,w)->save.run()).show();else save.run();
        }));space(body,12);body.addView(button("Start BLE test",()->command("START")));space(body,12);
        body.addView(text("2 · Find your sweet spot",19,ink));space(body,8);body.addView(text("Hold the phone 5–15 cm from the board for NEAR. Then stand 2–3 metres away for AWAY. Collect 15 samples at each position.",14,muted));
        body.addView(button("Collect NEAR samples",()->command("NEAR")));body.addView(button("Collect AWAY samples",()->command("AWAY")));body.addView(button("Save calibration",()->command("SAVE")));
        bleDetails=text("",14,primary);body.addView(bleDetails);space(body,14);body.addView(text("Move away after each accepted test, then return. Allow at least 15 seconds between attempts. Keep the app open for calibration.",13,muted));
        body.addView(button("Stop testing",this::stopBle));body.addView(button("Android app settings",()->startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:"+getPackageName())))));renderStatus();
    }
    private void showTotpSetup(){
        LinearLayout body=openSheet();body.addView(text("Codes, all in one place",27,ink));space(body,12);body.addView(text("Enter the same setup key used by Dad's Authenticator. It is stored in Android Keystore, stays on this phone, and is separate from BLE enrollment.",14,muted));space(body,18);
        TextInputEditText secret=field(body,"Authenticator setup key",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        body.addView(button("Save setup key",()->{try{TotpKeys.save(secret.getText().toString());secret.setText("");showMethod(1);}catch(Exception e){toast("Could not save setup key: "+e.getMessage());}}));
        body.addView(button("Remove saved key",()->new MaterialAlertDialogBuilder(this).setTitle("Remove in-app codes?").setMessage("You can still enter codes from Google Authenticator.").setNegativeButton("Keep",null).setPositiveButton("Remove",(d,w)->{try{TotpKeys.remove();showMethod(1);}catch(Exception e){toast("Could not remove key");}}).show()));
    }
    private void renderStatus(){
        if(liveStatus==null)return;String current=EntryService.currentStatus;
        String log=Keys.prefs(this).getString("history","");
        liveStatus.setText(current.contains("Signal")?"Nearby entry is listening":current.contains("Connected")?"Your phone is nearby":current.contains("Stopped")?"Ready when you are":current.isEmpty()?"Ready when you are":"Nearby entry · testing");
        history.setText(log.isEmpty()?"Your test arrivals will appear here.":log);
        if(bleDetails!=null)bleDetails.setText(current.isEmpty()?"Tap Start to begin.":current);
        long at=DoorSettings.prefs(this).getLong("feedback_at",0);
        if(FeedbackPolicy.fresh(at,SystemClock.elapsedRealtime())){
            String stage=DoorSettings.prefs(this).getString("feedback_stage","");
            if(at!=observedFeedback){observedFeedback=at;if(sheet==null&&resumed&&!DoorFeedback.proximity(stage))showMethod(2);}
            if(selected==2&&sheetMotion!=null){sheetMotion.complete(DoorFeedback.SUCCESS.equals(stage));sheetTitle.setText(DoorFeedback.title(stage));sheetSubtitle.setText(DoorSettings.prefs(this).getString("feedback_detail","Dry run only"));}
        }else if(selected==2&&sheetMotion!=null){
            sheetMotion.complete(false);sheetTitle.setText(DoorSettings.enabled(this)?"A little closer":"Nearby entry is paused");sheetSubtitle.setText(DoorSettings.enabled(this)?"Hold your phone near the door.\nThis is a BLE dry run.":"Tap Start BLE test to resume nearby checks.");
        }
        if(totpNumber!=null){try{java.security.Key key=TotpKeys.key();if(key==null){totpNumber.setText("");totpRemaining.setText("No in-app key saved");}else{long seconds=System.currentTimeMillis()/1000;String code=LegacyProtocol.code(key,seconds);totpNumber.setText(code.substring(0,3)+" "+code.substring(3));totpRemaining.setText("Refreshes in "+(30-seconds%30)+" seconds");}}catch(Exception e){totpNumber.setText("—");totpRemaining.setText("Could not read saved key");}}
    }
    private void stopBle(){DoorSettings.enabled(this,false);DoorFeedback.clear(this);stopService(new Intent(this,EntryService.class));EntryService.currentStatus="Stopped. Tap Start to run the BLE test.";renderStatus();}
    public void showBackgroundSetup(){
        LinearLayout body=openSheet();body.addView(text("Ready when life happens",26,ink));space(body,12);
        body.addView(text("Start once to keep nearby checks running. Stop pauses them, including restart after reboot. The actual door and internet unlock remain disconnected.",14,muted));space(body,12);
        body.addView(button("Start BLE test",()->command("START")));body.addView(button("Stop background checks",this::stopBle));
        toggle(body,"Approach notifications & vibration","alerts",true);
        toggle(body,"Floating cards over other apps","floating",false);
        body.addView(button(android.provider.Settings.canDrawOverlays(this)?"Floating-card permission is enabled":"Allow floating cards",()->startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,android.net.Uri.parse("package:"+getPackageName())))));
        body.addView(text("Floating cards appear while the phone is unlocked. When locked, Door uses notifications and vibration. Tap the notification to open the app.",13,muted));
        PowerManager power=getSystemService(PowerManager.class);
        body.addView(button(power.isIgnoringBatteryOptimizations(getPackageName())?"Background battery access is allowed":"Allow reliable background use",()->{if(!power.isIgnoringBatteryOptimizations(getPackageName()))startActivity(new Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,android.net.Uri.parse("package:"+getPackageName())));}));
        body.addView(button("Test background popup",this::previewBackgroundFeedback));
        body.addView(button("Notification & lock-screen settings",()->startActivity(new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,getPackageName()))));
        space(body,16);body.addView(text("Your door, one tap away",21,ink));
        body.addView(button("Add four-method widget",()->DoorAccess.pinWidget(this)));
        body.addView(button("Add a home-screen shortcut",()->new MaterialAlertDialogBuilder(this).setTitle("Choose a shortcut").setItems(DoorAccess.LABELS,(d,n)->DoorAccess.pinShortcut(this,n)).show()));
        body.addView(button("Add a Quick Settings tile",()->new MaterialAlertDialogBuilder(this).setTitle("Choose a tile").setItems(DoorAccess.LABELS,(d,n)->DoorAccess.addTile(this,n)).show()));
        body.addView(text("For a lock-screen entry point, choose Door under Android Device controls where supported. Your phone controls which shortcuts can appear on its lock screen. All four panels are also in the widget and Quick Settings.",13,muted));space(body,16);
        body.addView(button("Copy debug report",()->{getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("Door debug",debugReport()));toast("Debug report copied; no keys or codes included");}));
        TextView debug=text(debugReport(),12,muted);debug.setTextIsSelectable(true);body.addView(debug);
    }
    private void toggle(LinearLayout body,String label,String key,boolean fallback){com.google.android.material.materialswitch.MaterialSwitch sw=new com.google.android.material.materialswitch.MaterialSwitch(this);sw.setText(label);sw.setTextColor(ink);sw.setChecked(DoorSettings.prefs(this).getBoolean(key,fallback));sw.setPadding(0,dp(8),0,dp(8));sw.setOnCheckedChangeListener((v,checked)->{DoorSettings.prefs(this).edit().putBoolean(key,checked).apply();if(!checked)FloatingDoor.dismiss();});body.addView(sw);}
    void previewBackgroundFeedback(){moveTaskToBack(true);handler.postDelayed(()->DoorFeedback.publish(this,DoorFeedback.VERIFYING,"Popup test only · no BLE check or door operation"),1000);}
    private String debugReport(){return "Door 1.5 · DRY RUN\nAndroid "+Build.VERSION.RELEASE+" / API "+Build.VERSION.SDK_INT+"\nBackground enabled: "+DoorSettings.enabled(this)+"\nOverlay permission: "+android.provider.Settings.canDrawOverlays(this)+"\nBattery exemption: "+getSystemService(PowerManager.class).isIgnoringBatteryOptimizations(getPackageName())+"\nNotifications: "+getSystemService(android.app.NotificationManager.class).areNotificationsEnabled()+"\nSession starts/completions: "+EntryService.sessionsStarted+" / "+EntryService.sessionsCompleted+"\nAuthenticated responses: "+EntryService.authenticatedResponses+"\nCalibration: "+Keys.prefs(this).getInt("threshold",0)+" dBm\n"+EntryService.currentStatus+"\nArmed for next approach: "+Keys.prefs(this).getBoolean("runtime_armed",true)+"\nAway signal needed: below "+(Keys.prefs(this).getInt("threshold",0)-8)+" dBm for 3 seconds\nFeedback routes:\n"+DoorSettings.prefs(this).getString("feedback_routes","No feedback recorded yet")+"\n"+DoorSettings.prefs(this).getString("resume_error","")+"\n\n"+Keys.prefs(this).getString("history","");}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    private boolean permissions(){List<String> need=new ArrayList<>();String[] required=Build.VERSION.SDK_INT>=31?new String[]{Manifest.permission.BLUETOOTH_SCAN,Manifest.permission.BLUETOOTH_CONNECT}:new String[]{Manifest.permission.ACCESS_FINE_LOCATION};for(String p:required)if(checkSelfPermission(p)!=PackageManager.PERMISSION_GRANTED)need.add(p);if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)need.add(Manifest.permission.POST_NOTIFICATIONS);if(!need.isEmpty()){requestPermissions(need.toArray(new String[0]),1);toast("Allow permissions, then tap Start again");return false;}return true;}
    private void command(String action){if(!permissions())return;try{Keys.key(this);DoorSettings.enabled(this,true);startForegroundService(new Intent(this,EntryService.class).setAction(action));}catch(Exception e){toast(e.getMessage());}}
    @Override protected void onResume(){super.onResume();visible=true;resumed=true;AppUpdates.onResume(this);FloatingDoor.dismiss();handler.post(refresh);if(!routed){routed=true;int method=DoorIntents.method(getIntent());if(method>=0)handler.post(()->showMethod(method));}if(DoorSettings.enabled(this))try{startForegroundService(new Intent(this,EntryService.class).setAction("RESUME"));}catch(RuntimeException ignored){}}
    @Override protected void onNewIntent(Intent i){super.onNewIntent(i);setIntent(i);int method=DoorIntents.method(i);if(method>=0)handler.post(()->showMethod(method));}
    @Override protected void onPause(){visible=false;resumed=false;handler.removeCallbacks(refresh);super.onPause();}
    @Override protected void onDestroy(){AppUpdates.onDestroy(this);handler.removeCallbacksAndMessages(null);if(sheet!=null)sheet.dismiss();super.onDestroy();}
}

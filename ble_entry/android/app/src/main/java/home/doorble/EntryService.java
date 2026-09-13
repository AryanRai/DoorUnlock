package home.doorble;

import android.app.*;
import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.*;
import android.os.*;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.*;

public class EntryService extends Service {
    public static volatile String currentStatus="";
    final Handler h=new Handler(Looper.getMainLooper());
    BluetoothAdapter adapter;BluetoothLeScanner scanner;BluetoothGatt gatt;BluetoothDevice knownBoard;
    BluetoothGattCharacteristic challenge,request,result;
    Key key;String board,phone,nonce,op,value,mode="",history="";
    boolean running=false,scanning=false,ready=false,busy=false,armed=true;
    int threshold=0,pendingThreshold=0,nearCount=0;
    long lastAccept=0,awaySince=0,lastScanAt=-8000,sessionStarted=0;
    int failures=0;
    boolean authenticatedSession=false,discovering=false,passive=false,attemptFeedback=false;
    long lastNotification=0;
    final FeedbackPolicy feedbackPolicy=new FeedbackPolicy();
    final ProximityPolicy proximity=new ProximityPolicy();
    final Runnable proximityWatch=new Runnable(){public void run(){if(!running)return;if(mode.isEmpty()){String change=proximity.tick(SystemClock.elapsedRealtime());if(change!=null)proximityFeedback(change,"Door signal lost · still checking nearby");}h.postDelayed(this,5000);}};
    void proximityFeedback(String change,String detail){
        if(ProximityPolicy.CLOSE.equals(change)&&(attemptFeedback||(lastAccept!=0&&SystemClock.elapsedRealtime()-lastAccept<10000)))return;
        if(ProximityPolicy.OUT.equals(change)){
            long delay=lastAccept==0?0:4000-(SystemClock.elapsedRealtime()-lastAccept);
            if(delay>0){h.postDelayed(()->{if(running&&!proximity.nearby())proximityFeedback(change,detail);},delay);return;}
            attemptFeedback=false;
        }
        DoorFeedback.publish(this,change,detail);event(DoorFeedback.title(change)+" · "+detail);
    }
    public static volatile int sessionsStarted=0, sessionsCompleted=0, authenticatedResponses=0;
    final Random retryRandom=new Random();
    final Runnable scanTask=this::connectOrScan;
    final Runnable sessionDeadline=()->releaseSlot("Session finished; allowing other phones to connect",false);
    final List<Integer> near=new ArrayList<>(),away=new ArrayList<>();
    final Map<String,Long> ignored=new HashMap<>();
    PowerManager.WakeLock wake;
    final Runnable timeout=()->fail("BLE request timed out; reconnecting");
    final Runnable poll=new Runnable(){public void run(){if(!running||!ready||busy)return;long budget=SessionPolicy.sessionBudget(!mode.isEmpty(),awaySince!=0&&!armed);if(sessionStarted!=0&&SystemClock.elapsedRealtime()-sessionStarted>=budget){releaseSlot("Taking turns with household phones",false);return;}send(pendingThreshold!=0?"C":"P",pendingThreshold!=0?Integer.toString(pendingThreshold):"0");}};
    public class LocalBinder extends Binder { public EntryService service(){return EntryService.this;} }
    public IBinder onBind(Intent i){return new LocalBinder();}
    public void onCreate(){super.onCreate();DoorFeedback.channels(this);wake=getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"Door:BLE-session");wake.setReferenceCounted(false);
        IntentFilter filter=new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);filter.addAction(Intent.ACTION_SCREEN_OFF);
        if(Build.VERSION.SDK_INT>=33)registerReceiver(deviceState,filter,Context.RECEIVER_EXPORTED);else registerReceiver(deviceState,filter);
    }
    final BroadcastReceiver deviceState=new BroadcastReceiver(){public void onReceive(Context c,Intent i){
        if(Intent.ACTION_SCREEN_OFF.equals(i.getAction())){FloatingDoor.dismiss();return;}
        int state=i.getIntExtra(BluetoothAdapter.EXTRA_STATE,-1);
        if(state==BluetoothAdapter.STATE_ON&&running){status("Bluetooth is back; resuming nearby checks");holdCpu();connectOrScan();}
        else if(state==BluetoothAdapter.STATE_OFF||state==BluetoothAdapter.STATE_TURNING_OFF){proximity.reset();DoorFeedback.clear(c);h.removeCallbacks(scanTask);releaseSlot("Bluetooth is off; waiting for it to return",false);releaseCpu();}
    }};
    boolean bluetoothOn(){try{return adapter!=null&&adapter.isEnabled();}catch(SecurityException e){DoorSettings.enabled(this,false);DoorSettings.prefs(this).edit().putString("resume_error","Bluetooth permission removed. Open Door and grant it again.").apply();stopSelf();return false;}}
    void holdCpu(){if(wake!=null)wake.acquire(25000);}
    void releaseCpu(){if(wake!=null&&wake.isHeld())wake.release();}
    Notification notification(String text){Intent open=DoorIntents.open(this,2);PendingIntent pi=PendingIntent.getActivity(this,0,open,PendingIntent.FLAG_IMMUTABLE);PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,EntryService.class).setAction("STOP"),PendingIntent.FLAG_IMMUTABLE);return new Notification.Builder(this,"entry").setSmallIcon(R.drawable.ic_door).setContentTitle("Door · Background BLE").setContentText(text).setContentIntent(pi).setOngoing(true).setOnlyAlertOnce(true).setVisibility(Notification.VISIBILITY_PUBLIC).addAction(new Notification.Action.Builder(null,"Stop",stop).build()).build();}
    void status(String text){currentStatus=text;long now=SystemClock.elapsedRealtime();if(now-lastNotification>=2000){lastNotification=now;getSystemService(NotificationManager.class).notify(1,notification(text));}}
    void event(String text){history=new java.text.SimpleDateFormat("HH:mm:ss",Locale.getDefault()).format(new Date())+"  "+text+"\n"+history;if(history.length()>5000)history=history.substring(0,5000);Keys.prefs(this).edit().putString("history",history).apply();status(text);}
    public int onStartCommand(Intent intent,int flags,int id){
        String action=intent==null?"RESUME":intent.getAction();
        if("STOP".equals(action)){DoorSettings.enabled(this,false);DoorFeedback.clear(this);stopSelf();return START_NOT_STICKY;}
        if(("RESUME".equals(action)||"BLE_WAKE".equals(action))&&!DoorSettings.enabled(this)){stopSelf();return START_NOT_STICKY;}
        try{startForeground(1,notification("Starting nearby entry · dry run"));}catch(SecurityException e){DoorSettings.prefs(this).edit().putString("resume_error","Bluetooth permission is required. Open Door and tap Start.").apply();stopSelf();return START_NOT_STICKY;}
        boolean startedNow=!running;
        if(!running){try{
            key=Keys.key(this);board=Keys.prefs(this).getString("board","");phone=Keys.prefs(this).getString("phone","");threshold=Keys.prefs(this).getInt("threshold",0);history=Keys.prefs(this).getString("history","");
            armed=Keys.prefs(this).getBoolean("runtime_armed",true);lastAccept=Keys.prefs(this).getLong("last_accept_elapsed",0);if(lastAccept>SystemClock.elapsedRealtime())lastAccept=0;
            adapter=getSystemService(BluetoothManager.class).getAdapter();if(adapter==null)throw new IllegalStateException("This phone has no Bluetooth adapter");
            DoorSettings.enabled(this,true);DoorSettings.prefs(this).edit().remove("resume_error").apply();running=true;DoorFeedback.clear(this);
            h.postDelayed(proximityWatch,5000);
            if(!"BLE_WAKE".equals(action)){holdCpu();connectOrScan();}
        }catch(Exception e){currentStatus=e.getMessage();DoorSettings.enabled(this,false);DoorSettings.prefs(this).edit().putString("resume_error",currentStatus).apply();stopSelf();return START_NOT_STICKY;}}
        if("BLE_WAKE".equals(action)){
            int error=intent.getIntExtra("scan_error",0);BluetoothDevice found=intent.getParcelableExtra("device");
            if(error!=0){passive=false;status("Passive scan error "+error+"; retrying active discovery");holdCpu();scan();}
            else if(found!=null&&gatt==null&&(passive||startedNow)){knownBoard=found;holdCpu();connectKnown();}else if(found==null&&gatt==null&&!passive){holdCpu();scan();}
        }
        if("NEAR".equals(action)){proximity.reset();mode="NEAR";near.clear();away.clear();pendingThreshold=0;nearCount=0;event("Hold phone 5–15 cm from board: collecting 15 NEAR samples");}
        if("AWAY".equals(action)){proximity.reset();mode="AWAY";away.clear();nearCount=0;event("Stand 2–3 metres away: collecting 15 AWAY samples");}
        if("SAVE".equals(action)){try{pendingThreshold=Protocol.threshold(near,away);mode="SAVE";event("Saving threshold "+pendingThreshold+" dBm to board");}catch(Exception e){event(e.getMessage());}}
        return START_STICKY;
    }
    void connectOrScan(){if(!running||scanning||passive||gatt!=null)return;if(!bluetoothOn()){status("Bluetooth is off; waiting for it to return");releaseCpu();return;}if(knownBoard!=null&&failures<2)connectKnown();else scan();}
    void connectKnown(){
        try{holdCpu();stopScan();status("Connecting to board...");authenticatedSession=false;sessionStarted=0;
            gatt=knownBoard.connectGatt(this,false,callback,BluetoothDevice.TRANSPORT_LE);
            if(gatt==null){fail("Connection could not start");return;}
            sessionsStarted++;h.postDelayed(sessionDeadline,SessionPolicy.HARD_LIMIT_MS);watchdog(12000);
        }catch(SecurityException e){status("Bluetooth permission removed");stopSelf();}
    }
    void scan(){if(!running||scanning||passive||gatt!=null)return;if(!bluetoothOn()){status("Bluetooth is off; waiting for it to return");releaseCpu();return;}holdCpu();h.removeCallbacks(scanTask);long wait=SessionPolicy.scanDelay(SystemClock.elapsedRealtime(),lastScanAt,0);if(wait>0){h.postDelayed(scanTask,wait);return;}lastScanAt=SystemClock.elapsedRealtime();try{scanner=adapter.getBluetoothLeScanner();if(scanner==null)throw new IllegalStateException("Bluetooth is unavailable");scanning=true;status("Searching for DoorBLE-"+board.substring(6));scanner.startScan(Collections.singletonList(new ScanFilter.Builder().setServiceUuid(new ParcelUuid(Protocol.SERVICE)).build()),new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),scanCallback);h.postDelayed(scanWindow,15000);}catch(SecurityException e){status("Bluetooth permission removed. Grant permission and restart.");stopSelf();}catch(Exception e){scanning=false;status("Scan failed: "+e.getMessage());scheduleScan(10000);}}
    final Runnable scanWindow=()->{stopScan();if(running&&gatt==null)passiveScan();};
    PendingIntent scanIntent(){return PendingIntent.getBroadcast(this,41,new Intent(this,BleWakeReceiver.class).setAction("home.doorble.SCAN"),PendingIntent.FLAG_UPDATE_CURRENT|(Build.VERSION.SDK_INT>=31?PendingIntent.FLAG_MUTABLE:0));}
    void passiveScan(){
        if(!running||gatt!=null)return;stopScan();
        try{if(!bluetoothOn()){status("Bluetooth is off; waiting for it to return");releaseCpu();return;}
            scanner=adapter.getBluetoothLeScanner();if(scanner==null)throw new IllegalStateException("Scanner unavailable");
            int result=scanner.startScan(Collections.singletonList(new ScanFilter.Builder().setServiceUuid(new ParcelUuid(Protocol.SERVICE)).build()),new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_POWER).build(),scanIntent());
            if(result!=0)throw new IllegalStateException("Scan error "+result);passive=true;status("Waiting for the door · background discovery active");releaseCpu();
        }catch(SecurityException e){DoorSettings.enabled(this,false);DoorSettings.prefs(this).edit().putString("resume_error","Bluetooth permission removed; open Door to restore it").apply();stopSelf();}
        catch(Exception e){status("Discovery retry: "+e.getMessage());holdCpu();scheduleScan(15000);}
    }
    void stopScan(){h.removeCallbacks(scanWindow);if(adapter!=null){try{BluetoothLeScanner current=adapter.getBluetoothLeScanner();if(current!=null)current.stopScan(scanIntent());}catch(SecurityException ignored){}catch(RuntimeException ignored){}passive=false;}if(scanning&&scanner!=null){try{scanner.stopScan(scanCallback);}catch(SecurityException ignored){}}scanning=false;}
    final ScanCallback scanCallback=new ScanCallback(){
        public void onScanResult(int type,ScanResult r){h.post(()->{if(!scanning||gatt!=null)return;String addr=r.getDevice().getAddress();if(ignored.getOrDefault(addr,0L)>SystemClock.elapsedRealtime())return;
            String name=r.getScanRecord()==null?null:r.getScanRecord().getDeviceName();if(name!=null&&!name.equals("DoorBLE-"+board.substring(6)))return;
            knownBoard=r.getDevice();connectKnown();
        });}
        public void onScanFailed(int error){h.post(()->{scanning=false;status("Bluetooth scan error "+error);scheduleScan(10000);});}
    };
    void watchdog(long ms){h.removeCallbacks(timeout);h.postDelayed(timeout,ms);}
    final BluetoothGattCallback callback=new BluetoothGattCallback(){
        public void onConnectionStateChange(BluetoothGatt g,int s,int state){h.post(()->{if(g!=gatt)return;if(s!=BluetoothGatt.GATT_SUCCESS||state==BluetoothProfile.STATE_DISCONNECTED){fail("Board disconnected; retrying");return;}if(state==BluetoothProfile.STATE_CONNECTED){try{if(!g.requestMtu(185)){fail("Could not negotiate BLE packet size");return;}watchdog(8000);}catch(SecurityException e){status("Bluetooth permission removed");stopSelf();}}});}
        public void onMtuChanged(BluetoothGatt g,int mtu,int s){h.post(()->{if(g!=gatt||discovering||ready)return;if(s!=0||mtu<160){fail("BLE MTU too small for authentication");return;}try{discovering=true;if(!g.discoverServices()){fail("Service discovery could not start");return;}watchdog(8000);}catch(SecurityException e){status("Bluetooth permission removed");stopSelf();}});}
        public void onServicesDiscovered(BluetoothGatt g,int s){h.post(()->{if(g!=gatt||ready)return;BluetoothGattService svc=g.getService(Protocol.SERVICE);if(s!=0||svc==null){fail("Door BLE service missing");return;}challenge=svc.getCharacteristic(Protocol.CHALLENGE);request=svc.getCharacteristic(Protocol.REQUEST);result=svc.getCharacteristic(Protocol.RESULT);if(challenge==null||request==null||result==null){fail("Incomplete Door BLE service");return;}h.removeCallbacks(timeout);ready=true;sessionStarted=SystemClock.elapsedRealtime();h.postDelayed(poll,1100);status("Connected; gathering signal samples");});}
        @SuppressWarnings("deprecation") public void onCharacteristicRead(BluetoothGatt g,BluetoothGattCharacteristic c,int s){if(Build.VERSION.SDK_INT<33)read(g,c,c.getValue(),s);}
        public void onCharacteristicRead(BluetoothGatt g,BluetoothGattCharacteristic c,byte[] bytes,int s){read(g,c,bytes,s);}
        public void onCharacteristicWrite(BluetoothGatt g,BluetoothGattCharacteristic c,int s){h.post(()->{if(g!=gatt||!busy)return;if(s!=0){fail("BLE write rejected: "+s);return;}try{if(!g.readCharacteristic(result))fail("Could not read board result");else watchdog(6000);}catch(SecurityException e){status("Bluetooth permission removed");stopSelf();}});}
    };
    void send(String operation,String argument){if(!ready||busy)return;op=operation;value=argument;busy=true;try{if(!gatt.readCharacteristic(challenge)){fail("Could not request challenge");return;}watchdog(6000);}catch(SecurityException e){status("Bluetooth permission removed");stopSelf();}}
    void read(BluetoothGatt g,BluetoothGattCharacteristic c,byte[] bytes,int s){final byte[] copy=bytes==null?new byte[0]:bytes.clone();h.post(()->{if(g!=gatt||!busy)return;if(s!=0){fail("BLE read failed: "+s);return;}try{
        String text=new String(copy,StandardCharsets.UTF_8);
        if(c.getUuid().equals(Protocol.CHALLENGE)){
            String[] parts=text.split("\\|");if(parts.length!=2||!parts[0].equals(board)||!parts[1].matches("[0-9a-f]{32}")){knownBoard=null;ignored.put(g.getDevice().getAddress(),SystemClock.elapsedRealtime()+60000);throw new SecurityException("Wrong board identity");}
            nonce=parts[1];byte[] payload=Protocol.request(key,board,phone,nonce,op,value).getBytes(StandardCharsets.UTF_8);
            boolean ok;if(Build.VERSION.SDK_INT>=33)ok=g.writeCharacteristic(request,payload,BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)==BluetoothStatusCodes.SUCCESS;else{request.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);request.setValue(payload);ok=g.writeCharacteristic(request);}if(!ok)throw new IllegalStateException("Write did not start");watchdog(6000);
        }else if(c.getUuid().equals(Protocol.RESULT)){
            String[] response=Protocol.verify(key,board,phone,nonce,text);authenticatedSession=true;authenticatedResponses++;failures=0;h.removeCallbacks(timeout);busy=false;process(response[0],Integer.parseInt(response[1]));
        }
    }catch(SecurityException e){fail("Authentication/permission error: "+e.getMessage());}catch(Exception e){fail("Authentication/connection error: "+e.getMessage());}});}
    void process(String code,int rssi){
        long now=SystemClock.elapsedRealtime();
        if(op.equals("C")){if(code.equals("CALIBRATED")){threshold=pendingThreshold;Keys.prefs(this).edit().putInt("threshold",threshold).apply();pendingThreshold=0;mode="";nearCount=0;event("Calibration saved: "+threshold+" dBm. Move away, then approach.");}else{pendingThreshold=0;mode="";event("Calibration rejected: "+code);}releaseSlot("Calibration: "+code+"; slot released",false);return;}
        if(op.equals("U")){nearCount=0;if(code.equals("WOULD_UNLOCK")){armed=false;lastAccept=now;awaySince=0;Keys.prefs(this).edit().putBoolean("runtime_armed",false).putLong("last_accept_elapsed",now).apply();DoorFeedback.publish(this,DoorFeedback.SUCCESS,"Phone verified nearby · door not operated");attemptFeedback=false;event("WOULD UNLOCK · "+rssi+" dBm · door not operated");}else{if(code.equals("WAIT_AWAY")){armed=false;Keys.prefs(this).edit().putBoolean("runtime_armed",false).apply();}if(attemptFeedback){DoorFeedback.publish(this,DoorFeedback.HELD,"Entry held: "+code+" · dry run");attemptFeedback=false;}event("Entry held: "+code+" · "+rssi+" dBm");}releaseSlot("Approach checked; sharing the BLE slots",false);return;}
        if(!code.equals("PROBE")){nearCount=0;status(code.equals("SAMPLING")?"Gathering stable signal…":"Board: "+code);if(!code.equals("SAMPLING")){releaseSlot("Board: "+code,false);return;}h.postDelayed(poll,700);return;}
        if(mode.equals("NEAR")||mode.equals("AWAY")){List<Integer> list=mode.equals("NEAR")?near:away;if(list.size()<15)list.add(rssi);status(mode+" "+list.size()+"/15 · "+rssi+" dBm"+(list.size()>=15?(mode.equals("NEAR")?"\nNow move away and collect AWAY samples.":"\nTap Save calibration."):""));if(list.size()>=15){releaseSlot(mode+" samples collected; slot released",false);return;}h.postDelayed(poll,600);return;}
        if(threshold==0){status("Connected · "+rssi+" dBm\nCollect NEAR and AWAY calibration samples.");releaseSlot("Connected; collect NEAR and AWAY samples to calibrate",false);return;}
        String proximityChange=proximity.observe(rssi,threshold,now);
        if(rssi<threshold-8){if(awaySince==0)awaySince=now;if(now-awaySince>=3000&&!armed){armed=true;Keys.prefs(this).edit().putBoolean("runtime_armed",true).apply();event("Away confirmed · ready for a new approach · "+rssi+" dBm");}}else awaySince=0;
        nearCount=rssi>=threshold?nearCount+1:0;
        if(armed&&nearCount==1&&(lastAccept==0||now-lastAccept>=15000)&&feedbackPolicy.begin(now)){attemptFeedback=true;DoorFeedback.publish(this,DoorFeedback.VERIFYING,"Checking the nearby phone · dry run");}
        if(proximityChange!=null)proximityFeedback(proximityChange,ProximityPolicy.CLOSE.equals(proximityChange)?(armed?"Door nearby · come closer to verify":"Door nearby · move away before another entry"):"Outside the nearby zone · still listening");
        status("Signal "+rssi+" dBm · threshold "+threshold+"\n"+(armed?"Ready for close approach":"Move away to rearm")+" · dry run");
        if(armed&&nearCount>=3&&(lastAccept==0||now-lastAccept>=15000)){send("U","0");return;}
        h.postDelayed(poll,500);
    }
    void scheduleScan(long desired){
        h.removeCallbacks(scanTask);
        if(running&&adapter!=null&&bluetoothOn())h.postDelayed(scanTask,knownBoard!=null&&failures<2?desired:SessionPolicy.scanDelay(SystemClock.elapsedRealtime(),lastScanAt,desired));
    }
    void releaseSlot(String reason,boolean failed){
        if(attemptFeedback){DoorFeedback.publish(this,DoorFeedback.HELD,failed?"Check interrupted; trying again":"Hold closer for a stable signal");attemptFeedback=false;}
        h.removeCallbacks(timeout);h.removeCallbacks(poll);h.removeCallbacks(sessionDeadline);
        ready=false;discovering=false;busy=false;nearCount=0;awaySince=0;sessionStarted=0;stopScan();
        BluetoothGatt old=gatt;gatt=null;
        if(old!=null){try{old.disconnect();old.close();}catch(SecurityException ignored){}}
        if(authenticatedSession){sessionsCompleted++;authenticatedSession=false;}
        if(failed)failures=Math.min(failures+1,4);
        long retry=SessionPolicy.retryDelay(failures,retryRandom.nextInt(2001));
        if(running){status(reason+"\nNext check in "+((retry+999)/1000)+" seconds");if(adapter==null||!bluetoothOn()){releaseCpu();return;}if(failures>=2){passiveScan();return;}holdCpu();scheduleScan(retry);}
    }
    void fail(String reason){releaseSlot(reason,true);}
    public void onDestroy(){running=false;try{unregisterReceiver(deviceState);}catch(IllegalArgumentException ignored){}FloatingDoor.dismiss();h.removeCallbacksAndMessages(null);stopScan();if(gatt!=null){try{gatt.disconnect();gatt.close();}catch(SecurityException ignored){}}if(wake!=null&&wake.isHeld())wake.release();currentStatus=DoorSettings.enabled(this)?"Background service restarting":"Stopped. Tap Start to run the BLE test.";stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy();}
}

package home.doorble;

import android.app.PendingIntent;
import android.os.Build;
import android.service.controls.*;
import android.service.controls.actions.ControlAction;
import android.service.controls.templates.ControlTemplate;
import androidx.annotation.RequiresApi;
import java.util.*;
import java.util.concurrent.Flow;
import java.util.function.Consumer;

/** Android device-controls entry points; each opens a panel and never operates a lock. */
@RequiresApi(30)
public class DoorControls extends ControlsProviderService {
    private final String[] names={"Button preview","TOTP codes","Nearby BLE test","Face preview"};
    private Control control(int n,boolean stateful){
        PendingIntent intent=DoorIntents.pending(this,n);
        if(!stateful)return new Control.StatelessBuilder("door-"+n,intent).setTitle(names[n]).setSubtitle("Open Door panel").setStructure("Front door · dry run").setDeviceType(DeviceTypes.TYPE_GENERIC_ON_OFF).build();
        Control.StatefulBuilder b=new Control.StatefulBuilder("door-"+n,intent).setTitle(names[n]).setSubtitle("Open Door panel").setStructure("Front door · dry run").setDeviceType(DeviceTypes.TYPE_GENERIC_ON_OFF).setStatus(Control.STATUS_OK).setStatusText("Open in app · no door operation").setControlTemplate(ControlTemplate.getNoTemplateObject());
        if(Build.VERSION.SDK_INT>=33)b.setAuthRequired(true);return b.build();
    }
    private Flow.Publisher<Control> publisher(List<Control> values){return subscriber->subscriber.onSubscribe(new Flow.Subscription(){int next;boolean cancelled;public synchronized void request(long count){if(cancelled)return;if(count<=0){cancelled=true;subscriber.onError(new IllegalArgumentException("Positive demand required"));return;}while(count-->0&&next<values.size()&&!cancelled)subscriber.onNext(values.get(next++));if(next==values.size()&&!cancelled){cancelled=true;subscriber.onComplete();}}public synchronized void cancel(){cancelled=true;}});}
    @Override public Flow.Publisher<Control> createPublisherForAllAvailable(){List<Control> all=new ArrayList<>();for(int i=0;i<4;i++)all.add(control(i,false));return publisher(all);}
    @Override public Flow.Publisher<Control> createPublisherFor(List<String> ids){List<Control> selected=new ArrayList<>();for(int i=0;i<4;i++)if(ids.contains("door-"+i))selected.add(control(i,true));return publisher(selected);}
    @Override public void performControlAction(String id,ControlAction action,Consumer<Integer> result){result.accept(ControlAction.RESPONSE_FAIL);}
}

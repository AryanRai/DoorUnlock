package home.doorble;
import android.app.PendingIntent;
import android.content.*;
import android.net.Uri;

public final class DoorIntents {
    public static Intent open(Context c,int method){return new Intent(c,MainActivity.class).setAction(Intent.ACTION_VIEW).setData(Uri.parse("door://method/"+method)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);}
    public static PendingIntent pending(Context c,int method){return PendingIntent.getActivity(c,100+method,open(c,method),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
    public static int method(Intent i){try{Uri u=i.getData();if(u==null||!"door".equals(u.getScheme())||!"method".equals(u.getHost()))return -1;int n=Integer.parseInt(u.getLastPathSegment());return n>=0&&n<=3?n:-1;}catch(Exception e){return -1;}}
}

package home.doorble;
import android.os.Build;
import android.service.quicksettings.*;

public class DoorTile extends TileService {
    protected int method(){return 2;}
    @Override public void onStartListening(){Tile tile=getQsTile();if(tile!=null){tile.setState(Tile.STATE_INACTIVE);tile.setContentDescription("Open Door controls. Dry run only.");if(Build.VERSION.SDK_INT>=29)tile.setSubtitle("Dry run");tile.updateTile();}}
    // The PendingIntent overload does not exist before Android 14.
    @android.annotation.SuppressLint("StartActivityAndCollapseDeprecated")
    @Override public void onClick(){super.onClick();if(Build.VERSION.SDK_INT>=34)startActivityAndCollapse(DoorIntents.pending(this,method()));else startActivityAndCollapse(DoorIntents.open(this,method()));}
    public static class Tap extends DoorTile {protected int method(){return 0;}}
    public static class Code extends DoorTile {protected int method(){return 1;}}
    public static class Face extends DoorTile {protected int method(){return 3;}}
}

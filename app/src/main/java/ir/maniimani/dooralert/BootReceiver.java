package ir.maniimani.dooralert;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Start watching the doors as soon as the head unit boots. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        DoorService.start(context);
    }
}

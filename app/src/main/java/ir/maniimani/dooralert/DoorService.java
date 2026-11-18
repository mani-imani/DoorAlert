package ir.maniimani.dooralert;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;

/**
 * Always-on watcher: polls the CANBUS door data and opens the popup when something is
 * left open. If the popup is dismissed by hand it stays away until another door opens.
 */
public class DoorService extends Service implements Doors.Listener {

    private static final String CHANNEL = "watch";
    private Doors doors;
    private boolean[] snoozed = new boolean[Doors.COUNT];

    static void start(Context c) {
        if (!c.getSharedPreferences("doors", MODE_PRIVATE).getBoolean("enabled", true)) return;
        try {
            c.startForegroundService(new Intent(c, DoorService.class));
        } catch (Throwable ignored) {
        }
    }

    static void stop(Context c) {
        c.stopService(new Intent(c, DoorService.class));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationChannel ch = new NotificationChannel(CHANNEL, "مراقبت از درها", NotificationManager.IMPORTANCE_MIN);
        ch.setShowBadge(false);
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(ch);
        Notification n = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_car)
                .setContentTitle("DoorAlert")
                .setContentText("مراقب درها و صندوقه")
                .setContentIntent(PendingIntent.getActivity(this, 0,
                        new Intent(this, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT))
                .setShowWhen(false)
                .build();
        startForeground(1, n);
        doors = Doors.get(this);
        doors.startPolling();
        doors.addListener(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        doors.removeListener(this);
        doors.stopPolling();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /** Called when the user closes the popup by hand. */
    static boolean[] sDismissedWith;

    @Override
    public void onDoorsChanged(boolean[] open, boolean calibrated) {
        if (!calibrated) return;
        if (!Doors.any(open)) {
            snoozed = new boolean[Doors.COUNT];
            sDismissedWith = null;
            return;
        }
        if (sDismissedWith != null) {
            snoozed = sDismissedWith;
            sDismissedWith = null;
        }
        boolean somethingNew = false;
        for (int i = 0; i < Doors.COUNT; i++) if (open[i] && !snoozed[i]) somethingNew = true;
        if (!somethingNew || PopupActivity.sVisible) return;
        startActivity(new Intent(this, PopupActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION));
    }
}

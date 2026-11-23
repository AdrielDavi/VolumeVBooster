package com.exemplo.volumeboost;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.audiofx.LoudnessEnhancer;
import android.os.Build;
import android.os.IBinder;
import android.widget.Toast;

public class BoostService extends Service {
    private static final String CHANNEL = "boost";
    private LoudnessEnhancer enhancer;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        int pct = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE).getInt("pct", 200);

        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(
                CHANNEL, "Reforço de volume", NotificationManager.IMPORTANCE_LOW));
        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
                .setContentTitle("Volume Boost ativo")
                .setContentText("Volume: " + pct + "%")
                .setContentIntent(open)
                .setOngoing(true)
                .build();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(1, n);
        }

        try {
            if (enhancer == null) enhancer = new LoudnessEnhancer(0); // sessão 0 = saída global
            // ganho em milibéis: 200% de amplitude = +6 dB = 602 mB
            int mB = (int) Math.round(2000 * Math.log10(pct / 100.0));
            enhancer.setTargetGain(mB);
            enhancer.setEnabled(true);
        } catch (Exception e) {
            Toast.makeText(this, "Não foi possível ativar o reforço: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
            stopSelf();
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (enhancer != null) {
            enhancer.setEnabled(false);
            enhancer.release();
            enhancer = null;
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}

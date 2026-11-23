package com.exemplo.volumeboost;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.media.audiofx.AudioEffect;
import android.media.audiofx.LoudnessEnhancer;
import android.os.Build;
import android.os.IBinder;
import java.util.HashMap;
import java.util.Map;

public class BoostService extends Service {
    private static final String CHANNEL = "boost";
    // sessão 0 = saída global; as outras vêm dos players que avisam o sistema
    private final Map<Integer, LoudnessEnhancer> enhancers = new HashMap<>();
    private String globalStatus = "";
    private boolean receiverOn = false;

    private final BroadcastReceiver sessions = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            int id = i.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, -1);
            if (id <= 0) return;
            if (AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION.equals(i.getAction())) {
                attach(id);
            } else {
                LoudnessEnhancer e = enhancers.remove(id);
                if (e != null) e.release();
            }
            refresh();
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        IntentFilter f = new IntentFilter();
        f.addAction(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION);
        f.addAction(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(sessions, f, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(sessions, f);
        }
        receiverOn = true;
    }

    private int gainMb() {
        int pct = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE).getInt("pct", 200);
        return (int) Math.round(2000 * Math.log10(pct / 100.0)); // 200% = +6 dB
    }

    private boolean attach(int session) {
        try {
            LoudnessEnhancer e = enhancers.get(session);
            if (e == null) {
                e = new LoudnessEnhancer(session);
                enhancers.put(session, e);
            }
            e.setTargetGain(gainMb());
            e.setEnabled(true);
            return e.getEnabled();
        } catch (Throwable t) {
            enhancers.remove(session);
            return false;
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(buildNotification());
        globalStatus = attach(0) ? "reforço global ATIVO" : "reforço global BLOQUEADO pelo sistema";
        for (int id : enhancers.keySet().toArray(new Integer[0])) {
            if (id != 0) attach(id);
        }
        refresh();
        return START_STICKY;
    }

    private void refresh() {
        int apps = enhancers.size() - (enhancers.containsKey(0) ? 1 : 0);
        String s = globalStatus + " | players conectados: " + apps;
        MainActivity a = MainActivity.current;
        if (a != null) a.showStatus(s);
        getSystemService(NotificationManager.class).notify(1, buildNotification());
    }

    private void startForeground(Notification n) {
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(1, n);
        }
    }

    private Notification buildNotification() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(
                CHANNEL, "Reforço de volume", NotificationManager.IMPORTANCE_LOW));
        int pct = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE).getInt("pct", 200);
        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
                .setContentTitle("Volume Boost: " + pct + "%")
                .setContentText(globalStatus)
                .setContentIntent(open)
                .setOngoing(true)
                .build();
    }

    @Override
    public void onDestroy() {
        if (receiverOn) unregisterReceiver(sessions);
        for (LoudnessEnhancer e : enhancers.values()) {
            try { e.setEnabled(false); e.release(); } catch (Throwable ignored) {}
        }
        enhancers.clear();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}

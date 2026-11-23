package com.exemplo.volumeboost;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

public class MainActivity extends Activity {
    static final String PREFS = "boost";
    static final int MIN_PCT = 100, MAX_PCT = 300;
    static MainActivity current;

    private SharedPreferences prefs;
    private TextView label, status;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
        }

        int pad = (int) (24 * getResources().getDisplayMetrics().density);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);

        label = new TextView(this);
        label.setTextSize(28);
        label.setGravity(Gravity.CENTER);

        SeekBar bar = new SeekBar(this);
        bar.setMax(MAX_PCT - MIN_PCT);
        bar.setProgress(prefs.getInt("pct", 200) - MIN_PCT);

        Switch toggle = new Switch(this);
        toggle.setText("Reforço ligado");
        toggle.setTextSize(18);
        toggle.setChecked(prefs.getBoolean("on", false));

        status = new TextView(this);
        status.setTextSize(15);
        status.setText("Status: desligado");

        TextView aviso = new TextView(this);
        aviso.setText("\nAtenção: volumes muito altos distorcem o som e podem "
                + "danificar o alto-falante e a sua audição. Comece baixo.");

        root.addView(label);
        root.addView(bar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(toggle);
        root.addView(status);
        root.addView(aviso);
        setContentView(root);
        updateLabel();

        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                prefs.edit().putInt("pct", p + MIN_PCT).apply();
                updateLabel();
                if (prefs.getBoolean("on", false)) apply();
            }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });
        toggle.setOnCheckedChangeListener((v, on) -> {
            prefs.edit().putBoolean("on", on).apply();
            apply();
        });
        if (toggle.isChecked()) apply();
    }

    @Override protected void onResume() { super.onResume(); current = this; }
    @Override protected void onPause() { current = null; super.onPause(); }

    void showStatus(String s) { runOnUiThread(() -> status.setText("Status: " + s)); }

    private void updateLabel() {
        label.setText("Volume: " + prefs.getInt("pct", 200) + "%");
    }

    private void apply() {
        Intent i = new Intent(this, BoostService.class);
        if (prefs.getBoolean("on", false)) {
            startForegroundService(i);
        } else {
            stopService(i);
            showStatus("desligado");
        }
    }
}

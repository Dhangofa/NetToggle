package com.dhangofa.networktoggle;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import rikka.shizuku.Shizuku;

public class MainActivity extends Activity {

    private static final String PREFS_NAME = "NetTogglePrefs";
    private static final String EXEC_MODE_KEY = "exec_mode";

    private RadioGroup radioGroup;
    private RadioButton radioRoot;
    private RadioButton radioShizuku;
    private TextView statusText;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        
        radioGroup = findViewById(R.id.modeRadioGroup);
        radioRoot = findViewById(R.id.radioRoot);
        radioShizuku = findViewById(R.id.radioShizuku);
        statusText = findViewById(R.id.shizukuStatusText);

        // Load saved mode (Default: Root = 1, Shizuku = 2)
        int savedMode = prefs.getInt(EXEC_MODE_KEY, 1);
        if (savedMode == 2) {
            radioShizuku.setChecked(true);
            checkShizukuPermission();
        } else {
            radioRoot.setChecked(true);
        }

        radioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.radioRoot) {
                prefs.edit().putInt(EXEC_MODE_KEY, 1).apply();
                statusText.setText("");
            } else if (checkedId == R.id.radioShizuku) {
                prefs.edit().putInt(EXEC_MODE_KEY, 2).apply();
                checkShizukuPermission();
            }
        });
    }

    private void checkShizukuPermission() {
        if (!Shizuku.pingBinder()) {
            statusText.setText("Shizuku is not running on this device.");
            return;
        }

        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            statusText.setText("Requesting Shizuku permission...");
            Shizuku.requestPermission(0);
        } else {
            statusText.setText("Shizuku permission granted!");
            statusText.setTextColor(0xFF1B873F); // Green
        }
    }
}

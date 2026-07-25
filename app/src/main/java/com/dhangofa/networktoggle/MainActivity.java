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

    // 1. Wait for Shizuku Binder to be injected, then check permissions
    private final Shizuku.OnBinderReceivedListener binderReceivedListener = () -> {
        runOnUiThread(this::checkShizukuPermission);
    };

    // 2. Handle if Shizuku suddenly dies in the background
    private final Shizuku.OnBinderDeadListener binderDeadListener = () -> {
        runOnUiThread(() -> {
            statusText.setText("Shizuku is not running.");
            statusText.setTextColor(0xFFFF5555); // Red
        });
    };

    // 3. React instantly when the user taps "Allow" on the Shizuku Popup
    private final Shizuku.OnRequestPermissionResultListener permissionResultListener = (requestCode, grantResult) -> {
        runOnUiThread(this::checkShizukuPermission);
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        
        radioGroup = findViewById(R.id.modeRadioGroup);
        radioRoot = findViewById(R.id.radioRoot);
        radioShizuku = findViewById(R.id.radioShizuku);
        statusText = findViewById(R.id.shizukuStatusText);

        // Register the lifecycle listeners
        Shizuku.addBinderReceivedListener(binderReceivedListener);
        Shizuku.addBinderDeadListener(binderDeadListener);
        Shizuku.addRequestPermissionResultListener(permissionResultListener);

        // Load saved mode (Default: Root = 1, Shizuku = 2)
        int savedMode = prefs.getInt(EXEC_MODE_KEY, 1);
        if (savedMode == 2) {
            radioShizuku.setChecked(true);
            checkShizukuPermission(); // Try immediately
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Prevent memory leaks by destroying the listeners when the app closes
        Shizuku.removeBinderReceivedListener(binderReceivedListener);
        Shizuku.removeBinderDeadListener(binderDeadListener);
        Shizuku.removeRequestPermissionResultListener(permissionResultListener);
    }

    private void checkShizukuPermission() {
        if (!Shizuku.pingBinder()) {
            statusText.setText("Waiting for Shizuku...");
            statusText.setTextColor(0xFFFF5555); // Red
            return;
        }

        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            statusText.setText("Requesting Shizuku permission...");
            statusText.setTextColor(0xFFFFB300); // Yellow
            Shizuku.requestPermission(0); // This line triggers the Auto-Popup
        } else {
            statusText.setText("Shizuku mode active & authorized!");
            statusText.setTextColor(0xFF1B873F); // Green
        }
    }
}

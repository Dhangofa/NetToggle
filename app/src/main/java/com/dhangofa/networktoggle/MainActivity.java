/*
Behavior:
- Fresh install: no option selected.
- Root selected: root permission checked.
- Shizuku selected: Shizuku permission checked/requested only then.
- Saved Root: root rechecked on open.
- Saved Shizuku: status checked without auto-popup.
- Shizuku callbacks update UI only when Shizuku mode is selected.
*/

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
	
    private static final int MODE_NONE = 0;
    private static final int MODE_ROOT = 1;
    private static final int MODE_SHIZUKU = 2;

    private RadioGroup radioGroup;
    private RadioButton radioRoot;
    private RadioButton radioShizuku;
    private TextView statusText;
    private SharedPreferences prefs;

    // 1. First check Shizuku mode selected then wait for Shizuku Binder to be injected, then check permissions
    private final Shizuku.OnBinderReceivedListener binderReceivedListener = () -> {
		runOnUiThread(() -> {
			if (prefs != null && prefs.getInt(EXEC_MODE_KEY, MODE_NONE) == MODE_SHIZUKU) {
				checkShizukuPermission(false);
			}
		});
	};

    // 2. Handle if Shizuku suddenly dies in the background
    private final Shizuku.OnBinderDeadListener binderDeadListener = () -> {
		runOnUiThread(() -> {
			if (prefs != null && prefs.getInt(EXEC_MODE_KEY, MODE_NONE) == MODE_SHIZUKU) {
				statusText.setText("Shizuku is not running.");
				statusText.setTextColor(0xFFFF5555);
			}
		});
	};


    // 3. React instantly when the user taps "Allow" on the Shizuku Popup
    private final Shizuku.OnRequestPermissionResultListener permissionResultListener = (requestCode, grantResult) -> {
        runOnUiThread(() -> {
            if (prefs != null && prefs.getInt(EXEC_MODE_KEY, MODE_NONE) == MODE_SHIZUKU) {
                checkShizukuPermission(false);
            }
        });
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

        // Load saved mode (Default =0, Root = 1, Shizuku = 2)
        int savedMode = prefs.getInt(EXEC_MODE_KEY, MODE_NONE);
        if (savedMode == MODE_ROOT) {
            radioRoot.setChecked(true);
            checkRootPermission();
        } else if (savedMode == MODE_SHIZUKU) {
            radioShizuku.setChecked(true);
            checkShizukuPermission(false);
        } else {
            radioGroup.clearCheck();
            statusText.setText("Select Root or Shizuku mode.");
            statusText.setTextColor(0xFFFFB300);
        }

        radioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.radioRoot) {
                prefs.edit().putInt(EXEC_MODE_KEY, MODE_ROOT).apply();
                checkRootPermission();
            } else if (checkedId == R.id.radioShizuku) {
                prefs.edit().putInt(EXEC_MODE_KEY, MODE_SHIZUKU).apply();
                checkShizukuPermission(true);
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

    private void checkRootPermission() {
        statusText.setText("Checking root permission...");
        statusText.setTextColor(0xFFFFB300);
    
        new Thread(() -> {
            boolean granted = false;
    
            try {
                Process process = Runtime.getRuntime().exec(new String[]{"su", "-c", "id"});
                int exitCode = process.waitFor();
                granted = exitCode == 0;
            } catch (Exception ignored) {
                granted = false;
            }
    
            boolean finalGranted = granted;
			runOnUiThread(() -> {
				if (prefs == null || prefs.getInt(EXEC_MODE_KEY, MODE_NONE) != MODE_ROOT) {
					return;
				}

				if (finalGranted) {
					statusText.setText("Root mode active & authorized!");
					statusText.setTextColor(0xFF1B873F);
				} else {
					statusText.setText("Root permission denied or unavailable.");
					statusText.setTextColor(0xFFFF5555);
				}
			});
        }).start();
    }
    
    private void checkShizukuPermission(boolean requestIfNeeded) {
		try {
			if (!Shizuku.pingBinder()) {
				statusText.setText("Shizuku is not running.");
				statusText.setTextColor(0xFFFF5555);
				return;
			}

			if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
				statusText.setText("Shizuku mode active & authorized!");
				statusText.setTextColor(0xFF1B873F);
				return;
			}

			statusText.setText("Shizuku permission not granted.");
			statusText.setTextColor(0xFFFFB300);

			if (requestIfNeeded) {
				statusText.setText("Requesting Shizuku permission...");
				statusText.setTextColor(0xFFFFB300);
				Shizuku.requestPermission(0);
			}
		} catch (Exception e) {
			statusText.setText("Shizuku check failed.");
			statusText.setTextColor(0xFFFF5555);
		}
	}
}

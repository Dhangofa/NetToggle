/*
Behavior:
- Fresh install: no execution mode selected.
- Root selected: root permission checked.
- Shizuku selected: Shizuku permission checked/requested only then.
- Saved Root: root rechecked on open.
- Saved Shizuku: status checked without auto-popup.
- Shizuku callbacks update UI only when Shizuku mode is selected.

Target SIM behavior:
- Target SIM defaults to Auto.
- Auto mode is handled by the QS tile service using the active data subscription.
- Auto mode resolves the active data subscription to the physical SIM slot before applying changes.
- If Auto SIM detection fails, the QS tile sets auto_sim_error=true and shows a Toast.
- MainActivity shows a persistent warning when auto_sim_error=true.
- Selecting any Target SIM option clears the previous Auto SIM warning.
- SIM 1 and SIM 2 use the chosen physical SIM slot directly.
*/

package com.dhangofa.networktoggle;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.view.View;

import rikka.shizuku.Shizuku;

public class MainActivity extends Activity {

    private static final String PREFS_NAME = "NetTogglePrefs";
    private static final String EXEC_MODE_KEY = "exec_mode";
	private static final String TARGET_SIM_KEY = "target_sim";
	private static final String AUTO_SIM_ERROR_KEY = "auto_sim_error";
	private static final String STATE_KEY = "net_state";
	
    private static final int MODE_NONE = 0;
    private static final int MODE_ROOT = 1;
    private static final int MODE_SHIZUKU = 2;
	private static final int STATE_UNKNOWN = 0;
	
	private static final int TARGET_SIM_AUTO = 0;
	private static final int TARGET_SIM_1 = 1;
	private static final int TARGET_SIM_2 = 2;

    private RadioGroup radioGroup;
    private RadioButton radioRoot;
    private RadioButton radioShizuku;
    private TextView statusText;
	private TextView appVersionText;
	private ImageView githubLink;
	private ImageView telegramLink;
    private SharedPreferences prefs;
	
	private RadioGroup targetSimRadioGroup;
	private RadioButton radioSimAuto;
	private RadioButton radioSim1;
	private RadioButton radioSim2;
	private TextView autoSimWarningText;
	
	private volatile boolean activityDestroyed = false;
	private Thread rootCheckThread;
	private Process rootCheckProcess;
	
    // Wait for Shizuku Binder, then check permission only if Shizuku mode is selected.
    private final Shizuku.OnBinderReceivedListener binderReceivedListener = () -> {
	    runOnUiThread(() -> {
	        if (!activityDestroyed
	                && prefs != null
	                && prefs.getInt(EXEC_MODE_KEY, MODE_NONE) == MODE_SHIZUKU) {
	            checkShizukuPermission(false);
	        }
	    });
	};

    // Handle Shizuku service death only when Shizuku mode is selected.
    private final Shizuku.OnBinderDeadListener binderDeadListener = () -> {
	    runOnUiThread(() -> {
	        if (!activityDestroyed
	                && prefs != null
	                && prefs.getInt(EXEC_MODE_KEY, MODE_NONE) == MODE_SHIZUKU) {
	            statusText.setText("Shizuku is not running.");
	            statusText.setTextColor(0xFFFF5555);
	        }
	    });
	};


    // React when the user grants or denies Shizuku permission.
    private final Shizuku.OnRequestPermissionResultListener permissionResultListener = (requestCode, grantResult) -> {
	    runOnUiThread(() -> {
	        if (!activityDestroyed
	                && prefs != null
	                && prefs.getInt(EXEC_MODE_KEY, MODE_NONE) == MODE_SHIZUKU) {
	            checkShizukuPermission(false);
	        }
	    });
	};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        activityDestroyed = false;
		// Inject custom version pill into the default Action Bar on the right side
        if (getActionBar() != null) {
            getActionBar().setDisplayOptions(
                    android.app.ActionBar.DISPLAY_SHOW_TITLE | android.app.ActionBar.DISPLAY_SHOW_CUSTOM);
            
            android.widget.TextView versionText = new android.widget.TextView(this);
            versionText.setText("v" + getAppVersionName());
            versionText.setTextSize(12);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                versionText.setTextColor(getColor(R.color.brand_on_primary_container));
            }
            versionText.setBackgroundResource(R.drawable.shape_pill_badge_bg);
            int padX = (int) (10 * getResources().getDisplayMetrics().density);
            int padY = (int) (4 * getResources().getDisplayMetrics().density);
            versionText.setPadding(padX, padY, padX, padY);
            
            android.app.ActionBar.LayoutParams layoutParams = new android.app.ActionBar.LayoutParams(
                    android.app.ActionBar.LayoutParams.WRAP_CONTENT,
                    android.app.ActionBar.LayoutParams.WRAP_CONTENT,
                    android.view.Gravity.END | android.view.Gravity.CENTER_VERTICAL);
            layoutParams.setMarginEnd((int) (16 * getResources().getDisplayMetrics().density));
            
            getActionBar().setCustomView(versionText, layoutParams);
            getActionBar().setElevation(0);
        }

		// ADDED THIS BLOCK FOR STATUS BAR LIGHT/DARK ICON CONTRAST
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            boolean isNight = (getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;
            getWindow().setStatusBarColor(getColor(R.color.surface_background));
            View decor = getWindow().getDecorView();
            if (!isNight) {
                decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
            } else {
                decor.setSystemUiVisibility(0);
            }
        }
        // -------------------------------------------------------------
		setContentView(R.layout.activity_main);

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        
        radioGroup = findViewById(R.id.modeRadioGroup);
        radioRoot = findViewById(R.id.radioRoot);
        radioShizuku = findViewById(R.id.radioShizuku);
        statusText = findViewById(R.id.shizukuStatusText);
		
		targetSimRadioGroup = findViewById(R.id.targetSimRadioGroup);
		radioSimAuto = findViewById(R.id.radioSimAuto);
		radioSim1 = findViewById(R.id.radioSim1);
		radioSim2 = findViewById(R.id.radioSim2);
		autoSimWarningText = findViewById(R.id.autoSimWarningText);
		
		appVersionText = findViewById(R.id.appVersionText);
		githubLink = findViewById(R.id.githubLink);
		telegramLink = findViewById(R.id.telegramLink);

		appVersionText.setText("v" + getAppVersionName());

		githubLink.setOnClickListener(v -> openUrl("https://github.com/Dhangofa/NetToggle"));
		telegramLink.setOnClickListener(v -> openUrl("https://t.me/dhangofa"));

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
		
		loadSavedTargetSimMode();
		updateAutoSimWarning();

		radioGroup.setOnCheckedChangeListener((group, checkedId) -> {
			if (checkedId == R.id.radioRoot) {
				prefs.edit()
						.putInt(EXEC_MODE_KEY, MODE_ROOT)
						.putInt(STATE_KEY, STATE_UNKNOWN)
						.putBoolean(AUTO_SIM_ERROR_KEY, false)
						.apply();

				checkRootPermission();
			} else if (checkedId == R.id.radioShizuku) {
				prefs.edit()
						.putInt(EXEC_MODE_KEY, MODE_SHIZUKU)
						.putInt(STATE_KEY, STATE_UNKNOWN)
						.putBoolean(AUTO_SIM_ERROR_KEY, false)
						.apply();

				checkShizukuPermission(true);
			}
		});
		
		targetSimRadioGroup.setOnCheckedChangeListener((group, checkedId) -> {
			SharedPreferences.Editor editor = prefs.edit();

			if (checkedId == R.id.radioSimAuto) {
				editor.putInt(TARGET_SIM_KEY, TARGET_SIM_AUTO);
			} else if (checkedId == R.id.radioSim1) {
				editor.putInt(TARGET_SIM_KEY, TARGET_SIM_1);
			} else if (checkedId == R.id.radioSim2) {
				editor.putInt(TARGET_SIM_KEY, TARGET_SIM_2);
			}

			editor.putInt(STATE_KEY, STATE_UNKNOWN);
			editor.putBoolean(AUTO_SIM_ERROR_KEY, false);
			editor.apply();

			updateAutoSimWarning();
		});
    }

	@Override
	protected void onResume() {
		super.onResume();

		if (prefs != null) {
			updateAutoSimWarning();
		}
	}

   @Override
	protected void onDestroy() {
	    activityDestroyed = true;
	
	    // Prevent memory leaks by destroying Shizuku listeners when the app closes
	    Shizuku.removeBinderReceivedListener(binderReceivedListener);
	    Shizuku.removeBinderDeadListener(binderDeadListener);
	    Shizuku.removeRequestPermissionResultListener(permissionResultListener);
	
	    // Stop any running root permission check process
	    if (rootCheckProcess != null) {
	        rootCheckProcess.destroy();
	        rootCheckProcess = null;
	    }
	
	    // Interrupt root check thread if it is still active
	    if (rootCheckThread != null && rootCheckThread.isAlive()) {
	        rootCheckThread.interrupt();
	        rootCheckThread = null;
	    }
	
	    super.onDestroy();
	}

    private void checkRootPermission() {
	    statusText.setText("Checking root permission...");
	    statusText.setTextColor(0xFFFFB300);
	
	    rootCheckThread = new Thread(() -> {
	        boolean granted = false;
	        Process process = null;
	
	        try {
	            process = Runtime.getRuntime().exec(new String[]{"su", "-c", "id"});
	            rootCheckProcess = process;
	
	            int exitCode = process.waitFor();
	            granted = exitCode == 0;
	        } catch (Exception ignored) {
	            granted = false;
	        } finally {
	            if (process != null) {
	                process.destroy();
	            }
	
	            if (rootCheckProcess == process) {
	                rootCheckProcess = null;
	            }
	        }
	
	        boolean finalGranted = granted;
	
	        runOnUiThread(() -> {
	            if (activityDestroyed) {
	                return;
	            }
	
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
	    });
	
	    rootCheckThread.start();
	}
    
    private void checkShizukuPermission(boolean requestIfNeeded) {
		if (activityDestroyed) {
		    return;
		}
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
	
	private String getAppVersionName() {
		try {
			PackageInfo packageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
			return packageInfo.versionName;
		} catch (Exception e) {
			return "unknown";
		}
	}

	private void openUrl(String url) {
		try {
			Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
			startActivity(intent);
		} catch (Exception ignored) {
		}
	}
	
	private void loadSavedTargetSimMode() {
		int targetSim = prefs.getInt(TARGET_SIM_KEY, TARGET_SIM_AUTO);

		if (targetSim == TARGET_SIM_1) {
			radioSim1.setChecked(true);
		} else if (targetSim == TARGET_SIM_2) {
			radioSim2.setChecked(true);
		} else {
			radioSimAuto.setChecked(true);
		}
	}
	
	private void updateAutoSimWarning() {
		if (autoSimWarningText == null || prefs == null) {
			return;
		}

		boolean hasAutoSimError = prefs.getBoolean(AUTO_SIM_ERROR_KEY, false);
		int targetSim = prefs.getInt(TARGET_SIM_KEY, TARGET_SIM_AUTO);

		if (hasAutoSimError && targetSim == TARGET_SIM_AUTO) {
			autoSimWarningText.setVisibility(View.VISIBLE);
			autoSimWarningText.setText("Auto SIM detection failed. Please choose SIM 1 or SIM 2 manually.");
			autoSimWarningText.setTextColor(0xFFFF5555);
		} else {
			autoSimWarningText.setVisibility(View.GONE);
		}
	}
	
}

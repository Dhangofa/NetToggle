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
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import com.dhangofa.networktoggle.config.AppPreferences;
import com.dhangofa.networktoggle.model.ExecutionMode;
import com.dhangofa.networktoggle.model.TargetSim;
import com.dhangofa.networktoggle.cycle.TileCycleManager;
import com.dhangofa.networktoggle.ui.TileCycleUiController;


import rikka.shizuku.Shizuku;

public class MainActivity extends Activity {	
	
    private RadioGroup radioGroup;
    private RadioButton radioRoot;
    private RadioButton radioShizuku;
	private TextView appVersionText;
    private TextView statusText;
    private ImageView githubLink;
    private ImageView telegramLink;

    private RadioGroup targetSimRadioGroup;
    private RadioButton radioSimAuto;
    private RadioButton radioSim1;
    private RadioButton radioSim2;
    private TextView autoSimWarningText;
	
	private View separatorRootShizuku;
    private View separatorAutoSim1;
    private View separatorSim1Sim2;

    private AppPreferences appPreferences;
	private TileCycleUiController tileCycleUiController;	
    private volatile boolean activityDestroyed;
    private Thread rootCheckThread;
    private Process rootCheckProcess;

    private final Shizuku.OnBinderReceivedListener binderReceivedListener = () ->
            runOnUiThread(() -> {
                if (!activityDestroyed
                        && appPreferences != null
                        && appPreferences.getExecutionMode() == ExecutionMode.SHIZUKU) {
                    checkShizukuPermission(false);
                }
            });

    private final Shizuku.OnBinderDeadListener binderDeadListener = () ->
            runOnUiThread(() -> {
                if (!activityDestroyed
                        && appPreferences != null
                        && appPreferences.getExecutionMode() == ExecutionMode.SHIZUKU) {
                    setStatus("Shizuku is not running.", 0xFFFF5555);
                }
            });

    private final Shizuku.OnRequestPermissionResultListener permissionResultListener =
            (requestCode, grantResult) -> runOnUiThread(() -> {
                if (!activityDestroyed
                        && appPreferences != null
                        && appPreferences.getExecutionMode() == ExecutionMode.SHIZUKU) {
                    checkShizukuPermission(false);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        activityDestroyed = false;

        configureStatusBar();
        setContentView(R.layout.activity_main);

        appPreferences = new AppPreferences(this);
        bindViews();
		appVersionText.setText("v" + getAppVersionName());
        bindLinks();
		TileCycleManager tileCycleManager = new TileCycleManager(appPreferences);
		tileCycleUiController = new TileCycleUiController(this, tileCycleManager);
		tileCycleUiController.initialize();
        registerShizukuListeners();
        loadSavedExecutionMode();
        loadSavedTargetSimMode();
        updateAutoSimWarning();
        bindSelectionListeners();
		updateSeparatorVisibility();
    }

    private void configureStatusBar() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return;

        boolean isNight = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        getWindow().setStatusBarColor(getColor(R.color.card_surface));
        getWindow().getDecorView().setSystemUiVisibility(
                isNight ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
    }

    private void bindViews() {
        radioGroup = findViewById(R.id.modeRadioGroup);
        radioRoot = findViewById(R.id.radioRoot);
        radioShizuku = findViewById(R.id.radioShizuku);
        statusText = findViewById(R.id.shizukuStatusText);
		appVersionText = findViewById(R.id.appVersionText);
        targetSimRadioGroup = findViewById(R.id.targetSimRadioGroup);
        radioSimAuto = findViewById(R.id.radioSimAuto);
        radioSim1 = findViewById(R.id.radioSim1);
        radioSim2 = findViewById(R.id.radioSim2);
        autoSimWarningText = findViewById(R.id.autoSimWarningText);
        githubLink = findViewById(R.id.githubLink);
        telegramLink = findViewById(R.id.telegramLink);
		separatorRootShizuku = findViewById(R.id.separatorRootShizuku);
        separatorAutoSim1 = findViewById(R.id.separatorAutoSim1);
        separatorSim1Sim2 = findViewById(R.id.separatorSim1Sim2);
    }

    private void bindLinks() {
        githubLink.setOnClickListener(v -> openUrl("https://github.com/Dhangofa/NetToggle"));
        telegramLink.setOnClickListener(v -> openUrl("https://t.me/dhangofas_projects_chat"));
    }
	
	private void updateSeparatorVisibility() {
        // Execution Mode Separator
        int modeId = radioGroup.getCheckedRadioButtonId();
        if (modeId == -1) {
            separatorRootShizuku.setVisibility(View.VISIBLE);
        } else {
            separatorRootShizuku.setVisibility(View.INVISIBLE);
        }

        // Target SIM Separators
        int simId = targetSimRadioGroup.getCheckedRadioButtonId();
        if (simId == -1) {
            separatorAutoSim1.setVisibility(View.VISIBLE);
            separatorSim1Sim2.setVisibility(View.VISIBLE);
        } else if (simId == R.id.radioSimAuto) {
            separatorAutoSim1.setVisibility(View.INVISIBLE);
            separatorSim1Sim2.setVisibility(View.VISIBLE);
        } else if (simId == R.id.radioSim1) {
            separatorAutoSim1.setVisibility(View.INVISIBLE);
            separatorSim1Sim2.setVisibility(View.INVISIBLE);
        } else if (simId == R.id.radioSim2) {
            separatorAutoSim1.setVisibility(View.VISIBLE);
            separatorSim1Sim2.setVisibility(View.INVISIBLE);
        }
    }

    private void registerShizukuListeners() {
        Shizuku.addBinderReceivedListener(binderReceivedListener);
        Shizuku.addBinderDeadListener(binderDeadListener);
        Shizuku.addRequestPermissionResultListener(permissionResultListener);
    }

    private void loadSavedExecutionMode() {
        ExecutionMode savedMode = appPreferences.getExecutionMode();
        if (savedMode == ExecutionMode.ROOT) {
            radioRoot.setChecked(true);
            checkRootPermission();
        } else if (savedMode == ExecutionMode.SHIZUKU) {
            radioShizuku.setChecked(true);
            checkShizukuPermission(false);
        } else {
            radioGroup.clearCheck();
            setStatus("Select Root or Shizuku mode.", 0xFFFFB300);
        }
    }

    private void bindSelectionListeners() {
        radioGroup.setOnCheckedChangeListener((group, checkedId) -> {
			updateSeparatorVisibility();
            if (checkedId == R.id.radioRoot) {
                appPreferences.onExecutionModeChanged(ExecutionMode.ROOT);
                checkRootPermission();
            } else if (checkedId == R.id.radioShizuku) {
                appPreferences.onExecutionModeChanged(ExecutionMode.SHIZUKU);
                checkShizukuPermission(true);
            }
        });

        targetSimRadioGroup.setOnCheckedChangeListener((group, checkedId) -> {
			updateSeparatorVisibility();
            TargetSim target = TargetSim.AUTO;
            if (checkedId == R.id.radioSim1) target = TargetSim.SIM_1;
            else if (checkedId == R.id.radioSim2) target = TargetSim.SIM_2;
            appPreferences.onTargetSimChanged(target);
            updateAutoSimWarning();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (appPreferences != null) updateAutoSimWarning();
    }

    @Override
    protected void onDestroy() {
        activityDestroyed = true;
        Shizuku.removeBinderReceivedListener(binderReceivedListener);
        Shizuku.removeBinderDeadListener(binderDeadListener);
        Shizuku.removeRequestPermissionResultListener(permissionResultListener);

        if (rootCheckProcess != null) {
            rootCheckProcess.destroy();
            rootCheckProcess = null;
        }
        if (rootCheckThread != null && rootCheckThread.isAlive()) {
            rootCheckThread.interrupt();
            rootCheckThread = null;
        }
        super.onDestroy();
    }

    private void checkRootPermission() {
        setStatus("Checking root permission...", 0xFFFFB300);
        rootCheckThread = new Thread(() -> {
            boolean granted = false;
            Process process = null;
            try {
                process = Runtime.getRuntime().exec(new String[]{"su", "-c", "id"});
                rootCheckProcess = process;
                granted = process.waitFor() == 0;
            } catch (Exception ignored) {
                granted = false;
            } finally {
                if (process != null) process.destroy();
                if (rootCheckProcess == process) rootCheckProcess = null;
            }

            boolean finalGranted = granted;
            runOnUiThread(() -> {
                if (activityDestroyed || appPreferences == null
                        || appPreferences.getExecutionMode() != ExecutionMode.ROOT) return;
                if (finalGranted) setStatus("Root mode active & authorized!", 0xFF1B873F);
                else setStatus("Root permission denied or unavailable.", 0xFFFF5555);
            });
        });
        rootCheckThread.start();
    }

    private void checkShizukuPermission(boolean requestIfNeeded) {
        if (activityDestroyed) return;
        try {
            if (!Shizuku.pingBinder()) {
                setStatus("Shizuku is not running.", 0xFFFF5555);
                return;
            }
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                setStatus("Shizuku mode active & authorized!", 0xFF1B873F);
                return;
            }
            setStatus("Shizuku permission not granted.", 0xFFFFB300);
            if (requestIfNeeded) {
                setStatus("Requesting Shizuku permission...", 0xFFFFB300);
                Shizuku.requestPermission(0);
            }
        } catch (Exception e) {
            setStatus("Shizuku check failed.", 0xFFFF5555);
        }
    }

	private void setStatus(String text, int color) {
	    statusText.setText(text);
	    statusText.setTextColor(color);
	
	    if (color == 0xFF1B873F) {
	        statusText.setBackgroundResource(
	                R.drawable.shape_status_badge_success
	        );
	    } else if (color == 0xFFFF5555) {
	        statusText.setBackgroundResource(
	                R.drawable.shape_status_badge_error
	        );
	    } else {
	        statusText.setBackgroundResource(
	                R.drawable.shape_pill_badge_bg
	        );
	    }
	}

    private String getAppVersionName() {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            return info.versionName;
        } catch (Exception e) {
            return "unknown";
        }
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception ignored) {
        }
    }

    private void loadSavedTargetSimMode() {
        TargetSim target = appPreferences.getTargetSim();
        if (target == TargetSim.SIM_1) radioSim1.setChecked(true);
        else if (target == TargetSim.SIM_2) radioSim2.setChecked(true);
        else radioSimAuto.setChecked(true);
    }

    private void updateAutoSimWarning() {
        if (autoSimWarningText == null || appPreferences == null) return;
        boolean show = appPreferences.hasAutoSimError()
                && appPreferences.getTargetSim() == TargetSim.AUTO;
        autoSimWarningText.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) {
            autoSimWarningText.setText(
                    "Auto SIM detection failed. Please choose SIM 1 or SIM 2 manually.");
            autoSimWarningText.setTextColor(0xFFFF5555);
        }
    }
}


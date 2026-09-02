/**
 * The main configuration screen for the app.
 * Orchestrates all the setup pieces: choosing the backend (Root or Shizuku),
 * picking the target SIM, managing the onboarding carousel UI, and keeping permissions in check.
 * 
 * Notes on behavior for future reference:
 * - On a fresh install, no execution mode is selected yet.
 * - When Root is selected, it immediately checks the su binary.
 * - When Shizuku is selected, it checks/requests the Shizuku permission.
 * - Shizuku callbacks only update the UI when Shizuku mode is actually selected.
 * - Target SIM defaults to Auto, which resolves the active data subscription to the physical slot.
 * - If Auto SIM detection fails, the QS tile flags it and this screen shows a persistent warning.
 * - Selecting a specific SIM slot clears that Auto SIM warning.
 */
package com.dhangofa.networktoggle;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.content.res.ColorStateList;
import android.widget.ImageView;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import com.dhangofa.networktoggle.config.AppPreferences;
import com.dhangofa.networktoggle.model.ExecutionMode;
import com.dhangofa.networktoggle.model.NetworkMode;
import com.dhangofa.networktoggle.model.TargetSim;
import com.dhangofa.networktoggle.cycle.TileCycleManager;
import com.dhangofa.networktoggle.telephony.NetworkModeController;
import com.dhangofa.networktoggle.telephony.NetworkCapabilityResolver;
import com.dhangofa.networktoggle.telephony.SimResolver;
import com.dhangofa.networktoggle.ui.TileCycleUiController;
import com.dhangofa.networktoggle.ui.DialogHelper;
import com.dhangofa.networktoggle.ui.TargetSimUiController;
import com.dhangofa.networktoggle.ui.TileCycleSyncController;
import com.dhangofa.networktoggle.util.AppExecutors;
import android.app.Dialog;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.graphics.drawable.ColorDrawable;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.widget.Toast;
import com.dhangofa.networktoggle.model.DiagnosticError;
import com.dhangofa.networktoggle.util.DiagnosticReporter;


import rikka.shizuku.Shizuku;

public class MainActivity extends Activity implements android.content.SharedPreferences.OnSharedPreferenceChangeListener {
    private int currentThemeMode = -1;
    private ImageView btnThemeToggle;

    @Override
    protected void attachBaseContext(Context newBase) {
        SharedPreferences prefs = newBase.getSharedPreferences("AppPrefs", MODE_PRIVATE);
        int mode = prefs.getInt("app_theme", 0);
        
        Configuration config = new Configuration(newBase.getResources().getConfiguration());
        if (mode == 1) { // Light
            config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | Configuration.UI_MODE_NIGHT_NO;
        } else if (mode == 2 || mode == 3) { // Dark or AMOLED
            config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | Configuration.UI_MODE_NIGHT_YES;
        }
        super.attachBaseContext(newBase.createConfigurationContext(config));
    }

    private void cycleThemeMode() {
        currentThemeMode = (currentThemeMode + 1) % 4;
        getSharedPreferences("AppPrefs", MODE_PRIVATE).edit().putInt("app_theme", currentThemeMode).apply();
        recreate();
    }
    
    private void updateThemeIcon() {
        if (btnThemeToggle == null) return;
        if (currentThemeMode == 0) {
            btnThemeToggle.setImageResource(R.drawable.ic_theme_auto);
        } else if (currentThemeMode == 1) {
            btnThemeToggle.setImageResource(R.drawable.ic_theme_light);
        } else if (currentThemeMode == 2) {
            btnThemeToggle.setImageResource(R.drawable.ic_theme_dark);
        } else {
            btnThemeToggle.setImageResource(R.drawable.ic_theme_amoled);
        }
    }
    
    private void applyAmoledIfNeeded() {
        if (currentThemeMode == 3) {
            int black = Color.BLACK;
            View root = findViewById(R.id.mainRoot);
            if (root != null) root.setBackgroundColor(black);
            getWindow().setNavigationBarColor(black);
            
            // AMOLED mode only darkens the root window background to pure black to save battery.
            // Cards and surfaces will retain their default dark mode elevation and borders,
            // otherwise they become completely invisible against the black background.
        }
    }
	
	
    private RadioGroup radioGroup;
    private RadioButton radioRoot;
    private RadioButton radioShizuku;
	private TextView appVersionText;
    private TextView statusText;
    private ImageView githubLink;
    private ImageView telegramLink;
    private ImageView faqLink;
    
    // Morphing View Carousel
    private com.dhangofa.networktoggle.ui.CarouselManager carouselManager;

	private View separatorRootShizuku;

    private com.dhangofa.networktoggle.ui.PhoneStatePermissionManager permissionManager;
    private View errorBannerContainer;
    private Dialog diagnosticDialog;
    private NetworkCapabilityResolver capabilityResolver;
    
    private static final int REQ_CODE_PHONE_STATE = 1001;

    private AppPreferences appPreferences;
	private TileCycleUiController tileCycleUiController;	
    private volatile boolean activityDestroyed;
    private com.dhangofa.networktoggle.ui.ExecutionStateController executionStateController;
    private TargetSimUiController targetSimUiController;
    private SimResolver simResolver;
    private NetworkModeController modeController;
    private com.dhangofa.networktoggle.telephony.NetworkModeReader modeReader;
    private TargetSim lastTargetSim = TargetSim.AUTO;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        android.content.SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        currentThemeMode = prefs.getInt("app_theme", 0);
        
        super.onCreate(savedInstanceState);
        activityDestroyed = false;
        // Triggering fresh deploy to streaming emulator to clear cache/state
        configureStatusBar();
        setContentView(R.layout.activity_main);
        
        btnThemeToggle = (ImageView) findViewById(R.id.btnThemeToggle);
        updateThemeIcon();
        if (btnThemeToggle != null) {
            btnThemeToggle.setOnClickListener(v -> cycleThemeMode());
        }
        applyAmoledIfNeeded();

        appPreferences = new AppPreferences(this);
        lastTargetSim = appPreferences.getTargetSim();
        appPreferences.registerListener(this);
        bindViews();
		appVersionText.setText(getString(R.string.app_version_format, getAppVersionName()));
        bindLinks();
        simResolver = new SimResolver(this, appPreferences);
        capabilityResolver = new NetworkCapabilityResolver(appPreferences, simResolver);
		TileCycleManager tileCycleManager = new TileCycleManager(appPreferences);
		tileCycleUiController = new TileCycleUiController(this, tileCycleManager);
        modeController = new NetworkModeController(simResolver);
        modeReader = new com.dhangofa.networktoggle.telephony.NetworkModeReader(this, appPreferences, simResolver);
        
        permissionManager = new com.dhangofa.networktoggle.ui.PhoneStatePermissionManager(this, REQ_CODE_PHONE_STATE, this::updateCapabilities);
        
        executionStateController = new com.dhangofa.networktoggle.ui.ExecutionStateController(this, appPreferences, this::setStatus);
        
        TileCycleSyncController tileCycleSyncController = new TileCycleSyncController(
                this, appPreferences, simResolver, modeController);
        tileCycleUiController.setOnCycleChangedListener(tileCycleSyncController);

		tileCycleUiController.initialize();
        executionStateController.registerListeners();
        targetSimUiController = new TargetSimUiController(
                this, appPreferences, this::onTargetSimSelectionChanged);
        targetSimUiController.initialize();
        bindSelectionListeners();
		updateSeparatorVisibility();
        carouselManager = new com.dhangofa.networktoggle.ui.CarouselManager(this);
        carouselManager.setupCarousel(false); // Default to false, loadSavedExecutionMode will correct it
        setupStaticLandscapeCards();
        
        // Load initial mode and update UI authorization state (triggers dimming if NONE)
        loadSavedExecutionMode();
    }

    private void configureStatusBar() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return;

        boolean isNight = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        
        android.view.Window window = getWindow();
        window.setStatusBarColor(getColor(R.color.card_surface));
        
        if (currentThemeMode == 3) {
            window.setNavigationBarColor(Color.BLACK);
        } else {
            window.setNavigationBarColor(getColor(R.color.surface_background));
        }
        
        int flags = isNight ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        if (!isNight && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        window.getDecorView().setSystemUiVisibility(flags);
    }

    private View btnRoutineShortcuts;
    
    private void bindViews() {
        radioGroup = findViewById(R.id.modeRadioGroup);
        radioRoot = findViewById(R.id.radioRoot);
        radioShizuku = findViewById(R.id.radioShizuku);
        statusText = findViewById(R.id.shizukuStatusText);
		appVersionText = findViewById(R.id.appVersionText);
        faqLink = findViewById(R.id.faqLink);
        githubLink = findViewById(R.id.githubLink);
        telegramLink = findViewById(R.id.telegramLink);
		separatorRootShizuku = findViewById(R.id.separatorRootShizuku);

        errorBannerContainer = findViewById(R.id.cardErrorBanner);
        if (errorBannerContainer != null) {
            errorBannerContainer.setOnClickListener(v -> showDiagnosticDialog());
        }
        
        btnRoutineShortcuts = findViewById(R.id.btnRoutineShortcuts);
        if (btnRoutineShortcuts != null) {
            btnRoutineShortcuts.setOnClickListener(v -> com.dhangofa.networktoggle.ui.ShortcutDialogHelper.showDialog(this, appPreferences));
            
            android.widget.ScrollView mainScrollView = findViewById(R.id.mainScrollView);
            if (mainScrollView != null) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    mainScrollView.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
                        if (scrollY > oldScrollY && btnRoutineShortcuts.getScaleX() == 1f) {
                            // Scrolling down (Swiping up) -> Hide FAB
                            btnRoutineShortcuts.animate().scaleX(0f).scaleY(0f).setDuration(200).withEndAction(() -> {
                                btnRoutineShortcuts.setVisibility(View.INVISIBLE);
                            }).start();
                        } else if (scrollY < oldScrollY && btnRoutineShortcuts.getScaleX() == 0f) {
                            // Scrolling up (Swiping down) -> Show FAB
                            btnRoutineShortcuts.setVisibility(View.VISIBLE);
                            btnRoutineShortcuts.animate().scaleX(1f).scaleY(1f).setDuration(200).withEndAction(null).start();
                        }
                    });
                }
            }
        }
    }

    private void bindLinks() {
        githubLink.setOnClickListener(v -> openUrl("https://github.com/Dhangofa/NetToggle"));
        telegramLink.setOnClickListener(v -> openUrl("https://t.me/dhangofas_projects_chat"));
        if (faqLink != null) faqLink.setOnClickListener(v -> openUrl("https://github.com/Dhangofa/NetToggle/wiki/Frequently-Asked-Questions-(FAQ)"));
    }
	

        private void setupStaticLandscapeCards() {
        View btn1 = findViewById(R.id.staticCardButton1);
        if (btn1 != null) {
            btn1.setOnClickListener(v -> openUrl("https://github.com/Dhangofa/NetToggle/wiki/1.-Execution-Mode-Configuration"));
        }
        View btn2 = findViewById(R.id.staticCardButton2);
        if (btn2 != null) {
            btn2.setOnClickListener(v -> openUrl("https://github.com/Dhangofa/NetToggle/wiki/2.-Target-SIM-Setup-&-Quick-Tile-Cycle-Guide"));
        }
        View btn3 = findViewById(R.id.staticCardButton3);
        if (btn3 != null) {
            btn3.setOnClickListener(v -> openUrl("https://github.com/Dhangofa/NetToggle/wiki/3.-Adding-the-Tile-to-Quick-Settings"));
        }
    }

	private void updateSeparatorVisibility() {
        // Execution Mode Separator
        int modeId = radioGroup.getCheckedRadioButtonId();
        if (modeId == -1) {
            separatorRootShizuku.setVisibility(View.VISIBLE);
        } else {
            separatorRootShizuku.setVisibility(View.INVISIBLE);
        }
    }

    private void loadSavedExecutionMode() {
        ExecutionMode savedMode = appPreferences.getExecutionMode();
        if (savedMode == ExecutionMode.ROOT) {
            radioRoot.setChecked(true);
            executionStateController.checkRootPermission();
        } else if (savedMode == ExecutionMode.SHIZUKU) {
            radioShizuku.setChecked(true);
            executionStateController.checkShizukuPermission(false);
        } else {
            radioGroup.clearCheck();
            setStatus(getString(R.string.status_select_mode), 3);
        }
    }

    private void bindSelectionListeners() {
        radioGroup.setOnCheckedChangeListener((group, checkedId) -> {
			updateSeparatorVisibility();
            if (checkedId == R.id.radioRoot) {
                appPreferences.onExecutionModeChanged(ExecutionMode.ROOT);
                executionStateController.checkRootPermission();
            } else if (checkedId == R.id.radioShizuku) {
                appPreferences.onExecutionModeChanged(ExecutionMode.SHIZUKU);
                executionStateController.checkShizukuPermission(true);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (targetSimUiController != null) targetSimUiController.updateAutoSimWarning();
        if (permissionManager != null) permissionManager.checkAndRequest();
        updateErrorBanner();
    }
    
    @Override
    protected void onPause() {
        super.onPause();
    }

    private void updateErrorBanner() {
        if (errorBannerContainer != null && appPreferences != null) {
            DiagnosticError error = appPreferences.getLastError();
            errorBannerContainer.setVisibility(error != null ? View.VISIBLE : View.GONE);
        }
    }

    private void showDiagnosticDialog() {
        if (isFinishing() || activityDestroyed) return;
        
        if (diagnosticDialog != null && diagnosticDialog.isShowing()) {
            diagnosticDialog.dismiss(); appPreferences.clearLastError(); updateErrorBanner();
        }

        diagnosticDialog = DialogHelper.buildDiagnosticDialog(this, appPreferences, new SimResolver(this, appPreferences), () -> {
            appPreferences.clearLastError(); 
            updateErrorBanner();
        });

        diagnosticDialog.show();
    }

    private void onTargetSimSelectionChanged() {
        TargetSim newTarget = appPreferences.getTargetSim();
        if (lastTargetSim != TargetSim.BOTH && newTarget == TargetSim.BOTH) {
            AppExecutors.executeTelephony(() -> {
                simResolver.setOverrideTargetSim(TargetSim.SIM_1);
                NetworkMode mode1 = modeReader.readCurrentMode();
                
                simResolver.setOverrideTargetSim(TargetSim.SIM_2);
                NetworkMode mode2 = modeReader.readCurrentMode();
                simResolver.setOverrideTargetSim(null);
                
                if (mode1 != NetworkMode.UNKNOWN && mode2 != NetworkMode.UNKNOWN && mode1 != mode2) {
                    com.dhangofa.networktoggle.cycle.TileCycleManager tileCycleManager = new com.dhangofa.networktoggle.cycle.TileCycleManager(appPreferences);
                    AppPreferences.NetworkCapabilities combinedCaps = capabilityResolver.getCapabilities(appPreferences.getExecutionMode());
                    tileCycleManager.forceRemoveUnsupportedAndAutoFill(combinedCaps);
                    java.util.List<NetworkMode> cycle = tileCycleManager.getCycle();
                    NetworkMode modeToApply = cycle.contains(mode1) ? mode1 : (cycle.contains(mode2) ? mode2 : cycle.get(0));
                    
                    simResolver.setOverrideTargetSim(TargetSim.SIM_1);
                    modeController.apply(modeToApply, appPreferences.getExecutionMode());
                    simResolver.setOverrideTargetSim(TargetSim.SIM_2);
                    modeController.apply(modeToApply, appPreferences.getExecutionMode());
                    simResolver.setOverrideTargetSim(null);
                    
                    appPreferences.setCachedNetworkMode(NetworkMode.UNKNOWN);
                }
                
                updateCapabilities();
            });
        } else {
            updateCapabilities();
        }
        lastTargetSim = newTarget;
    }

    private void updateCapabilities() {
        AppExecutors.executeTelephony(() -> {
            AppPreferences.NetworkCapabilities caps = capabilityResolver.getCapabilities(appPreferences.getExecutionMode());
            runOnUiThread(() -> {
                if (!activityDestroyed && tileCycleUiController != null) {
                    tileCycleUiController.applyCapabilities(caps);
                }
                android.service.quicksettings.TileService.requestListeningState(MainActivity.this, new android.content.ComponentName(MainActivity.this, NetworkTileService.class));
            });
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (permissionManager != null) {
            permissionManager.handleRequestPermissionsResult(requestCode, grantResults);
        }
    }

    @Override
    protected void onDestroy() {
        activityDestroyed = true;
        if (appPreferences != null) appPreferences.unregisterListener(this);
        
        if (executionStateController != null) {
            executionStateController.destroy();
        }
        
        if (permissionManager != null) {
            permissionManager.destroy();
        }

        super.onDestroy();
    }

    @Override
    public void onSharedPreferenceChanged(android.content.SharedPreferences sharedPreferences, String key) {
        if ("last_error_cmd".equals(key)) {
            runOnUiThread(() -> updateErrorBanner());
        }
    }


	private void setStatus(String text, int colorCode) {
	    statusText.setText(text);
	
	    if (colorCode == 1) { // Success
	        statusText.setTextColor(getColor(R.color.status_success_text));
	        statusText.setBackgroundResource(R.drawable.shape_status_badge_success);
	    } else if (colorCode == 2) { // Error
	        statusText.setTextColor(getColor(R.color.status_error_text));
	        statusText.setBackgroundResource(R.drawable.shape_status_badge_error);
	    } else { // Warning/Neutral
	        statusText.setTextColor(getColor(R.color.status_warning_text));
	        statusText.setBackgroundResource(R.drawable.shape_status_badge_warning);
	    }
	    updateAuthorizationUI(colorCode == 1);
	}
	
    private Boolean isUIAuthorized = null;
    
	private void updateAuthorizationUI(boolean authorized) {
	    if (isUIAuthorized != null && isUIAuthorized == authorized) return;
	    isUIAuthorized = authorized;
	    if (carouselManager != null) carouselManager.updateCarouselContext(authorized);
	    
	    float alpha = authorized ? 1.0f : 0.4f;
        
        if (btnRoutineShortcuts != null) {
            btnRoutineShortcuts.setAlpha(alpha);
            btnRoutineShortcuts.setEnabled(authorized);
        }
	    
	    if (tileCycleUiController != null) {
	        tileCycleUiController.setAuthorized(authorized);
	    }
        if (targetSimUiController != null) {
	        targetSimUiController.setAuthorized(authorized);
	    }
	    
	    if (authorized && appPreferences != null) {
	        appPreferences.clearDeviceCapabilities();
	        appPreferences.invalidateSlotCache(0);
	        appPreferences.invalidateSlotCache(1);
	        updateCapabilities();
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
}


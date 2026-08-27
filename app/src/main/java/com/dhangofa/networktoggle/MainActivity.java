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
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
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
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.widget.Toast;
import com.dhangofa.networktoggle.model.DiagnosticError;
import com.dhangofa.networktoggle.util.DiagnosticReporter;


import rikka.shizuku.Shizuku;

public class MainActivity extends Activity implements android.content.SharedPreferences.OnSharedPreferenceChangeListener {	
	
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

    private ImageView faqLink;
    
    // Morphing View Carousel
    private com.dhangofa.networktoggle.ui.CarouselManager carouselManager;

	private View separatorRootShizuku;
    private View separatorAutoSim1;
    private View separatorSim1Sim2;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        activityDestroyed = false;
        // Triggering fresh deploy to streaming emulator to clear cache/state
        configureStatusBar();
        setContentView(R.layout.activity_main);

        appPreferences = new AppPreferences(this);
        appPreferences.registerListener(this);
        bindViews();
		appVersionText.setText("v" + getAppVersionName());
        bindLinks();
        SimResolver simResolver = new SimResolver(this, appPreferences);
        capabilityResolver = new NetworkCapabilityResolver(appPreferences, simResolver);
		TileCycleManager tileCycleManager = new TileCycleManager(appPreferences);
		tileCycleUiController = new TileCycleUiController(this, tileCycleManager);
        NetworkModeController modeController = new NetworkModeController(simResolver);
        
        permissionManager = new com.dhangofa.networktoggle.ui.PhoneStatePermissionManager(this, REQ_CODE_PHONE_STATE, this::updateCapabilities);
        
        executionStateController = new com.dhangofa.networktoggle.ui.ExecutionStateController(this, appPreferences, this::setStatus);
        
        TileCycleSyncController tileCycleSyncController = new TileCycleSyncController(
                this, appPreferences, simResolver, modeController);
        tileCycleUiController.setOnCycleChangedListener(tileCycleSyncController);

		tileCycleUiController.initialize();
        executionStateController.registerListeners();
        targetSimUiController = new TargetSimUiController(
                this, appPreferences, this::updateCapabilities);
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

        faqLink = findViewById(R.id.faqLink);
		separatorRootShizuku = findViewById(R.id.separatorRootShizuku);
        separatorAutoSim1 = findViewById(R.id.separatorAutoSim1);
        separatorSim1Sim2 = findViewById(R.id.separatorSim1Sim2);

        errorBannerContainer = findViewById(R.id.cardErrorBanner);
        if (errorBannerContainer != null) {
            errorBannerContainer.setOnClickListener(v -> showDiagnosticDialog());
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
            setStatus("Select Root or Shizuku mode.", 0xFFFFB300);
        }
    }

    private boolean updatingSimUi = false;

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

        android.view.View.OnTouchListener lockTouch = (v, event) -> {
            if (!isUIAuthorized && event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                android.widget.Toast.makeText(MainActivity.this, "Please authorize Root or Shizuku to configure toggles.", android.widget.Toast.LENGTH_SHORT).show();
                return true;
            }
            return false;
        };
        radioSimAuto.setOnTouchListener(lockTouch);
        radioSim1.setOnTouchListener(lockTouch);
        radioSim2.setOnTouchListener(lockTouch);

        targetSimRadioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (updatingSimUi) return;
            TargetSim target = TargetSim.AUTO;
            if (checkedId == R.id.radioSim1) target = TargetSim.SIM_1;
            else if (checkedId == R.id.radioSim2) target = TargetSim.SIM_2;
            
            // Validate slot immediately
            if (target != TargetSim.AUTO && checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                android.telephony.SubscriptionManager sm = getSystemService(android.telephony.SubscriptionManager.class);
                if (sm != null) {
                    boolean found = false;
                    java.util.List<android.telephony.SubscriptionInfo> infos = sm.getActiveSubscriptionInfoList();
                    if (infos != null) {
                        for (android.telephony.SubscriptionInfo info : infos) {
                            if (info.getSimSlotIndex() == target.getManualSlotIndex()) {
                                found = true;
                                break;
                            }
                        }
                    }
                    if (!found) {
                        Toast.makeText(MainActivity.this, "No SIM card found in slot " + (target.getManualSlotIndex() + 1), Toast.LENGTH_SHORT).show();
                        updatingSimUi = true;
                        radioSimAuto.setChecked(true);
                        updatingSimUi = false;
                        target = TargetSim.AUTO;
                    }
                }
            }
            
            updateSeparatorVisibility();
            appPreferences.onTargetSimChanged(target);
            updateAutoSimWarning();
            updateCapabilities();
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

    private void updateCapabilities() {
        AppExecutors.executeTelephony(() -> {
            AppPreferences.NetworkCapabilities caps = capabilityResolver.getCapabilities(appPreferences.getExecutionMode());
            runOnUiThread(() -> {
                if (!activityDestroyed && tileCycleUiController != null) {
                    tileCycleUiController.applyCapabilities(caps);
                }
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
	    updateAuthorizationUI(color == 0xFF1B873F);
	}
	
	
    


    
    
    
    private boolean isUIAuthorized = true;
    
	private void updateAuthorizationUI(boolean authorized) {
	    if (isUIAuthorized == authorized) return;
	    isUIAuthorized = authorized;
	    if (carouselManager != null) carouselManager.updateCarouselContext(authorized);
	    
	    float alpha = authorized ? 1.0f : 0.4f;
	    View targetSimCard = findViewById(R.id.targetSimRadioGroup);
	    if (targetSimCard != null) targetSimCard.setAlpha(alpha);
	    View targetSimHeader = findViewById(R.id.targetSimHeaderContainer);
	    if (targetSimHeader != null) targetSimHeader.setAlpha(alpha);
	    View autoSimWarningText = findViewById(R.id.autoSimWarningText);
	    if (autoSimWarningText != null) autoSimWarningText.setAlpha(alpha);
	    
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


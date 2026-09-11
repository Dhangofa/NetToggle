package com.dhangofa.networktoggle;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.VelocityTracker;
import android.view.ViewConfiguration;
import android.view.animation.DecelerateInterpolator;
import android.app.Activity;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.service.quicksettings.TileService;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.View;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import com.dhangofa.networktoggle.NetworkTileService;
import com.dhangofa.networktoggle.R;
import com.dhangofa.networktoggle.config.AppPreferences;
import com.dhangofa.networktoggle.cycle.TileCycleManager;
import com.dhangofa.networktoggle.model.DiagnosticError;
import com.dhangofa.networktoggle.model.ExecutionMode;
import com.dhangofa.networktoggle.model.NetworkMode;
import com.dhangofa.networktoggle.model.TargetSim;
import com.dhangofa.networktoggle.telephony.NetworkCapabilityResolver;
import com.dhangofa.networktoggle.telephony.NetworkModeController;
import com.dhangofa.networktoggle.telephony.NetworkModeReader;
import com.dhangofa.networktoggle.telephony.SimResolver;
import com.dhangofa.networktoggle.ui.BroadcastTabHelper;
import com.dhangofa.networktoggle.ui.DialogHelper;
import com.dhangofa.networktoggle.ui.ExecutionStateController;
import com.dhangofa.networktoggle.ui.PhoneStatePermissionManager;
import com.dhangofa.networktoggle.ui.ShortcutTabHelper;
import com.dhangofa.networktoggle.ui.TargetSimUiController;
import com.dhangofa.networktoggle.ui.TileCycleSyncController;
import com.dhangofa.networktoggle.ui.TileCycleUiController;
import com.dhangofa.networktoggle.util.AppExecutors;
import android.graphics.Rect;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.HorizontalScrollView;
import android.widget.Spinner;
import java.util.List;

public class MainActivity extends Activity implements SharedPreferences.OnSharedPreferenceChangeListener {
    private com.dhangofa.networktoggle.ui.DiagnosticUiController diagnosticUiController;
    private com.dhangofa.networktoggle.ui.ExecutionModeUiController executionModeUiController;
    private com.dhangofa.networktoggle.ui.MainNavigationController navigationController;

    private com.dhangofa.networktoggle.ui.ThemeController themeController;
    private int currentThemeMode = -1;
    private ImageView btnThemeToggle;
    private PhoneStatePermissionManager permissionManager;
    private NetworkCapabilityResolver capabilityResolver;
    private static final int REQ_CODE_PHONE_STATE = 1001;
    private AppPreferences appPreferences;
    private TileCycleUiController tileCycleUiController;
    private volatile boolean activityDestroyed;
    private ExecutionStateController executionStateController;
    private TargetSimUiController targetSimUiController;
    private BroadcastTabHelper broadcastTabHelper;
    private ShortcutTabHelper shortcutTabHelper;
    private SimResolver simResolver;
    private NetworkModeController modeController;
    private NetworkModeReader modeReader;
    private TargetSim lastTargetSim = TargetSim.AUTO;

    public int getCurrentTabIndex() {
        return this.navigationController != null ? this.navigationController.getCurrentTabIndex() : 0;
    }
    public ShortcutTabHelper getShortcutTabHelper() { return this.shortcutTabHelper; }
    public BroadcastTabHelper getBroadcastTabHelper() { return this.broadcastTabHelper; }
    public com.dhangofa.networktoggle.ui.ThemeController getThemeController() { return this.themeController; }

    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent ev) {
        if (this.navigationController != null) {
            return this.navigationController.dispatchTouchEvent(ev);
        }
        return super.dispatchTouchEvent(ev);
    }

    public boolean superDispatchTouchEvent(MotionEvent ev) {
        return super.dispatchTouchEvent(ev);
    }

    protected void attachBaseContext(Context newBase) {
        SharedPreferences prefs = newBase.getSharedPreferences("AppPrefs", 0);
        int mode = prefs.getInt("app_theme", 0);
        Configuration config = new Configuration(newBase.getResources().getConfiguration());
        if (mode == 1) {
            config.uiMode = config.uiMode & 0xFFFFFFCF | 0x10;
        } else if (mode == 2 || mode == 3) {
            config.uiMode = config.uiMode & 0xFFFFFFCF | 0x20;
        }
        super.attachBaseContext(newBase.createConfigurationContext(config));
    }
    public boolean isAmoled() {
        return this.themeController != null && this.themeController.isAmoled();
    }

    protected void onCreate(Bundle savedInstanceState) {
        int savedTab = 0;
        int savedAutomationSubTab = -1;
        if (savedInstanceState != null) {
            savedTab = savedInstanceState.getInt("savedTab", 0);
            savedAutomationSubTab = savedInstanceState.getInt("savedAutomationSubTab", -1);
        }
        this.navigationController = new com.dhangofa.networktoggle.ui.MainNavigationController(this, savedTab, savedAutomationSubTab);
        SharedPreferences prefs = this.getSharedPreferences("AppPrefs", 0);
        this.themeController = new com.dhangofa.networktoggle.ui.ThemeController(this, prefs);
        this.currentThemeMode = this.themeController.getCurrentThemeMode(); // Keep field synced for now if needed by other methods
        super.onCreate(savedInstanceState);
        this.activityDestroyed = false;
        this.themeController.configureStatusBar();
        this.setContentView(R.layout.activity_main);
        this.btnThemeToggle = (ImageView)this.findViewById(R.id.btnThemeToggle);
        this.themeController.bindViews(this.btnThemeToggle, this.findViewById(R.id.navIndicatorPill));
        this.appPreferences = new AppPreferences((Context)this);
        this.lastTargetSim = this.appPreferences.getTargetSim();
        this.appPreferences.registerListener(this);

        this.diagnosticUiController = new com.dhangofa.networktoggle.ui.DiagnosticUiController(this, this.appPreferences);
        this.diagnosticUiController.bindViews();

        com.dhangofa.networktoggle.ui.AppFooterHelper.setupFooter(this);
        this.simResolver = new SimResolver((Context)this, this.appPreferences);
        this.capabilityResolver = new NetworkCapabilityResolver(this.appPreferences, this.simResolver);
        TileCycleManager tileCycleManager = new TileCycleManager(this.appPreferences);
        this.tileCycleUiController = new TileCycleUiController(this, tileCycleManager);
        this.modeController = new NetworkModeController(this.simResolver);
        this.modeReader = new NetworkModeReader((Context)this, this.appPreferences, this.simResolver);
        this.permissionManager = new PhoneStatePermissionManager(this, 1001, this::updateCapabilities);
        
        this.executionStateController = new ExecutionStateController(this, this.appPreferences, (text, color) -> {
            if (this.executionModeUiController != null) {
                this.executionModeUiController.setStatus(text, color);
            }
        });
        this.executionModeUiController = new com.dhangofa.networktoggle.ui.ExecutionModeUiController(this, this.appPreferences, this.executionStateController, this::updateAuthorizationUI);
        this.executionModeUiController.bindViews();

        TileCycleSyncController tileCycleSyncController = new TileCycleSyncController((Context)this, this.appPreferences, this.simResolver, this.modeController);
        this.tileCycleUiController.setOnCycleChangedListener(tileCycleSyncController);
        this.tileCycleUiController.initialize();
        this.executionStateController.registerListeners();
        this.targetSimUiController = new TargetSimUiController(this, this.appPreferences, this::onTargetSimSelectionChanged);
        this.targetSimUiController.initialize();
        this.broadcastTabHelper = BroadcastTabHelper.setupTab(this, this.appPreferences);
        this.shortcutTabHelper = ShortcutTabHelper.setupTab(this, this.appPreferences);
        com.dhangofa.networktoggle.ui.GuidesTabHelper.setupTab(this);
        this.navigationController.initialize();
        this.navigationController.setupKeyboardListener();
        this.navigationController.setupOrientationListener();
    }

    protected void onSaveInstanceState(android.os.Bundle outState) {
        super.onSaveInstanceState(outState);
        if (this.navigationController != null) {
            outState.putInt("savedTab", this.navigationController.getCurrentTabIndex());
        }
        RadioGroup automationSegmentGroup = (RadioGroup) this.findViewById(R.id.automationSegmentGroup);
        if (automationSegmentGroup != null) {
            outState.putInt("savedAutomationSubTab", automationSegmentGroup.getCheckedRadioButtonId());
        }
    }

    protected void onResume() {
        super.onResume();
        if (this.targetSimUiController != null) {
            this.targetSimUiController.updateAutoSimWarning();
        }
        if (this.permissionManager != null) {
            this.permissionManager.checkAndRequest();
        }
        if (this.broadcastTabHelper != null) {
            this.broadcastTabHelper.refreshCapabilities();
        }
        if (this.diagnosticUiController != null) this.diagnosticUiController.updateErrorBanner();
    }

    protected void onPause() {
        super.onPause();
    }

    private void onTargetSimSelectionChanged() {
        TargetSim newTarget = this.appPreferences.getTargetSim();
        if (this.lastTargetSim != TargetSim.BOTH && newTarget == TargetSim.BOTH) {
            AppExecutors.executeTelephony(() -> {
                this.simResolver.setOverrideTargetSim(TargetSim.SIM_1);
                NetworkMode mode1 = this.modeReader.readCurrentMode();
                this.simResolver.setOverrideTargetSim(TargetSim.SIM_2);
                NetworkMode mode2 = this.modeReader.readCurrentMode();
                this.simResolver.setOverrideTargetSim(null);
                if (mode1 != NetworkMode.UNKNOWN && mode2 != NetworkMode.UNKNOWN && mode1 != mode2) {
                    TileCycleManager tileCycleManager = new TileCycleManager(this.appPreferences);
                    AppPreferences.NetworkCapabilities combinedCaps = this.capabilityResolver.getCapabilities(this.appPreferences.getExecutionMode());
                    tileCycleManager.forceRemoveUnsupportedAndAutoFill(combinedCaps);
                    List<NetworkMode> cycle = tileCycleManager.getCycle();
                    NetworkMode modeToApply = cycle.contains((Object)mode1) ? mode1 : (cycle.contains((Object)mode2) ? mode2 : cycle.get(0));
                    this.simResolver.setOverrideTargetSim(TargetSim.SIM_1);
                    this.modeController.apply(modeToApply, this.appPreferences.getExecutionMode());
                    this.simResolver.setOverrideTargetSim(TargetSim.SIM_2);
                    this.modeController.apply(modeToApply, this.appPreferences.getExecutionMode());
                    this.simResolver.setOverrideTargetSim(null);
                    this.appPreferences.setCachedNetworkMode(NetworkMode.UNKNOWN);
                }
                this.updateCapabilities();
            });
        } else {
            this.updateCapabilities();
        }
        this.lastTargetSim = newTarget;
    }

    private void updateCapabilities() {
        AppExecutors.executeTelephony(() -> {
            AppPreferences.NetworkCapabilities caps = this.capabilityResolver.getCapabilities(this.appPreferences.getExecutionMode());
            this.runOnUiThread(() -> {
                if (!this.activityDestroyed && this.tileCycleUiController != null) {
                    this.tileCycleUiController.applyCapabilities(caps);
                }
                if (!this.activityDestroyed && this.broadcastTabHelper != null) {
                    this.broadcastTabHelper.refreshCapabilities();
                }
                TileService.requestListeningState((Context)this, (ComponentName)new ComponentName((Context)this, NetworkTileService.class));
            });
        });
    }

    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (this.permissionManager != null) {
            this.permissionManager.handleRequestPermissionsResult(requestCode, grantResults);
        }
    }

    protected void onDestroy() {
        this.activityDestroyed = true;
        if (this.navigationController != null) {
            this.navigationController.destroy();
        }
        if (this.appPreferences != null) {
            this.appPreferences.unregisterListener(this);
        }
        if (this.executionStateController != null) {
            this.executionStateController.destroy();
        }
        if (this.permissionManager != null) {
            this.permissionManager.destroy();
        }
        
        if (this.diagnosticUiController != null) {
            this.diagnosticUiController.destroy();
        }
        super.onDestroy();
    }

    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if ("last_error_cmd".equals(key)) {
            this.runOnUiThread(() -> { if (this.diagnosticUiController != null) this.diagnosticUiController.updateErrorBanner(); });
        }
    }

    private void updateAuthorizationUI(boolean authorized) {
        float f;
        float alpha = authorized ? 1.0f : 0.4f;
        f = alpha;
        if (this.tileCycleUiController != null) {
            this.tileCycleUiController.setAuthorized(authorized);
        }
        if (this.targetSimUiController != null) {
            this.targetSimUiController.setAuthorized(authorized);
        }
        if (this.broadcastTabHelper != null) {
            this.broadcastTabHelper.setAuthorized(authorized);
        }
        if (this.shortcutTabHelper != null) {
            this.shortcutTabHelper.setAuthorized(authorized);
        }
        if (authorized && this.appPreferences != null) {
            if (this.appPreferences.getDeviceCapabilities() == null) {
                this.updateCapabilities();
            }
        }
    }

    public boolean isExecutionAuthorized() {
        return this.executionModeUiController != null && this.executionModeUiController.isExecutionAuthorized();
    }

}

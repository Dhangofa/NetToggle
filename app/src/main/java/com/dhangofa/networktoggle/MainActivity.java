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
    private View carouselBackground;
    private TextView carouselTitle;
    private TextView carouselDesc;
    private TextView carouselButtonText;
    private ImageView carouselIcon;
    private ImageView carouselButtonIcon;
    private View carouselButton;
    private View carouselIconContainer;
    private LinearLayout carouselIndicators;
    
    private int currentCarouselIndex = 0;
    private float touchStartX = 0f;
    private CarouselItem[] carouselItems;
    
    private static class CarouselItem {
        String title, desc, btnText, url;
        int iconRes, bgColor, accentColor, btnBgColor;
        CarouselItem(String title, String desc, String btnText, String url, int iconRes, int bgColor, int accentColor, int btnBgColor) {
            this.title = title; this.desc = desc; this.btnText = btnText; this.url = url;
            this.iconRes = iconRes; this.bgColor = bgColor; this.accentColor = accentColor; this.btnBgColor = btnBgColor;
        }
    }
	
	private View separatorRootShizuku;
    private View separatorAutoSim1;
    private View separatorSim1Sim2;

    private Dialog permissionDialog;
    private View errorBannerContainer;
    private Dialog diagnosticDialog;
    private NetworkCapabilityResolver capabilityResolver;
    
    private static final int REQ_CODE_PHONE_STATE = 1001;

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
        appPreferences.registerListener(this);
        bindViews();
		appVersionText.setText("v" + getAppVersionName());
        bindLinks();
        SimResolver simResolver = new SimResolver(this, appPreferences);
        capabilityResolver = new NetworkCapabilityResolver(appPreferences, simResolver);
		TileCycleManager tileCycleManager = new TileCycleManager(appPreferences);
		tileCycleUiController = new TileCycleUiController(this, tileCycleManager);
        NetworkModeController modeController = new NetworkModeController(simResolver);
        
        tileCycleUiController.setOnCycleChangedListener(newCycle -> {
            new Thread(() -> {
                NetworkMode currentMode = appPreferences.getCachedNetworkMode();
                
                // If cache is wiped (e.g. from SIM switch), but execution is allowed, read it directly once
                if (currentMode == NetworkMode.UNKNOWN && appPreferences.getExecutionMode() != ExecutionMode.NONE) {
                    currentMode = new com.dhangofa.networktoggle.telephony.NetworkModeReader(this, appPreferences, simResolver).readCurrentMode();
                }

                if (currentMode != NetworkMode.UNKNOWN && !newCycle.contains(currentMode)) {
                    NetworkMode fallbackMode = newCycle.get(0);
                    // Attempt to sync the network state silently
                    modeController.apply(fallbackMode, appPreferences.getExecutionMode());
                    appPreferences.setCachedNetworkMode(fallbackMode);
                } else if (currentMode != NetworkMode.UNKNOWN) {
                    // It's in the cycle, ensure it is cached so UI shows the actual active state
                    appPreferences.setCachedNetworkMode(currentMode);
                }
            }).start();
        });

		tileCycleUiController.initialize();
        registerShizukuListeners();
        loadSavedExecutionMode();
        loadSavedTargetSimMode();
        updateAutoSimWarning();
        bindSelectionListeners();
		updateSeparatorVisibility();
        setupCarousel();
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
        carouselBackground = findViewById(R.id.carouselBackground);
        carouselTitle = findViewById(R.id.carouselTitle);
        carouselDesc = findViewById(R.id.carouselDesc);
        carouselButtonText = findViewById(R.id.carouselButtonText);
        carouselIcon = findViewById(R.id.carouselIcon);
        carouselButtonIcon = findViewById(R.id.carouselButtonIcon);
        carouselButton = findViewById(R.id.carouselButton);
        carouselIconContainer = findViewById(R.id.carouselIconContainer);
        carouselIndicators = findViewById(R.id.carouselIndicators);
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
        if (faqLink != null) faqLink.setOnClickListener(v -> openUrl("https://github.com/Dhangofa/NetToggle/wiki"));
    }
	

    private void setupCarousel() {
        if (carouselBackground == null) return;
        carouselItems = new CarouselItem[]{
            new CarouselItem("New to NetToggle?", "Learn how to set up Execution Modes", "Read Guide", "https://github.com/Dhangofa/NetToggle/wiki/1.-Execution-Mode-Configuration", R.drawable.ic_terminal, R.color.first_pg_bg, R.color.exec_accent, R.color.view_guide_button_bg),
            new CarouselItem("Target SIM & Cycle", "Learn how to configure your modes", "Read Guide", "https://github.com/Dhangofa/NetToggle/wiki/2.-Target-SIM-Setup-&-Quick-Tile-Cycle-Guide", R.drawable.ic_sim_card, R.color.second_pg_bg, R.color.accent_orange, R.color.view_guide_button_bg),
            new CarouselItem("Quick Settings Ready", "Add the tile to your Control Center", "Read Guide", "https://github.com/Dhangofa/NetToggle/wiki/3.-Adding-the-Tile-to-Quick-Settings", R.drawable.ic_network_bars, R.color.third_pg_bg, R.color.accent_pink, R.color.view_guide_button_bg)
        };
        
        setupCarouselIndicators(carouselItems.length);
        // Start state depends on initial auth state. 
        // We defer to updateCarouselContext which will be triggered by loadSavedExecutionMode -> setStatus -> updateAuthorizationUI
        // But let's set a default page just in case
        renderCarouselPage(isUIAuthorized ? 1 : 0, false, 0);
        
        carouselBackground.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    touchStartX = event.getX();
                    return true;
                case android.view.MotionEvent.ACTION_UP:
                case android.view.MotionEvent.ACTION_CANCEL:
                    float deltaX = event.getX() - touchStartX;
                    if (Math.abs(deltaX) > 100) { 
                        carouselHandler.removeCallbacks(carouselAutoRunnable);
                        if (deltaX > 0 && currentCarouselIndex > 0) {
                            currentCarouselIndex--;
                            renderCarouselPage(currentCarouselIndex, true, -1);
                        } else if (deltaX < 0 && currentCarouselIndex < carouselItems.length - 1) {
                            currentCarouselIndex++;
                            renderCarouselPage(currentCarouselIndex, true, 1);
                        }
                        // Resume after 10 seconds of inactivity
                        carouselHandler.postDelayed(carouselAutoRunnable, 10000);
                    } else if (event.getAction() == android.view.MotionEvent.ACTION_UP) {
                        v.performClick();
                    }
                    return true;
            }
            return false;
        });
        
        carouselBackground.setOnClickListener(v -> {
            // Needed to ensure ripple works when performClick is called
        });
    }

    private void renderCarouselPage(int index, boolean animate, int direction) {
        CarouselItem item = carouselItems[index];
        Runnable updateViews = () -> {
            carouselTitle.setText(item.title);
            carouselDesc.setText(item.desc);
            carouselButtonText.setText(item.btnText);
            carouselButtonText.setTextColor(getColor(item.accentColor));
            
            carouselIcon.setImageResource(item.iconRes);
            carouselIcon.setColorFilter(getColor(item.accentColor));
            carouselButtonIcon.setColorFilter(getColor(item.accentColor));
            
            carouselBackground.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getColor(item.bgColor)));
            carouselButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getColor(item.btnBgColor)));
            carouselIconContainer.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getColor(item.btnBgColor)));
            
            carouselButton.setOnClickListener(v -> {
                try { startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(item.url))); } catch (Exception e) {}
            });
            updateCarouselIndicators(index);
        };

        if (animate) {
            float moveOutX = direction * -100f; // Swipe left (next) -> content moves left (-100).
            float moveInX = direction * 100f; // Swipe left -> new content comes from right (+100).
            long duration = 200;

            // Animate background color smoothly
            android.animation.ValueAnimator colorAnim = android.animation.ValueAnimator.ofObject(
                new android.animation.ArgbEvaluator(),
                carouselBackground.getBackgroundTintList().getDefaultColor(),
                getColor(item.bgColor)
            );
            colorAnim.setDuration(duration);
            colorAnim.addUpdateListener(animator -> {
                carouselBackground.setBackgroundTintList(android.content.res.ColorStateList.valueOf((int) animator.getAnimatedValue()));
            });
            colorAnim.start();

            // Animate content out
            android.view.View[] viewsToAnimate = {carouselTitle, carouselDesc, carouselButton, carouselIconContainer};
            for (android.view.View view : viewsToAnimate) {
                view.animate().translationX(moveOutX).alpha(0f).setDuration(duration / 2)
                    .setInterpolator(new android.view.animation.AccelerateInterpolator())
                    .withEndAction(() -> {
                        // Once out, swap data
                        if (view == carouselTitle) updateViews.run();
                        
                        // Prepare for animate in
                        view.setTranslationX(moveInX);
                        view.animate().translationX(0f).alpha(1f).setDuration(duration)
                            .setInterpolator(new android.view.animation.DecelerateInterpolator())
                            .start();
                    }).start();
            }
        } else {
            updateViews.run();
        }
    }

    private void setupCarouselIndicators(int count) {
        if (carouselIndicators == null) return;
        carouselIndicators.removeAllViews();
        for (int i = 0; i < count; i++) {
            android.widget.ImageView dot = new android.widget.ImageView(this);
            dot.setImageDrawable(getDrawable(R.drawable.shape_dot_inactive));
            android.widget.LinearLayout.LayoutParams params = new android.widget.LinearLayout.LayoutParams(16, 16);
            params.setMargins(8, 0, 8, 0);
            dot.setLayoutParams(params);
            carouselIndicators.addView(dot);
        }
    }

    private void updateCarouselIndicators(int position) {
        if (carouselIndicators == null) return;
        for (int i = 0; i < carouselIndicators.getChildCount(); i++) {
            android.widget.ImageView dot = (android.widget.ImageView) carouselIndicators.getChildAt(i);
            if (i == position) {
                dot.setImageDrawable(getDrawable(R.drawable.shape_dot_active));
            } else {
                dot.setImageDrawable(getDrawable(R.drawable.shape_dot_inactive));
            }
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

    private boolean updatingSimUi = false;

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
        if (appPreferences != null) updateAutoSimWarning();
        checkAndRequestPermission();
        updateErrorBanner();
        updateCapabilities();
        if (carouselHandler != null && carouselAutoRunnable != null) {
            carouselHandler.removeCallbacks(carouselAutoRunnable);
            carouselHandler.postDelayed(carouselAutoRunnable, 5000);
        }
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        if (carouselHandler != null && carouselAutoRunnable != null) {
            carouselHandler.removeCallbacks(carouselAutoRunnable);
        }
    }

    private void checkAndRequestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            boolean granted = checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED;
            if (!granted) {
                boolean shouldShowRationale = shouldShowRequestPermissionRationale(android.Manifest.permission.READ_PHONE_STATE);
                if (shouldShowRationale) {
                    showPermissionBottomSheet();
                } else {
                    // Automatically prompt on first open if not asked yet
                    requestPermissions(new String[]{android.Manifest.permission.READ_PHONE_STATE}, REQ_CODE_PHONE_STATE);
                }
            } else if (permissionDialog != null && permissionDialog.isShowing()) {
                permissionDialog.dismiss();
            }
        }
    }

    private void showPermissionBottomSheet() {
        if (permissionDialog == null) {
            permissionDialog = new Dialog(this, R.style.TransparentBottomSheetStyle);
            View view = getLayoutInflater().inflate(R.layout.bottom_sheet_permission, null);
            
            view.findViewById(R.id.btnDismissPermission).setOnClickListener(v -> permissionDialog.dismiss());
            view.findViewById(R.id.btnGrantPermission).setOnClickListener(v -> {
                permissionDialog.dismiss();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    requestPermissions(new String[]{android.Manifest.permission.READ_PHONE_STATE}, REQ_CODE_PHONE_STATE);
                }
            });
            
            permissionDialog.setContentView(view);
            Window window = permissionDialog.getWindow();
            if (window != null) {
                window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                window.setGravity(Gravity.BOTTOM);
                window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            }
        }
        
        if (!permissionDialog.isShowing() && !activityDestroyed) {
            permissionDialog.show();
        }
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

        diagnosticDialog = new Dialog(this);
        diagnosticDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        diagnosticDialog.setContentView(R.layout.dialog_diagnostic);
        diagnosticDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        diagnosticDialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);

        TextView reportText = diagnosticDialog.findViewById(R.id.diagnosticReportText);
        if (reportText != null) {
            String report = DiagnosticReporter.generateReport(appPreferences, new SimResolver(this, appPreferences));
            reportText.setText(report);
        }

        View btnClose = diagnosticDialog.findViewById(R.id.btnCloseDiagnostic);
        if (btnClose != null) btnClose.setOnClickListener(v -> { diagnosticDialog.dismiss(); appPreferences.clearLastError(); updateErrorBanner(); });

        View btnCopy = diagnosticDialog.findViewById(R.id.btnCopyDiagnostic);
        if (btnCopy != null) {
            btnCopy.setOnClickListener(v -> {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("Diagnostic Report", reportText.getText());
                clipboard.setPrimaryClip(clip);
                Toast.makeText(this, "Report copied to clipboard", Toast.LENGTH_SHORT).show();
            });
        }

        diagnosticDialog.show();
    }

    private void updateCapabilities() {
        new Thread(() -> {
            AppPreferences.NetworkCapabilities caps = capabilityResolver.getCapabilities(appPreferences.getExecutionMode());
            runOnUiThread(() -> {
                if (!activityDestroyed && tileCycleUiController != null) {
                    tileCycleUiController.applyCapabilities(caps);
                }
            });
        }).start();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_CODE_PHONE_STATE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (permissionDialog != null && permissionDialog.isShowing()) permissionDialog.dismiss();
                updateCapabilities(); // Fetch capabilities immediately upon grant
            } else {
                showPermissionBottomSheet();
            }
        }
    }

    @Override
    protected void onDestroy() {
        activityDestroyed = true;
        if (appPreferences != null) appPreferences.unregisterListener(this);
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

    @Override
    public void onSharedPreferenceChanged(android.content.SharedPreferences sharedPreferences, String key) {
        if ("last_error_cmd".equals(key)) {
            runOnUiThread(() -> updateErrorBanner());
        }
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
                if (finalGranted) {
                    setStatus("Root mode active & authorized!", 0xFF1B873F);
                    appPreferences.setTileErrorState(com.dhangofa.networktoggle.config.AppPreferences.TILE_ERROR_NONE);
                } else {
                    setStatus("Root permission denied or unavailable.", 0xFFFF5555);
                    appPreferences.setTileErrorState(com.dhangofa.networktoggle.config.AppPreferences.TILE_ERROR_ROOT);
                }
                android.service.quicksettings.TileService.requestListeningState(MainActivity.this, new android.content.ComponentName(MainActivity.this, NetworkTileService.class));
            });
        });
        rootCheckThread.start();
    }

    private void checkShizukuPermission(boolean requestIfNeeded) {
        if (activityDestroyed) return;
        try {
            if (!Shizuku.pingBinder()) {
                setStatus("Shizuku is not running.", 0xFFFF5555);
                if (appPreferences != null) {
                    appPreferences.setTileErrorState(com.dhangofa.networktoggle.config.AppPreferences.TILE_ERROR_SHIZUKU);
                    android.service.quicksettings.TileService.requestListeningState(MainActivity.this, new android.content.ComponentName(MainActivity.this, NetworkTileService.class));
                }
                return;
            }
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                setStatus("Shizuku mode active & authorized!", 0xFF1B873F);
                if (appPreferences != null) {
                    appPreferences.setTileErrorState(com.dhangofa.networktoggle.config.AppPreferences.TILE_ERROR_NONE);
                    android.service.quicksettings.TileService.requestListeningState(MainActivity.this, new android.content.ComponentName(MainActivity.this, NetworkTileService.class));
                }
                return;
            }
            setStatus("Shizuku permission not granted.", 0xFFFFB300);
            if (appPreferences != null) {
                appPreferences.setTileErrorState(com.dhangofa.networktoggle.config.AppPreferences.TILE_ERROR_SHIZUKU);
                android.service.quicksettings.TileService.requestListeningState(MainActivity.this, new android.content.ComponentName(MainActivity.this, NetworkTileService.class));
            }
            if (requestIfNeeded) {
                setStatus("Requesting Shizuku permission...", 0xFFFFB300);
                Shizuku.requestPermission(0);
            }
        } catch (Exception e) {
            setStatus("Shizuku check failed.", 0xFFFF5555);
            if (appPreferences != null) {
                appPreferences.setTileErrorState(com.dhangofa.networktoggle.config.AppPreferences.TILE_ERROR_SHIZUKU);
                android.service.quicksettings.TileService.requestListeningState(MainActivity.this, new android.content.ComponentName(MainActivity.this, NetworkTileService.class));
            }
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
	
	
    private android.os.Handler carouselHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    
    private Runnable carouselAutoRunnable = new Runnable() {
        @Override
        public void run() {
            if (carouselItems == null) return;
            
            int nextIndex;
            if (!isUIAuthorized) {
                // If unauthorized but user manually swiped away, bring them back to 0 eventually
                nextIndex = 0;
            } else {
                // If authorized, cycle through logically
                nextIndex = (currentCarouselIndex == 2) ? 1 : (currentCarouselIndex + 1);
            }
            
            if (nextIndex != currentCarouselIndex) {
                int direction = (nextIndex > currentCarouselIndex) ? 1 : -1;
                currentCarouselIndex = nextIndex;
                renderCarouselPage(currentCarouselIndex, true, direction);
            }
            carouselHandler.postDelayed(this, 5000);
        }
    };
    
    private void updateCarouselContext(boolean authorized) {
        if (carouselBackground == null || carouselItems == null) return;
        carouselHandler.removeCallbacks(carouselAutoRunnable);

        if (!authorized) {
            if (currentCarouselIndex != 0) {
                currentCarouselIndex = 0;
                renderCarouselPage(0, true, -1);
            }
            // Will auto-correct to 0 every 5 seconds if user swipes away while unauthorized
            carouselHandler.postDelayed(carouselAutoRunnable, 5000);
        } else {
            if (currentCarouselIndex == 0) {
                currentCarouselIndex = 1;
                renderCarouselPage(1, true, 1);
            }
            carouselHandler.postDelayed(carouselAutoRunnable, 5000);
        }
    }

    private boolean isUIAuthorized = false;
	private void updateAuthorizationUI(boolean authorized) {
	    updateCarouselContext(authorized);
	    if (isUIAuthorized == authorized) return;
	    isUIAuthorized = authorized;
	    
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


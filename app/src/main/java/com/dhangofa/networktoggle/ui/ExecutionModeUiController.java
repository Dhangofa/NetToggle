package com.dhangofa.networktoggle.ui;

import android.app.Activity;
import android.transition.ChangeBounds;
import android.transition.Fade;
import android.transition.TransitionManager;
import android.transition.TransitionSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import com.dhangofa.networktoggle.MainActivity;
import com.dhangofa.networktoggle.R;
import com.dhangofa.networktoggle.config.AppPreferences;
import com.dhangofa.networktoggle.model.ExecutionMode;
import rikka.shizuku.Shizuku;

public class ExecutionModeUiController {
    private final Activity activity;
    private final AppPreferences appPreferences;
    private final ExecutionStateController stateController;
    
    public interface AuthStateCallback {
        void onAuthStateChanged(boolean authorized);
    }
    private final AuthStateCallback authStateCallback;

    public interface ModeChangeListener {
        void onModeChanged(ExecutionMode mode);
    }
    private ModeChangeListener modeChangeListener;
    
    // Main Container Cards
    private View cardSetupHero;
    private View cardExecutionAndTarget;
    
    // Setup Hero elements
    private View cardSelectShizuku;
    private View cardSelectRoot;
    private ImageView radioIndicatorShizuku;
    private ImageView radioIndicatorRoot;
    private View shizukuStatusDot;
    private TextView shizukuLiveStatusText;
    private View rootStatusDot;
    private TextView rootLiveStatusText;
    private View btnSetupAuthorize;
    private TextView setupActionBtnText;
    private View btnOpenGuidesLink;
    
    // Authorized Compact Card elements
    private RadioGroup radioGroup;
    private RadioButton radioRoot;
    private RadioButton radioShizuku;
    private TextView statusText;
    private View separatorRootShizuku;
    
    private ExecutionMode selectedSetupMode = ExecutionMode.SHIZUKU;
    private Boolean isUIAuthorized = null;
    private boolean isProgrammaticCheck = false;
    
    public ExecutionModeUiController(Activity activity, AppPreferences appPreferences, ExecutionStateController stateController, AuthStateCallback authStateCallback) {
        this.activity = activity;
        this.appPreferences = appPreferences;
        this.stateController = stateController;
        this.authStateCallback = authStateCallback;
    }

    public void setOnModeChangeListener(ModeChangeListener listener) {
        this.modeChangeListener = listener;
    }
    
    public void bindViews() {
        // Main Cards (Support both include ID and layout root ID)
        this.cardSetupHero = activity.findViewById(R.id.cardSetupHero);
        if (this.cardSetupHero == null) {
            this.cardSetupHero = activity.findViewById(R.id.cardSetupHeroRoot);
        }
        this.cardExecutionAndTarget = activity.findViewById(R.id.cardExecutionAndTarget);
        
        // Setup Hero Views
        this.cardSelectShizuku = activity.findViewById(R.id.cardSelectShizuku);
        this.cardSelectRoot = activity.findViewById(R.id.cardSelectRoot);
        this.radioIndicatorShizuku = activity.findViewById(R.id.radioIndicatorShizuku);
        this.radioIndicatorRoot = activity.findViewById(R.id.radioIndicatorRoot);
        this.shizukuStatusDot = activity.findViewById(R.id.shizukuStatusDot);
        this.shizukuLiveStatusText = activity.findViewById(R.id.shizukuLiveStatusText);
        this.rootStatusDot = activity.findViewById(R.id.rootStatusDot);
        this.rootLiveStatusText = activity.findViewById(R.id.rootLiveStatusText);
        this.btnSetupAuthorize = activity.findViewById(R.id.btnSetupAuthorize);
        this.setupActionBtnText = activity.findViewById(R.id.setupActionBtnText);
        this.btnOpenGuidesLink = activity.findViewById(R.id.btnOpenGuidesLink);
        
        // Authorized Views
        this.radioGroup = activity.findViewById(R.id.modeRadioGroup);
        this.radioRoot = activity.findViewById(R.id.radioRoot);
        this.radioShizuku = activity.findViewById(R.id.radioShizuku);
        this.statusText = activity.findViewById(R.id.shizukuStatusText);
        this.separatorRootShizuku = activity.findViewById(R.id.separatorRootShizuku);
        
        bindSetupHeroListeners();
        bindAuthorizedListeners();
        loadSavedExecutionMode();
    }
    
    private void bindSetupHeroListeners() {
        if (cardSelectShizuku != null) {
            cardSelectShizuku.setOnClickListener(v -> selectSetupMode(ExecutionMode.SHIZUKU));
        }
        if (cardSelectRoot != null) {
            cardSelectRoot.setOnClickListener(v -> selectSetupMode(ExecutionMode.ROOT));
        }
        if (btnSetupAuthorize != null) {
            btnSetupAuthorize.setOnClickListener(v -> {
                if (selectedSetupMode == ExecutionMode.SHIZUKU) {
                    appPreferences.onExecutionModeChanged(ExecutionMode.SHIZUKU);
                    stateController.checkShizukuPermission(true);
                } else if (selectedSetupMode == ExecutionMode.ROOT) {
                    updateRootHeroLiveStatus(true, false, false);
                    appPreferences.onExecutionModeChanged(ExecutionMode.ROOT);
                    stateController.checkRootPermission();
                }
            });
        }
        if (btnOpenGuidesLink != null) {
            btnOpenGuidesLink.setOnClickListener(v -> {
                if (activity instanceof MainActivity) {
                    ((MainActivity) activity).getNavigationController().switchTab(2);
                }
            });
        }
    }
    
    private void selectSetupMode(ExecutionMode mode) {
        if (this.selectedSetupMode == mode) return;
        this.selectedSetupMode = mode;
        View selectedView = (mode == ExecutionMode.SHIZUKU) ? cardSelectShizuku : cardSelectRoot;
        if (selectedView != null) {
            selectedView.setScaleX(0.96f);
            selectedView.setScaleY(0.96f);
            selectedView.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(160)
                    .setInterpolator(new OvershootInterpolator(1.2f))
                    .start();
        }
        if (mode == ExecutionMode.SHIZUKU) {
            if (cardSelectShizuku != null) {
                cardSelectShizuku.setBackgroundResource(R.drawable.shape_setup_option_selected);
            }
            if (radioIndicatorShizuku != null) {
                radioIndicatorShizuku.setImageResource(R.drawable.shape_radio_selected);
            }
            if (cardSelectRoot != null) {
                cardSelectRoot.setBackgroundResource(R.drawable.shape_setup_option_unselected);
            }
            if (radioIndicatorRoot != null) {
                radioIndicatorRoot.setImageResource(R.drawable.shape_radio_unselected);
            }
            updateActionButtonTextWithCrossfade(R.string.btn_authorize_shizuku);
        } else {
            if (cardSelectRoot != null) {
                cardSelectRoot.setBackgroundResource(R.drawable.shape_setup_option_selected);
            }
            if (radioIndicatorRoot != null) {
                radioIndicatorRoot.setImageResource(R.drawable.shape_radio_selected);
            }
            if (cardSelectShizuku != null) {
                cardSelectShizuku.setBackgroundResource(R.drawable.shape_setup_option_unselected);
            }
            if (radioIndicatorShizuku != null) {
                radioIndicatorShizuku.setImageResource(R.drawable.shape_radio_unselected);
            }
            updateActionButtonTextWithCrossfade(R.string.btn_grant_root);
        }
    }

    private void updateActionButtonTextWithCrossfade(int stringResId) {
        if (setupActionBtnText == null) return;
        String newText = activity.getString(stringResId);
        if (setupActionBtnText.getText().toString().equals(newText)) return;
        setupActionBtnText.animate()
                .alpha(0f)
                .setDuration(80)
                .withEndAction(() -> {
                    setupActionBtnText.setText(newText);
                    setupActionBtnText.animate()
                            .alpha(1f)
                            .setDuration(120)
                            .start();
                })
                .start();
    }
    
    private void bindAuthorizedListeners() {
        if (this.radioGroup != null) {
            this.radioGroup.setOnCheckedChangeListener((group, checkedId) -> {
                if (isProgrammaticCheck) {
                    this.updateSeparatorVisibility();
                    return;
                }
                this.updateSeparatorVisibility();
                
                ExecutionMode newMode = (checkedId == R.id.radioRoot) ? ExecutionMode.ROOT : ExecutionMode.SHIZUKU;
                ExecutionMode oldMode = this.appPreferences.getExecutionMode();
                
                if (isExecutionAuthorized() && oldMode != newMode) {
                    animateSegmentSelection(checkedId);
                    animateCardFeedback();
                }
                
                this.appPreferences.onExecutionModeChanged(newMode);
                
                if (this.modeChangeListener != null) {
                    this.modeChangeListener.onModeChanged(newMode);
                }
                
                if (checkedId == R.id.radioRoot) {
                    this.stateController.checkRootPermission();
                } else if (checkedId == R.id.radioShizuku) {
                    this.stateController.checkShizukuPermission(true);
                }
            });
        }
    }
    
    private void updateSeparatorVisibility() {
        if (this.radioGroup == null || this.separatorRootShizuku == null) return;
        int modeId = this.radioGroup.getCheckedRadioButtonId();
        if (modeId == -1) {
            this.separatorRootShizuku.setVisibility(View.VISIBLE);
        } else {
            this.separatorRootShizuku.setVisibility(View.INVISIBLE);
        }
    }

    private void loadSavedExecutionMode() {
        isProgrammaticCheck = true;
        try {
            ExecutionMode savedMode = this.appPreferences.getExecutionMode();
            if (savedMode == ExecutionMode.ROOT) {
                selectSetupMode(ExecutionMode.ROOT);
                if (this.radioRoot != null) this.radioRoot.setChecked(true);
                this.stateController.checkRootPermission();
            } else if (savedMode == ExecutionMode.SHIZUKU) {
                selectSetupMode(ExecutionMode.SHIZUKU);
                if (this.radioShizuku != null) this.radioShizuku.setChecked(true);
                this.stateController.checkShizukuPermission(false);
            } else {
                selectSetupMode(ExecutionMode.SHIZUKU);
                if (this.radioGroup != null) this.radioGroup.clearCheck();
                this.setStatus(activity.getString(R.string.status_select_mode), 3);
            }
        } finally {
            isProgrammaticCheck = false;
        }
        updateSeparatorVisibility();
        updateShizukuHeroLiveStatus();
        updateRootHeroLiveStatus(false, false, false);
    }
    
    private void updateShizukuHeroLiveStatus() {
        if (shizukuLiveStatusText == null || shizukuStatusDot == null) return;
        try {
            boolean running = Shizuku.pingBinder();
            if (running) {
                shizukuStatusDot.setBackgroundTintList(activity.getColorStateList(R.color.status_success_icon));
                shizukuLiveStatusText.setText(R.string.shizuku_status_running);
                shizukuLiveStatusText.setTextColor(activity.getColor(R.color.status_success_text));
            } else {
                shizukuStatusDot.setBackgroundTintList(activity.getColorStateList(R.color.status_warning_icon));
                shizukuLiveStatusText.setText(R.string.shizuku_status_not_running);
                shizukuLiveStatusText.setTextColor(activity.getColor(R.color.status_warning_text));
            }
        } catch (Exception e) {
            shizukuStatusDot.setBackgroundTintList(activity.getColorStateList(R.color.status_warning_icon));
            shizukuLiveStatusText.setText(R.string.shizuku_status_not_running);
            shizukuLiveStatusText.setTextColor(activity.getColor(R.color.status_warning_text));
        }
    }
    
    private void updateRootHeroLiveStatus(boolean checking, boolean denied, boolean granted) {
        if (rootLiveStatusText == null || rootStatusDot == null) return;
        if (checking) {
            rootStatusDot.setBackgroundTintList(activity.getColorStateList(R.color.status_warning_icon));
            rootLiveStatusText.setText(R.string.root_status_checking);
            rootLiveStatusText.setTextColor(activity.getColor(R.color.status_warning_text));
        } else if (denied) {
            rootStatusDot.setBackgroundTintList(activity.getColorStateList(R.color.status_error_icon));
            rootLiveStatusText.setText(R.string.root_status_denied);
            rootLiveStatusText.setTextColor(activity.getColor(R.color.status_error_text));
        } else if (granted) {
            rootStatusDot.setBackgroundTintList(activity.getColorStateList(R.color.status_success_icon));
            rootLiveStatusText.setText(R.string.root_status_active);
            rootLiveStatusText.setTextColor(activity.getColor(R.color.status_success_text));
        } else {
            rootStatusDot.setBackgroundTintList(activity.getColorStateList(R.color.status_warning_icon));
            rootLiveStatusText.setText(R.string.root_status_not_granted);
            rootLiveStatusText.setTextColor(activity.getColor(R.color.status_warning_text));
        }
    }

    private void applyStatusColor(int colorCode) {
        if (this.statusText == null) return;
        if (colorCode == 1) {
            this.statusText.setTextColor(activity.getColor(R.color.status_success_text));
            this.statusText.setBackgroundResource(R.drawable.shape_status_badge_success);
        } else if (colorCode == 2) {
            this.statusText.setTextColor(activity.getColor(R.color.status_error_text));
            this.statusText.setBackgroundResource(R.drawable.shape_status_badge_error);
        } else {
            this.statusText.setTextColor(activity.getColor(R.color.status_warning_text));
            this.statusText.setBackgroundResource(R.drawable.shape_status_badge_warning);
        }
    }

    private void applyStatusTextDirect(String text, int colorCode) {
        if (this.statusText != null) {
            this.statusText.setText(text);
            applyStatusColor(colorCode);
        }
    }

    private void animateStatusChange(String text, int colorCode) {
        if (this.statusText == null) return;
        this.statusText.animate()
                .alpha(0f)
                .scaleX(0.85f)
                .scaleY(0.85f)
                .setDuration(100)
                .withEndAction(() -> {
                    this.statusText.setText(text);
                    applyStatusColor(colorCode);
                    this.statusText.animate()
                            .alpha(1f)
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(180)
                            .setInterpolator(new OvershootInterpolator(1.2f))
                            .start();
                })
                .start();
    }

    private void animateSegmentSelection(int checkedId) {
        RadioButton selected = activity.findViewById(checkedId);
        if (selected != null) {
            selected.setScaleX(0.94f);
            selected.setScaleY(0.94f);
            selected.animate()
                    .scaleX(1.05f)
                    .scaleY(1.05f)
                    .setDuration(120)
                    .setInterpolator(new DecelerateInterpolator())
                    .withEndAction(() -> selected.animate()
                            .scaleX(1.0f)
                            .scaleY(1.0f)
                            .setDuration(120)
                            .setInterpolator(new OvershootInterpolator(1.2f))
                            .start())
                    .start();
        }
        int otherId = (checkedId == R.id.radioRoot) ? R.id.radioShizuku : R.id.radioRoot;
        RadioButton unselected = activity.findViewById(otherId);
        if (unselected != null) {
            unselected.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start();
        }
    }

    private void animateCardFeedback() {
        if (cardExecutionAndTarget != null) {
            cardExecutionAndTarget.animate()
                    .scaleX(1.015f)
                    .scaleY(1.015f)
                    .setDuration(120)
                    .setInterpolator(new DecelerateInterpolator())
                    .withEndAction(() -> cardExecutionAndTarget.animate()
                            .scaleX(1.0f)
                            .scaleY(1.0f)
                            .setDuration(160)
                            .setInterpolator(new DecelerateInterpolator())
                            .start())
                    .start();
        }
    }

    private void animateTransitionToAuthorized(boolean authorized) {
        ViewGroup parent = null;
        if (cardSetupHero != null && cardSetupHero.getParent() instanceof ViewGroup) {
            parent = (ViewGroup) cardSetupHero.getParent();
        } else if (cardExecutionAndTarget != null && cardExecutionAndTarget.getParent() instanceof ViewGroup) {
            parent = (ViewGroup) cardExecutionAndTarget.getParent();
        }

        if (parent != null) {
            TransitionSet transition = new TransitionSet();
            transition.setOrdering(TransitionSet.ORDERING_TOGETHER);
            ChangeBounds changeBounds = new ChangeBounds();
            changeBounds.setDuration(340);
            changeBounds.setInterpolator(new DecelerateInterpolator());
            transition.addTransition(changeBounds);
            Fade fade = new Fade();
            fade.setDuration(220);
            transition.addTransition(fade);
            TransitionManager.beginDelayedTransition(parent, transition);
        }

        float density = activity.getResources().getDisplayMetrics().density;
        float offset = 10f * density;

        if (authorized) {
            if (cardSetupHero != null) {
                cardSetupHero.animate()
                        .alpha(0f)
                        .scaleY(0.88f)
                        .setDuration(200)
                        .withEndAction(() -> {
                            cardSetupHero.setVisibility(View.GONE);
                            cardSetupHero.setAlpha(1f);
                            cardSetupHero.setScaleY(1f);
                        })
                        .start();
            }
            if (cardExecutionAndTarget != null) {
                cardExecutionAndTarget.setVisibility(View.VISIBLE);
                cardExecutionAndTarget.setAlpha(0f);
                cardExecutionAndTarget.setScaleY(0.88f);
                cardExecutionAndTarget.setTranslationY(-offset);
                cardExecutionAndTarget.animate()
                        .alpha(1f)
                        .scaleY(1f)
                        .translationY(0f)
                        .setDuration(340)
                        .setInterpolator(new DecelerateInterpolator())
                        .start();
            }
        } else {
            if (cardExecutionAndTarget != null) {
                cardExecutionAndTarget.animate()
                        .alpha(0f)
                        .scaleY(0.88f)
                        .setDuration(200)
                        .withEndAction(() -> {
                            cardExecutionAndTarget.setVisibility(View.GONE);
                            cardExecutionAndTarget.setAlpha(1f);
                            cardExecutionAndTarget.setScaleY(1f);
                        })
                        .start();
            }
            if (cardSetupHero != null) {
                cardSetupHero.setVisibility(View.VISIBLE);
                cardSetupHero.setAlpha(0f);
                cardSetupHero.setScaleY(0.88f);
                cardSetupHero.setTranslationY(-offset);
                cardSetupHero.animate()
                        .alpha(1f)
                        .scaleY(1f)
                        .translationY(0f)
                        .setDuration(340)
                        .setInterpolator(new DecelerateInterpolator())
                        .start();
            }
        }
    }
    
    public void setStatus(String text, int colorCode) {
        boolean authorized = (colorCode == 1);
        boolean stateChanged = (this.isUIAuthorized != null && this.isUIAuthorized != authorized);
        boolean isFirstRun = (this.isUIAuthorized == null);
        
        // Handle collapse / expand animations between Setup Hero and Execution Card
        if (stateChanged) {
            animateTransitionToAuthorized(authorized);
        } else if (isFirstRun) {
            if (cardSetupHero != null) {
                cardSetupHero.setVisibility(authorized ? View.GONE : View.VISIBLE);
            }
            if (cardExecutionAndTarget != null) {
                cardExecutionAndTarget.setVisibility(authorized ? View.VISIBLE : View.GONE);
            }
        }
        
        if (authorized) {
            ExecutionMode currentMode = appPreferences.getExecutionMode();
            if (radioGroup != null) {
                isProgrammaticCheck = true;
                try {
                    if (currentMode == ExecutionMode.ROOT && radioRoot != null && !radioRoot.isChecked()) {
                        radioRoot.setChecked(true);
                    } else if (currentMode == ExecutionMode.SHIZUKU && radioShizuku != null && !radioShizuku.isChecked()) {
                        radioShizuku.setChecked(true);
                    }
                } finally {
                    isProgrammaticCheck = false;
                }
            }
            updateSeparatorVisibility();
            if (!stateChanged && !isFirstRun) {
                animateStatusChange(text, colorCode);
            } else {
                applyStatusTextDirect(text, colorCode);
            }
        } else {
            applyStatusTextDirect(text, colorCode);
            updateShizukuHeroLiveStatus();
            if (colorCode == 2 && selectedSetupMode == ExecutionMode.ROOT) {
                updateRootHeroLiveStatus(false, true, false);
            } else {
                updateRootHeroLiveStatus(false, false, false);
            }
        }
        
        if (this.isUIAuthorized == null || this.isUIAuthorized != authorized) {
            this.isUIAuthorized = authorized;
            if (this.authStateCallback != null) {
                this.authStateCallback.onAuthStateChanged(authorized);
            }
        }
    }
    
    public boolean isExecutionAuthorized() {
        return this.isUIAuthorized != null && this.isUIAuthorized;
    }
}

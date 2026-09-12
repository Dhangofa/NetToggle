package com.dhangofa.networktoggle.ui;

/**
 * UI controller to render and manage the drag-and-drop network cycle list in the app.
 * It handles adding, removing, and reordering the network modes that the QS tile will cycle through.
 */

import android.app.Activity;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.CheckBox;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.dhangofa.networktoggle.R;
import com.dhangofa.networktoggle.config.AppPreferences;
import com.dhangofa.networktoggle.cycle.TileCycleManager;
import com.dhangofa.networktoggle.model.NetworkMode;

import java.util.List;

public final class TileCycleUiController {
    private final Activity activity;
    private final TileCycleManager cycleManager;
    private final AppPreferences appPreferences;

    private final CheckBox modePref5g;
    private final CheckBox modePref4g;
    private final CheckBox modePref3g;
    private final CheckBox mode5gOnly;
    private final CheckBox mode4gOnly;
    private final CheckBox mode2gOnly;

    private final View separatorCyclePref1;
    private final View separatorCyclePref2;
    private final View separatorCycleOnly1;
    private final View separatorCycleOnly2;

    private final TextView selectedCount;
    private final TextView cycleOrder;
    private final View lockBadge;
    private final Switch switchAutoRestore;
    private final View rowAutoRestoreToggle;
    private boolean updatingUi;

    private AppPreferences.NetworkCapabilities currentCaps;
    private OnCycleChangedListener cycleChangedListener;
    private Boolean isAuthorized = null;

    public interface OnCycleChangedListener {
        void onCycleChanged(List<NetworkMode> newCycle);
    }

    public TileCycleUiController(Activity activity, TileCycleManager cycleManager, AppPreferences appPreferences) {
        this.activity = activity;
        this.cycleManager = cycleManager;
        this.appPreferences = appPreferences;

        modePref5g = activity.findViewById(R.id.cyclePreferred5g);
        modePref4g = activity.findViewById(R.id.cyclePreferred4g);
        modePref3g = activity.findViewById(R.id.cyclePreferred3g);
        mode5gOnly = activity.findViewById(R.id.cycle5gOnly);
        mode4gOnly = activity.findViewById(R.id.cycle4gOnly);
        mode2gOnly = activity.findViewById(R.id.cycle2gOnly);

        separatorCyclePref1 = activity.findViewById(R.id.separatorCyclePref1);
        separatorCyclePref2 = activity.findViewById(R.id.separatorCyclePref2);
        separatorCycleOnly1 = activity.findViewById(R.id.separatorCycleOnly1);
        separatorCycleOnly2 = activity.findViewById(R.id.separatorCycleOnly2);

        selectedCount = activity.findViewById(R.id.cycleSelectedCount);
        cycleOrder = activity.findViewById(R.id.cycleOrderText);
        lockBadge = activity.findViewById(R.id.tileCycleLockBadge);
        switchAutoRestore = activity.findViewById(R.id.switchAutoRestore);
        rowAutoRestoreToggle = activity.findViewById(R.id.rowAutoRestoreToggle);
    }

    public void setOnCycleChangedListener(OnCycleChangedListener listener) {
        this.cycleChangedListener = listener;
    }

    public void initialize() {
        refresh();

        android.view.View.OnTouchListener lockTouch = (v, event) -> {
            boolean isAuth = isAuthorized != null && isAuthorized;
            if (!isAuth && event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                showToast(activity.getString(R.string.toast_auth_required_tile));
                return true; // Consume event to prevent visual change
            }
            return false;
        };
        modePref5g.setOnTouchListener(lockTouch);
        modePref4g.setOnTouchListener(lockTouch);
        modePref3g.setOnTouchListener(lockTouch);
        mode5gOnly.setOnTouchListener(lockTouch);
        mode4gOnly.setOnTouchListener(lockTouch);
        mode2gOnly.setOnTouchListener(lockTouch);

        modePref5g.setOnCheckedChangeListener((button, selected) ->
                handleSelection(NetworkMode.PREFERRED_5G, selected));
        modePref4g.setOnCheckedChangeListener((button, selected) ->
                handleSelection(NetworkMode.PREFERRED_4G, selected));
        modePref3g.setOnCheckedChangeListener((button, selected) ->
                handleSelection(NetworkMode.PREFERRED_3G, selected));
        mode5gOnly.setOnCheckedChangeListener((button, selected) ->
                handleSelection(NetworkMode.FIVE_G_ONLY, selected));
        mode4gOnly.setOnCheckedChangeListener((button, selected) ->
                handleSelection(NetworkMode.FOUR_G_ONLY, selected));
        mode2gOnly.setOnCheckedChangeListener((button, selected) ->
                handleSelection(NetworkMode.TWO_G_ONLY, selected));

        if (switchAutoRestore != null) {
            switchAutoRestore.setOnTouchListener((v, event) -> {
                boolean isAuth = isAuthorized != null && isAuthorized;
                if (!isAuth) {
                    if (event.getAction() == MotionEvent.ACTION_DOWN) {
                        showToast(activity.getString(R.string.toast_auth_required_tile));
                    }
                    return true;
                }
                return false;
            });

            switchAutoRestore.setOnClickListener(v -> {
                boolean isAuth = isAuthorized != null && isAuthorized;
                if (!isAuth) {
                    switchAutoRestore.setChecked(false);
                    showToast(activity.getString(R.string.toast_auth_required_tile));
                }
            });

            switchAutoRestore.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (updatingUi) return;
                boolean isAuth = isAuthorized != null && isAuthorized;
                if (!isAuth && isChecked) {
                    switchAutoRestore.setChecked(false);
                    showToast(activity.getString(R.string.toast_auth_required_tile));
                    return;
                }
                if (appPreferences != null) {
                    appPreferences.setAutoRestorePreferredModeEnabled(isChecked);
                }
            });
        }

        if (rowAutoRestoreToggle != null) {
            rowAutoRestoreToggle.setOnClickListener(v -> {
                boolean isAuth = isAuthorized != null && isAuthorized;
                if (!isAuth) {
                    showToast(activity.getString(R.string.toast_auth_required_tile));
                    return;
                }
                if (switchAutoRestore != null) {
                    switchAutoRestore.toggle();
                }
            });
        }
    }

    public void setAuthorized(boolean authorized) {
        if (this.isAuthorized != null && this.isAuthorized == authorized) return;
        boolean animate = (this.isAuthorized != null);
        this.isAuthorized = authorized;
        float alpha = authorized ? 1.0f : 0.75f;

        View card = activity.findViewById(R.id.cardTileCycle);
        if (card != null) {
            if (animate) {
                if (authorized) {
                    float offset = 6f * activity.getResources().getDisplayMetrics().density;
                    card.setTranslationY(offset);
                    card.animate()
                            .alpha(alpha)
                            .translationY(0f)
                            .setDuration(320)
                            .setInterpolator(new DecelerateInterpolator())
                            .start();
                } else {
                    card.animate()
                            .alpha(alpha)
                            .translationY(0f)
                            .setDuration(320)
                            .setInterpolator(new DecelerateInterpolator())
                            .start();
                }
            } else {
                card.setAlpha(alpha);
                card.setTranslationY(0f);
            }
        }
        if (lockBadge != null) {
            if (authorized) {
                if (animate && lockBadge.getVisibility() == View.VISIBLE) {
                    lockBadge.animate()
                            .alpha(0f)
                            .scaleX(0.4f)
                            .scaleY(0.4f)
                            .setDuration(220)
                            .withEndAction(() -> {
                                lockBadge.setVisibility(View.GONE);
                                lockBadge.setScaleX(1f);
                                lockBadge.setScaleY(1f);
                                lockBadge.setAlpha(1f);
                            })
                            .start();
                } else {
                    lockBadge.setVisibility(View.GONE);
                }
            } else {
                if (animate && lockBadge.getVisibility() != View.VISIBLE) {
                    lockBadge.setVisibility(View.VISIBLE);
                    lockBadge.setAlpha(0f);
                    lockBadge.setScaleX(0.4f);
                    lockBadge.setScaleY(0.4f);
                    lockBadge.animate()
                            .alpha(1f)
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(250)
                            .setInterpolator(new OvershootInterpolator(1.2f))
                            .start();
                } else {
                    lockBadge.setVisibility(View.VISIBLE);
                    lockBadge.setAlpha(1f);
                    lockBadge.setScaleX(1f);
                    lockBadge.setScaleY(1f);
                }
            }
        }

        if (switchAutoRestore != null && appPreferences != null) {
            updatingUi = true;
            if (!authorized && appPreferences.isAutoRestorePreferredModeEnabled()) {
                appPreferences.setAutoRestorePreferredModeEnabled(false);
            }
            switchAutoRestore.setChecked(authorized && appPreferences.isAutoRestorePreferredModeEnabled());
            updatingUi = false;
        }
    }

    private void animateAlpha(View view, float targetAlpha) {
        if (view == null) return;
        if (Math.abs(view.getAlpha() - targetAlpha) > 0.01f) {
            view.animate().alpha(targetAlpha).setDuration(250).setInterpolator(new DecelerateInterpolator()).start();
        }
    }

    public void applyCapabilities(AppPreferences.NetworkCapabilities caps) {
        if (caps == null) return;
        this.currentCaps = caps;

        animateAlpha(modePref5g, caps.supports5g ? 1.0f : 0.4f);
        animateAlpha(mode5gOnly, caps.supports5g ? 1.0f : 0.4f);
        animateAlpha(modePref3g, caps.supports3g ? 1.0f : 0.4f);
        animateAlpha(mode2gOnly, caps.supports2g ? 1.0f : 0.4f);

        if (cycleManager.forceRemoveUnsupportedAndAutoFill(caps)) {
            showToast("Cycle auto-adjusted for current SIM capabilities");
            if (cycleChangedListener != null) {
                cycleChangedListener.onCycleChanged(cycleManager.getCycle());
            }
        }

        refresh();
    }

    private void handleSelection(NetworkMode mode, boolean selected) {
        if (updatingUi) return;

        if (!isAuthorized) {
            showToast("Please authorize Root or Shizuku to configure toggles.");
            refresh(); // Revert checkbox visual change
            return;
        }

        if (selected && currentCaps != null) {
            boolean supported = true;
            String reason = "";
            if ((mode == NetworkMode.PREFERRED_5G || mode == NetworkMode.FIVE_G_ONLY) && !currentCaps.supports5g) {
                supported = false;
                reason = activity.getString(R.string.tilecycle_5g_unsupported);
            } else if (mode == NetworkMode.PREFERRED_3G && !currentCaps.supports3g) {
                supported = false;
                reason = activity.getString(R.string.tilecycle_3g_unsupported);
            } else if (mode == NetworkMode.TWO_G_ONLY && !currentCaps.supports2g) {
                supported = false;
                reason = activity.getString(R.string.tilecycle_2g_unsupported);
            }

            if (!supported) {
                showToast(reason);
                refresh(); // Revert UI to match the actual saved cycle
                return;
            }
        }

        TileCycleManager.ChangeResult result = cycleManager.setSelected(mode, selected);
        if (result == TileCycleManager.ChangeResult.CHANGED) {
            if (cycleChangedListener != null) {
                cycleChangedListener.onCycleChanged(cycleManager.getCycle());
            }
        } else if (result == TileCycleManager.ChangeResult.MINIMUM_REACHED) {
            showToast(activity.getString(R.string.toast_select_at_least_2));
        } else if (result == TileCycleManager.ChangeResult.MAXIMUM_REACHED) {
            showToast(activity.getString(R.string.toast_select_up_to_3));
        }
        refresh();
    }

    private void refresh() {
        updatingUi = true;
        List<NetworkMode> cycle = cycleManager.getCycle();

        modePref5g.setChecked(cycle.contains(NetworkMode.PREFERRED_5G));
        modePref4g.setChecked(cycle.contains(NetworkMode.PREFERRED_4G));
        modePref3g.setChecked(cycle.contains(NetworkMode.PREFERRED_3G));
        mode5gOnly.setChecked(cycle.contains(NetworkMode.FIVE_G_ONLY));
        mode4gOnly.setChecked(cycle.contains(NetworkMode.FOUR_G_ONLY));
        mode2gOnly.setChecked(cycle.contains(NetworkMode.TWO_G_ONLY));

        // Hide separators if either adjacent button is checked to create a seamless pill background
        separatorCyclePref1.setVisibility(modePref5g.isChecked() || modePref4g.isChecked() ? View.INVISIBLE : View.VISIBLE);
        separatorCyclePref2.setVisibility(modePref4g.isChecked() || modePref3g.isChecked() ? View.INVISIBLE : View.VISIBLE);
        separatorCycleOnly1.setVisibility(mode5gOnly.isChecked() || mode4gOnly.isChecked() ? View.INVISIBLE : View.VISIBLE);
        separatorCycleOnly2.setVisibility(mode4gOnly.isChecked() || mode2gOnly.isChecked() ? View.INVISIBLE : View.VISIBLE);

        selectedCount.setText(activity.getString(R.string.cycle_selected_count, cycle.size()));
        cycleOrder.setText(buildOrderText(cycle));

        if (switchAutoRestore != null && appPreferences != null) {
            boolean isChecked = (isAuthorized != null && isAuthorized) && appPreferences.isAutoRestorePreferredModeEnabled();
            switchAutoRestore.setChecked(isChecked);
        }

        updatingUi = false;
    }

    private String buildOrderText(List<NetworkMode> cycle) {
        StringBuilder text = new StringBuilder(activity.getString(R.string.cycle_prefix));
        for (int i = 0; i < cycle.size(); i++) {
            if (i > 0) text.append("  →  ");
            text.append(cycle.get(i).getDisplayName());
        }
        return text.toString();
    }

    private void showToast(String message) {
        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show();
    }
}

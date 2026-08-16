package com.dhangofa.networktoggle.ui;

import android.app.Activity;
import android.view.View;
import android.widget.CheckBox;
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
    private boolean updatingUi;

    private AppPreferences.NetworkCapabilities currentCaps;
    private OnCycleChangedListener cycleChangedListener;
    private boolean isAuthorized = true;

    public interface OnCycleChangedListener {
        void onCycleChanged(List<NetworkMode> newCycle);
    }

    public TileCycleUiController(Activity activity, TileCycleManager cycleManager) {
        this.activity = activity;
        this.cycleManager = cycleManager;
        
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
    }

    public void setOnCycleChangedListener(OnCycleChangedListener listener) {
        this.cycleChangedListener = listener;
    }

    public void initialize() {
        refresh();
        
        android.view.View.OnTouchListener lockTouch = (v, event) -> {
            if (!isAuthorized && event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                showToast("Please authorize Root or Shizuku to configure toggles.");
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
    }

    public void setAuthorized(boolean authorized) {
        if (this.isAuthorized == authorized) return;
        this.isAuthorized = authorized;
        float alpha = authorized ? 1.0f : 0.4f;
        
        View card = activity.findViewById(R.id.cardTileCycle);
        if (card != null) {
            card.setAlpha(alpha);
        }
    }

    public void applyCapabilities(AppPreferences.NetworkCapabilities caps) {
        if (caps == null) return;
        this.currentCaps = caps;
        
        modePref5g.setAlpha(caps.supports5g ? 1.0f : 0.4f);
        mode5gOnly.setAlpha(caps.supports5g ? 1.0f : 0.4f);
        
        modePref3g.setAlpha(caps.supports3g ? 1.0f : 0.4f);
        
        mode2gOnly.setAlpha(caps.supports2g ? 1.0f : 0.4f);
        
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
                reason = "5G is not supported by your device or current carrier.";
            } else if (mode == NetworkMode.PREFERRED_3G && !currentCaps.supports3g) {
                supported = false;
                reason = "3G is disabled or not supported by your current carrier.";
            } else if (mode == NetworkMode.TWO_G_ONLY && !currentCaps.supports2g) {
                supported = false;
                reason = "2G is disabled or not supported by your current carrier.";
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
            showToast("Select at least 2 tile modes.");
        } else if (result == TileCycleManager.ChangeResult.MAXIMUM_REACHED) {
            showToast("You can select up to 3 tile modes.");
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
        
        selectedCount.setText("Selected: " + cycle.size() + "/3");
        cycleOrder.setText(buildOrderText(cycle));
        updatingUi = false;
    }

    private String buildOrderText(List<NetworkMode> cycle) {
        StringBuilder text = new StringBuilder("Cycle: ");
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

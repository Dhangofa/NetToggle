package com.dhangofa.networktoggle.ui;

import android.app.Activity;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import com.dhangofa.networktoggle.R;
import com.dhangofa.networktoggle.cycle.TileCycleManager;
import com.dhangofa.networktoggle.model.NetworkMode;

import java.util.List;

public final class TileCycleUiController {
    private final Activity activity;
    private final TileCycleManager cycleManager;
    private final CheckBox mode5gOnly;
    private final CheckBox mode4gOnly;
    private final CheckBox modePref5g;
    private final CheckBox modePref4g;
    private final TextView selectedCount;
    private final TextView cycleOrder;
    private boolean updatingUi;

    public TileCycleUiController(Activity activity, TileCycleManager cycleManager) {
        this.activity = activity;
        this.cycleManager = cycleManager;
        mode5gOnly = activity.findViewById(R.id.cycle5gOnly);
        mode4gOnly = activity.findViewById(R.id.cycle4gOnly);
        modePref5g = activity.findViewById(R.id.cyclePreferred5g);
        modePref4g = activity.findViewById(R.id.cyclePreferred4g);
        selectedCount = activity.findViewById(R.id.cycleSelectedCount);
        cycleOrder = activity.findViewById(R.id.cycleOrderText);
    }

    public void initialize() {
        refresh();
        mode5gOnly.setOnCheckedChangeListener((button, selected) ->
                handleSelection(NetworkMode.FIVE_G_ONLY, selected));
        mode4gOnly.setOnCheckedChangeListener((button, selected) ->
                handleSelection(NetworkMode.FOUR_G_ONLY, selected));
        modePref5g.setOnCheckedChangeListener((button, selected) ->
                handleSelection(NetworkMode.PREFERRED_5G, selected));
        modePref4g.setOnCheckedChangeListener((button, selected) ->
                handleSelection(NetworkMode.PREFERRED_4G, selected));
    }

    private void handleSelection(NetworkMode mode, boolean selected) {
        if (updatingUi) return;

        TileCycleManager.ChangeResult result = cycleManager.setSelected(mode, selected);
        if (result == TileCycleManager.ChangeResult.MINIMUM_REACHED) {
            showToast("Select at least 2 tile modes.");
        } else if (result == TileCycleManager.ChangeResult.MAXIMUM_REACHED) {
            showToast("You can select up to 3 tile modes.");
        }
        refresh();
    }

    private void refresh() {
        updatingUi = true;
        List<NetworkMode> cycle = cycleManager.getCycle();
        mode5gOnly.setChecked(cycle.contains(NetworkMode.FIVE_G_ONLY));
        mode4gOnly.setChecked(cycle.contains(NetworkMode.FOUR_G_ONLY));
        modePref5g.setChecked(cycle.contains(NetworkMode.PREFERRED_5G));
        modePref4g.setChecked(cycle.contains(NetworkMode.PREFERRED_4G));
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

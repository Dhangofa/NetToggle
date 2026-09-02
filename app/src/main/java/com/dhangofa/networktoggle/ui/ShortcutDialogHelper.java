package com.dhangofa.networktoggle.ui;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Toast;

import com.dhangofa.networktoggle.R;
import com.dhangofa.networktoggle.ShortcutActionActivity;
import com.dhangofa.networktoggle.config.AppPreferences;


import java.util.ArrayList;
import java.util.List;

public class ShortcutDialogHelper {

    public static void showDialog(Activity activity, AppPreferences prefs) {
        android.app.Dialog dialog = new android.app.Dialog(activity, R.style.TransparentBottomSheetStyle);
        View view = LayoutInflater.from(activity).inflate(R.layout.dialog_routine_shortcuts, null);
        dialog.setContentView(view);
        android.view.Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setGravity(android.view.Gravity.BOTTOM);
        }

        LinearLayout container = view.findViewById(R.id.shortcutsContainer);
        View btnAdd = view.findViewById(R.id.btnAddShortcut);
        View btnSave = view.findViewById(R.id.btnSaveShortcuts);
        View btnCancel = view.findViewById(R.id.btnCancel);
        btnCancel.setOnClickListener(v -> dialog.dismiss());
        

        LayoutInflater inflater = LayoutInflater.from(activity);
        List<View> rows = new ArrayList<>();

        boolean hasSim1 = false;
        boolean hasSim2 = false;

        if (activity.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            SubscriptionManager sm = activity.getSystemService(SubscriptionManager.class);
            if (sm != null) {
                try {
                    List<SubscriptionInfo> infos = sm.getActiveSubscriptionInfoList();
                    if (infos != null) {
                        for (SubscriptionInfo info : infos) {
                            if (info.getSimSlotIndex() == 0) hasSim1 = true;
                            if (info.getSimSlotIndex() == 1) hasSim2 = true;
                        }
                    }
                } catch (Exception ignored) {}
            }
        } else {
            // Assume both if no permission
            hasSim1 = true;
            hasSim2 = true;
        }

        // Failsafe in case OS returned empty list incorrectly but capabilities exist
        if (!hasSim1 && !hasSim2) {
            hasSim1 = true;
        }

        List<String> simDisplayList = new ArrayList<>();
        List<Integer> simValueList = new ArrayList<>();

        if (hasSim1) {
            simDisplayList.add("SIM 1");
            simValueList.add(1);
        }
        if (hasSim2) {
            simDisplayList.add("SIM 2");
            simValueList.add(2);
        }
        if (hasSim1 && hasSim2) {
            simDisplayList.add("Both");
            simValueList.add(3);
        }

        AppPreferences.NetworkCapabilities deviceCaps = prefs.getDeviceCapabilities();
        if (deviceCaps == null) deviceCaps = AppPreferences.NetworkCapabilities.assumeAll();
        AppPreferences.NetworkCapabilities sim1Caps = prefs.getSlotCapabilities(0);
        if (sim1Caps == null) sim1Caps = deviceCaps;
        AppPreferences.NetworkCapabilities sim2Caps = prefs.getSlotCapabilities(1);
        if (sim2Caps == null) sim2Caps = deviceCaps;

        // BOTH capabilities should be the intersection (lowest common denominator) of SIM 1 and SIM 2
        AppPreferences.NetworkCapabilities bothCaps = new AppPreferences.NetworkCapabilities(
            sim1Caps.supports2g && sim2Caps.supports2g,
            sim1Caps.supports3g && sim2Caps.supports3g,
            sim1Caps.supports4g && sim2Caps.supports4g,
            sim1Caps.supports5g && sim2Caps.supports5g
        );

        AppPreferences.NetworkCapabilities finalSim1Caps = sim1Caps;
        AppPreferences.NetworkCapabilities finalSim2Caps = sim2Caps;
        AppPreferences.NetworkCapabilities finalBothCaps = bothCaps;

        Runnable updateAddButton = () -> {
            boolean canAdd = rows.size() < 4;
            btnAdd.setAlpha(canAdd ? 1f : 0.5f);
            btnAdd.setEnabled(canAdd);
            
        };

        Runnable addRow = new Runnable() {
            public void run() {
                if (rows.size() >= 4) return;
                
                View row = inflater.inflate(R.layout.item_routine_shortcut, container, false);
                Spinner spinnerSim = row.findViewById(R.id.spinnerSim);
                Spinner spinnerMode = row.findViewById(R.id.spinnerMode);
                ImageView btnRemove = row.findViewById(R.id.btnRemoveShortcut);

                ArrayAdapter<String> simAdapter = new ArrayAdapter<>(activity, android.R.layout.simple_spinner_dropdown_item, simDisplayList);
                spinnerSim.setAdapter(simAdapter);

                spinnerSim.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                        int simValue = simValueList.get(position);
                        AppPreferences.NetworkCapabilities caps;
                        if (simValue == 1) caps = finalSim1Caps;
                        else if (simValue == 2) caps = finalSim2Caps;
                        else caps = finalBothCaps;

                        List<String> validModes = new ArrayList<>();
                        List<String> validDisplays = new ArrayList<>();

                        if (caps.supports5g) {
                            validModes.add("5G_ONLY"); validDisplays.add("5G Only");
                            validModes.add("PREF_5G"); validDisplays.add("Pref 5G");
                        }
                        if (caps.supports4g) {
                            validModes.add("4G_ONLY"); validDisplays.add("4G Only");
                            validModes.add("PREF_4G"); validDisplays.add("Pref 4G");
                        }
                        if (caps.supports3g) {
                            validModes.add("PREF_3G"); validDisplays.add("Pref 3G");
                        }
                        if (caps.supports2g) {
                            validModes.add("2G_ONLY"); validDisplays.add("2G Only");
                        }
                        
                        row.setTag(validModes);
                        
                        ArrayAdapter<String> modeAdapter = new ArrayAdapter<>(activity, android.R.layout.simple_spinner_dropdown_item, validDisplays);
                        spinnerMode.setAdapter(modeAdapter);
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {}
                });

                btnRemove.setOnClickListener(v -> {
                    container.removeView(row);
                    rows.remove(row);
                    updateAddButton.run();
                });

                rows.add(row);
                container.addView(row);
                updateAddButton.run();
            }
        };

        btnAdd.setOnClickListener(v -> addRow.run());

        // Load existing
        for (int i = 1; i <= 4; i++) {
            String savedMode = prefs.getRoutineShortcutMode(i);
            if (!"NONE".equals(savedMode)) { 
                int savedSim = prefs.getRoutineShortcutSim(i); // 1=SIM1, 2=SIM2, 3=Both
                
                int mappedIndex = simValueList.indexOf(savedSim);
                if (mappedIndex == -1) {
                    // Sim no longer available. Still load it so they can see and delete/change it,
                    // but default to the first available SIM.
                    mappedIndex = 0; 
                }

                addRow.run();
                View row = rows.get(rows.size() - 1);
                Spinner spinnerSim = row.findViewById(R.id.spinnerSim);
                
                spinnerSim.setSelection(mappedIndex);
                
                // Hacky delay to let the spinner selection listener fire and populate the mode adapter
                final String modeToSelect = savedMode;
                row.postDelayed(() -> {
                    Spinner sm = row.findViewById(R.id.spinnerMode);
                    List<String> vModes = (List<String>) row.getTag();
                    if (vModes != null) {
                        for (int k = 0; k < vModes.size(); k++) {
                            if (vModes.get(k).equals(modeToSelect)) {
                                sm.setSelection(k);
                                break;
                            }
                        }
                    }
                }, 50);
            }
        }

        btnSave.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                ShortcutManager shortcutManager = activity.getSystemService(ShortcutManager.class);
                if (shortcutManager != null) {
                    List<ShortcutInfo> shortcuts = new ArrayList<>();
                    List<String> seenCombos = new ArrayList<>();
                    
                    int slot = 1;
                    for (View row : rows) {
                        Spinner spinnerSim = row.findViewById(R.id.spinnerSim);
                        Spinner spinnerMode = row.findViewById(R.id.spinnerMode);
                        
                        int simSelection = spinnerSim.getSelectedItemPosition();
                        int simValue = simValueList.get(simSelection);
                        
                        List<String> vModes = (List<String>) row.getTag();
                        if (vModes == null || vModes.isEmpty()) continue;
                        
                        int modeSelection = spinnerMode.getSelectedItemPosition();
                        if (modeSelection < 0 || modeSelection >= vModes.size()) modeSelection = 0;
                        
                        String selectedMode = vModes.get(modeSelection);
                        String comboKey = simValue + "_" + selectedMode;

                        if (seenCombos.contains(comboKey)) {
                            Toast.makeText(activity, activity.getString(R.string.toast_skipped_duplicate_shortcut), Toast.LENGTH_SHORT).show();
                            continue;
                        }
                        seenCombos.add(comboKey);
                        
                        prefs.saveRoutineShortcut(slot, selectedMode, simValue);
                        
                        String simLabel = simDisplayList.get(simSelection);
                        String modeLabel = spinnerMode.getSelectedItem().toString();
                        String shortLabel = (simValue == 3 ? "" : simLabel + " ") + modeLabel;
                        
                        Intent intent = new Intent(activity, ShortcutActionActivity.class);
                        intent.setAction("com.dhangofa.networktoggle.SHORTCUT_ACTION_" + slot);
                        intent.putExtra("mode", selectedMode);
                        intent.putExtra("sim", simValue);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        
                        ShortcutInfo shortcut = new ShortcutInfo.Builder(activity, "routine_slot_" + slot)
                                .setShortLabel(shortLabel)
                                .setLongLabel("Switch to " + modeLabel + " (" + simLabel + ")")
                                .setIcon(Icon.createWithResource(activity, R.drawable.ic_shortcut_network))
                                .setIntent(intent)
                                .build();
                                
                        shortcuts.add(shortcut);
                        slot++;
                    }
                    
                    for (int i = slot; i <= 4; i++) {
                        prefs.saveRoutineShortcut(i, "NONE", 1);
                    }
                    
                    try {
                        shortcutManager.setDynamicShortcuts(shortcuts);
                        Toast.makeText(activity, activity.getString(R.string.toast_shortcuts_updated), Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        Toast.makeText(activity, activity.getString(R.string.toast_shortcuts_failed), Toast.LENGTH_SHORT).show();
                    }
                }
            } else {
                Toast.makeText(activity, activity.getString(R.string.toast_shortcuts_require_7_1), Toast.LENGTH_SHORT).show();
            }
            dialog.dismiss();
        });

        dialog.show();
    }
}

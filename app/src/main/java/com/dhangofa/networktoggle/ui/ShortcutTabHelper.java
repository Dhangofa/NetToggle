package com.dhangofa.networktoggle.ui;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.content.res.Configuration;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.dhangofa.networktoggle.MainActivity;
import com.dhangofa.networktoggle.R;
import com.dhangofa.networktoggle.ShortcutActionActivity;
import com.dhangofa.networktoggle.automation.AutomationExecutor;
import com.dhangofa.networktoggle.automation.AutomationRequest;
import com.dhangofa.networktoggle.config.AppPreferences;
import com.dhangofa.networktoggle.model.NetworkMode;
import com.dhangofa.networktoggle.model.TargetSim;
import com.dhangofa.networktoggle.util.AppExecutors;

import java.util.ArrayList;
import java.util.List;

public class ShortcutTabHelper {

    private final Activity activity;
    private final AppPreferences prefs;
    private final List<View> rows = new ArrayList<>();
    private boolean isAuthorized = false;
    private boolean isRestoringState = false;
    private String lastSyncedSignature = null;
    private boolean isKeyboardOpen = false;

    public ShortcutTabHelper(Activity activity, AppPreferences prefs) {
        this.activity = activity;
        this.prefs = prefs;
        if (activity instanceof MainActivity) {
            this.isAuthorized = ((MainActivity) activity).isExecutionAuthorized();
        }
    }

    public static ShortcutTabHelper setupTab(Activity activity, AppPreferences prefs) {
        ShortcutTabHelper helper = new ShortcutTabHelper(activity, prefs);
        helper.setup();
        return helper;
    }

    public void setAuthorized(boolean authorized) {
        this.isAuthorized = authorized;
        float alpha = authorized ? 1.0f : 0.4f;
        for (View row : rows) {
            View btnTestShortcut = row.findViewById(R.id.btnTestShortcut);
            if (btnTestShortcut != null) {
                btnTestShortcut.setAlpha(alpha);
            }
        }
    }

    public void setup() {
        ViewGroup container = activity.findViewById(R.id.shortcutsContainer);
        View btnAdd = activity.findViewById(R.id.btnAddShortcut);
        TextView badgeCount = activity.findViewById(R.id.badgeShortcutCount);
        View bannerLimitReached = activity.findViewById(R.id.bannerLimitReached);

        if (container == null || btnAdd == null) return;

        LayoutInflater inflater = LayoutInflater.from(activity);
        rows.clear();
        container.removeAllViews();

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
            hasSim1 = true;
            hasSim2 = true;
        }

        if (!hasSim1 && !hasSim2) {
            hasSim1 = true;
        }

        List<String> simDisplayList = new ArrayList<>();
        List<Integer> simValueList = new ArrayList<>();

        if (hasSim1) {
            simDisplayList.add(activity.getString(R.string.sim_1));
            simValueList.add(1);
        }
        if (hasSim2) {
            simDisplayList.add(activity.getString(R.string.sim_2));
            simValueList.add(2);
        }
        if (hasSim1 && hasSim2) {
            simDisplayList.add(activity.getString(R.string.both));
            simValueList.add(3);
        }

        AppPreferences.NetworkCapabilities deviceCaps = prefs.getDeviceCapabilities();
        if (deviceCaps == null) deviceCaps = AppPreferences.NetworkCapabilities.assumeAll();
        AppPreferences.NetworkCapabilities sim1Caps = prefs.getSlotCapabilities(0);
        if (sim1Caps == null) sim1Caps = deviceCaps;
        AppPreferences.NetworkCapabilities sim2Caps = prefs.getSlotCapabilities(1);
        if (sim2Caps == null) sim2Caps = deviceCaps;

        AppPreferences.NetworkCapabilities bothCaps = new AppPreferences.NetworkCapabilities(
            sim1Caps.supports2g && sim2Caps.supports2g,
            sim1Caps.supports3g && sim2Caps.supports3g,
            sim1Caps.supports4g && sim2Caps.supports4g,
            sim1Caps.supports5g && sim2Caps.supports5g
        );

        final AppPreferences.NetworkCapabilities finalSim1Caps = sim1Caps;
        final AppPreferences.NetworkCapabilities finalSim2Caps = sim2Caps;
        final AppPreferences.NetworkCapabilities finalBothCaps = bothCaps;

        // Auto-save runnable that syncs preferences and system ShortcutManager
        Runnable syncShortcuts = () -> {
            if (isRestoringState) return;

            int count = rows.size();
            if (badgeCount != null) {
                badgeCount.setText(String.format(activity.getString(R.string.routine_shortcuts_count_format), count));
            }

            if (bannerLimitReached != null) {
                bannerLimitReached.setVisibility(count >= 4 ? View.VISIBLE : View.GONE);
            }
            updateFabVisibility();

            StringBuilder sigBuilder = new StringBuilder();
            for (int i = 0; i < count; i++) {
                View row = rows.get(i);
                EditText editTitle = row.findViewById(R.id.editShortcutTitle);
                Spinner spinnerSim = row.findViewById(R.id.spinnerSim);
                Spinner spinnerMode = row.findViewById(R.id.spinnerMode);

                String rawName = editTitle != null ? editTitle.getText().toString().trim() : "";
                int simPos = spinnerSim.getSelectedItemPosition();
                int simValue = (simPos >= 0 && simPos < simValueList.size()) ? simValueList.get(simPos) : 1;

                @SuppressWarnings("unchecked")
                List<String> validModes = (List<String>) row.getTag(R.id.spinnerMode);
                String selectedMode = "NONE";
                if (validModes != null && !validModes.isEmpty()) {
                    int modePos = spinnerMode.getSelectedItemPosition();
                    if (modePos < 0 || modePos >= validModes.size()) modePos = 0;
                    selectedMode = validModes.get(modePos);
                }
                sigBuilder.append(i).append(':').append(rawName).append(':').append(selectedMode).append(':').append(simValue).append(';');
            }
            String currentSignature = sigBuilder.toString();
            if (currentSignature.equals(lastSyncedSignature)) {
                return;
            }
            lastSyncedSignature = currentSignature;

            List<ShortcutInfo> dynamicShortcuts = new ArrayList<>();

            for (int i = 0; i < count; i++) {
                int slot = i + 1;
                View row = rows.get(i);
                EditText editTitle = row.findViewById(R.id.editShortcutTitle);
                Spinner spinnerSim = row.findViewById(R.id.spinnerSim);
                Spinner spinnerMode = row.findViewById(R.id.spinnerMode);

                String rawName = editTitle != null ? editTitle.getText().toString().trim() : "";
                String shortcutLabel = rawName.isEmpty() ? ("Routine #" + slot) : rawName;

                int simPos = spinnerSim.getSelectedItemPosition();
                int simValue = (simPos >= 0 && simPos < simValueList.size()) ? simValueList.get(simPos) : 1;

                @SuppressWarnings("unchecked")
                List<String> validModes = (List<String>) row.getTag(R.id.spinnerMode);
                String selectedMode = "NONE";
                if (validModes != null && !validModes.isEmpty()) {
                    int modePos = spinnerMode.getSelectedItemPosition();
                    if (modePos < 0 || modePos >= validModes.size()) modePos = 0;
                    selectedMode = validModes.get(modePos);
                }

                prefs.saveRoutineShortcut(slot, rawName, selectedMode, simValue);

                if (!"NONE".equals(selectedMode) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                    Intent intent = new Intent(activity, ShortcutActionActivity.class);
                    intent.setAction("com.dhangofa.networktoggle.SHORTCUT_ACTION_" + slot);
                    intent.putExtra("mode", selectedMode);
                    intent.putExtra("sim", simValue);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

                    String simLabel = (simPos >= 0 && simPos < simDisplayList.size()) ? simDisplayList.get(simPos) : "SIM";
                    String modeDisplay = spinnerMode.getSelectedItem() != null ? spinnerMode.getSelectedItem().toString() : selectedMode;

                    ShortcutInfo shortcut = new ShortcutInfo.Builder(activity, "routine_slot_" + slot)
                            .setShortLabel(shortcutLabel)
                            .setLongLabel(shortcutLabel + " (" + modeDisplay + ", " + simLabel + ")")
                            .setIcon(Icon.createWithResource(activity, R.drawable.ic_magic_wand))
                            .setIntent(intent)
                            .build();

                    dynamicShortcuts.add(shortcut);
                }
            }

            for (int i = count + 1; i <= 4; i++) {
                prefs.saveRoutineShortcut(i, "", "NONE", 1);
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                try {
                    ShortcutManager sm = activity.getSystemService(ShortcutManager.class);
                    if (sm != null) {
                        sm.setDynamicShortcuts(dynamicShortcuts);
                    }
                } catch (Exception ignored) {}
            }
        };

        Runnable updateIndicesAndStyles = () -> {
            for (int i = 0; i < rows.size(); i++) {
                View row = rows.get(i);
                TextView label = row.findViewById(R.id.slotLabel);
                EditText editTitle = row.findViewById(R.id.editShortcutTitle);

                int slotNum = i + 1;
                if (label != null) {
                    label.setText(activity.getString(R.string.shortcut_hash_format, slotNum));
                    if (slotNum % 2 == 1) {
                        label.setBackgroundResource(R.drawable.shape_badge_slot_purple);
                        label.setTextColor(activity.getColor(R.color.shortcut_badge_purple_text));
                    } else {
                        label.setBackgroundResource(R.drawable.shape_badge_slot_green);
                        label.setTextColor(activity.getColor(R.color.shortcut_badge_green_text));
                    }
                }

                if (editTitle != null) {
                    editTitle.setHint("Routine #" + slotNum);
                }
            }
        };

        interface RowAdder { void add(String savedName, String pendingMode, int savedSimValue); }
        RowAdder addRow = (savedName, pendingMode, savedSimValue) -> {
            if (rows.size() >= 4) return;

            int slotIndex = rows.size() + 1;
            View row = inflater.inflate(R.layout.item_routine_shortcut, container, false);
            TextView slotLabel = row.findViewById(R.id.slotLabel);
            EditText editTitle = row.findViewById(R.id.editShortcutTitle);
            Spinner spinnerSim = row.findViewById(R.id.spinnerSim);
            Spinner spinnerMode = row.findViewById(R.id.spinnerMode);
            View btnTestShortcut = row.findViewById(R.id.btnTestShortcut);
            View btnPinShortcut = row.findViewById(R.id.btnPinShortcut);
            ImageView btnRemove = row.findViewById(R.id.btnRemoveShortcut);

            // Set Title
            if (savedName != null && !savedName.isEmpty()) {
                editTitle.setText(savedName);
            } else {
                editTitle.setText("");
            }

            // SIM Adapter
            ArrayAdapter<String> simAdapter = new ArrayAdapter<>(activity, android.R.layout.simple_spinner_dropdown_item, simDisplayList);
            spinnerSim.setAdapter(simAdapter);

            int initialSimPos = simValueList.indexOf(savedSimValue);
            if (initialSimPos < 0) initialSimPos = 0;
            spinnerSim.setSelection(initialSimPos);

            Runnable populateModesForSim = () -> {
                int simPos = spinnerSim.getSelectedItemPosition();
                int simValue = (simPos >= 0 && simPos < simValueList.size()) ? simValueList.get(simPos) : 1;
                AppPreferences.NetworkCapabilities caps;
                if (simValue == 1) caps = finalSim1Caps;
                else if (simValue == 2) caps = finalSim2Caps;
                else caps = finalBothCaps;

                List<String> validModes = new ArrayList<>();
                List<String> validDisplays = new ArrayList<>();

                if (caps.supports5g) {
                    validModes.add("5G_ONLY"); validDisplays.add(activity.getString(R.string.text_5g_only));
                    validModes.add("PREF_5G"); validDisplays.add(activity.getString(R.string.pref_5g));
                }
                if (caps.supports4g) {
                    validModes.add("4G_ONLY"); validDisplays.add(activity.getString(R.string.text_4g_only));
                    validModes.add("PREF_4G"); validDisplays.add(activity.getString(R.string.pref_4g));
                }
                if (caps.supports3g) {
                    validModes.add("PREF_3G"); validDisplays.add(activity.getString(R.string.pref_3g));
                }
                if (caps.supports2g) {
                    validModes.add("2G_ONLY"); validDisplays.add(activity.getString(R.string.text_2g_only));
                }

                if (validModes.isEmpty()) {
                    validModes.add("PREF_4G");
                    validDisplays.add(activity.getString(R.string.pref_4g));
                }

                row.setTag(R.id.spinnerMode, validModes);

                ArrayAdapter<String> modeAdapter = new ArrayAdapter<>(activity, android.R.layout.simple_spinner_dropdown_item, validDisplays);
                spinnerMode.setAdapter(modeAdapter);

                String modeToRestore = (String) row.getTag(R.id.btnRemoveShortcut);
                if (modeToRestore != null) {
                    int idx = validModes.indexOf(modeToRestore);
                    if (idx >= 0) {
                        spinnerMode.setSelection(idx);
                    }
                    row.setTag(R.id.btnRemoveShortcut, null);
                }
            };

            if (pendingMode != null) {
                row.setTag(R.id.btnRemoveShortcut, pendingMode);
            }

            // Immediately populate modes synchronously so validModes is never null
            populateModesForSim.run();
            spinnerSim.setTag(R.id.spinnerSim, initialSimPos);
            spinnerMode.setTag(R.id.spinnerMode, spinnerMode.getSelectedItemPosition());

            spinnerSim.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    if (isRestoringState) return;
                    Object lastSim = spinnerSim.getTag(R.id.spinnerSim);
                    if (lastSim instanceof Integer && (Integer) lastSim == position) return;
                    spinnerSim.setTag(R.id.spinnerSim, position);
                    populateModesForSim.run();
                    syncShortcuts.run();
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {}
            });

            spinnerMode.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    if (isRestoringState) return;
                    Object lastMode = spinnerMode.getTag(R.id.spinnerMode);
                    if (lastMode instanceof Integer && (Integer) lastMode == position) return;
                    spinnerMode.setTag(R.id.spinnerMode, position);
                    syncShortcuts.run();
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {}
            });

            // Auto-save title on edit
            editTitle.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {}

                @Override
                public void afterTextChanged(Editable s) {
                    if (isRestoringState) return;
                    syncShortcuts.run();
                }
            });

            // Initial state for Test Shortcut button based on authorization
            btnTestShortcut.setAlpha(isAuthorized ? 1.0f : 0.4f);

            // Test Shortcut Action
            btnTestShortcut.setOnClickListener(v -> {
                if (!isAuthorized) {
                    Toast.makeText(activity, R.string.toast_auth_required_shortcut, Toast.LENGTH_SHORT).show();
                    return;
                }
                int simPos = spinnerSim.getSelectedItemPosition();
                int simValue = (simPos >= 0 && simPos < simValueList.size()) ? simValueList.get(simPos) : 1;

                @SuppressWarnings("unchecked")
                List<String> validModes = (List<String>) row.getTag(R.id.spinnerMode);
                if (validModes == null || validModes.isEmpty()) return;

                int modePos = spinnerMode.getSelectedItemPosition();
                if (modePos < 0 || modePos >= validModes.size()) modePos = 0;
                String selectedMode = validModes.get(modePos);

                TargetSim targetSim = TargetSim.AUTO;
                if (simValue == 1) targetSim = TargetSim.SIM_1;
                else if (simValue == 2) targetSim = TargetSim.SIM_2;
                else if (simValue == 3) targetSim = TargetSim.BOTH;

                NetworkMode networkMode = parseNetworkMode(selectedMode);
                if (networkMode != NetworkMode.UNKNOWN) {
                    final TargetSim finalTargetSim = targetSim;
                    AppExecutors.executeTelephony(() -> {
                        AutomationRequest request = new AutomationRequest(networkMode, finalTargetSim, false, "ShortcutTest");
                        AutomationExecutor.execute(activity.getApplicationContext(), request);
                    });
                }

                String displayMode = selectedMode.replace("_ONLY", " Only").replace("PREF_", "Pref ");
                String simName = (simPos >= 0 && simPos < simDisplayList.size()) ? simDisplayList.get(simPos) : "SIM 1";
                Toast.makeText(activity, activity.getString(R.string.toast_applying) + " " + displayMode + " (" + simName + ")...", Toast.LENGTH_SHORT).show();
            });

            // Pin to Home Screen Action
            btnPinShortcut.setOnClickListener(v -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    ShortcutManager shortcutManager = activity.getSystemService(ShortcutManager.class);
                    if (shortcutManager != null && shortcutManager.isRequestPinShortcutSupported()) {
                        int slot = rows.indexOf(row) + 1;
                        int simPos = spinnerSim.getSelectedItemPosition();
                        int simValue = (simPos >= 0 && simPos < simValueList.size()) ? simValueList.get(simPos) : 1;

                        @SuppressWarnings("unchecked")
                        List<String> validModes = (List<String>) row.getTag(R.id.spinnerMode);
                        String selectedMode = (validModes != null && !validModes.isEmpty())
                                ? validModes.get(Math.max(0, spinnerMode.getSelectedItemPosition()))
                                : "5G_ONLY";

                        String title = editTitle.getText().toString().trim();
                        if (title.isEmpty()) title = "Routine #" + slot;

                        Intent intent = new Intent(activity, ShortcutActionActivity.class);
                        intent.setAction("com.dhangofa.networktoggle.SHORTCUT_ACTION_" + slot);
                        intent.putExtra("mode", selectedMode);
                        intent.putExtra("sim", simValue);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

                        ShortcutInfo pinShortcut = new ShortcutInfo.Builder(activity, "routine_slot_" + slot)
                                .setShortLabel(title)
                                .setIcon(Icon.createWithResource(activity, R.drawable.ic_magic_wand))
                                .setIntent(intent)
                                .build();

                        shortcutManager.requestPinShortcut(pinShortcut, null);
                        Toast.makeText(activity, activity.getString(R.string.toast_shortcut_pinned), Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(activity, activity.getString(R.string.toast_pin_not_supported), Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(activity, activity.getString(R.string.toast_pin_not_supported), Toast.LENGTH_SHORT).show();
                }
            });

            // Remove Button
            btnRemove.setOnClickListener(v -> {
                container.removeView(row);
                rows.remove(row);
                updateIndicesAndStyles.run();
                syncShortcuts.run();
            });

            if (pendingMode != null) {
                row.setTag(R.id.btnRemoveShortcut, pendingMode);
            }

            if (container instanceof GridLayout) {
                GridLayout.LayoutParams glp = new GridLayout.LayoutParams();
                glp.width = 0;
                glp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                glp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
                glp.rowSpec = GridLayout.spec(GridLayout.UNDEFINED);
                int margin = (int) (6 * activity.getResources().getDisplayMetrics().density);
                glp.setMargins(margin, margin, margin, margin);
                row.setLayoutParams(glp);
            }

            rows.add(row);
            container.addView(row);
            updateIndicesAndStyles.run();
        };

        btnAdd.setOnClickListener(v -> {
            addRow.add("", null, 1);
            syncShortcuts.run();
        });

        // Load existing saved shortcuts
        isRestoringState = true;
        int loadedCount = 0;
        for (int i = 1; i <= 4; i++) {
            String savedMode = prefs.getRoutineShortcutMode(i);
            if (!"NONE".equals(savedMode)) {
                String savedName = prefs.getRoutineShortcutName(i);
                if (savedName == null || savedName.trim().isEmpty()) {
                    if (i == 1) savedName = "Home Ultra 5G";
                    else if (i == 2) savedName = "Office Battery Saver";
                }
                int savedSim = prefs.getRoutineShortcutSim(i);
                addRow.add(savedName, savedMode, savedSim);
                loadedCount++;
            }
        }

        // If newly installed and empty, pre-populate 2 helpful default shortcuts on very first app run
        if (loadedCount == 0) {
            addRow.add("Home Ultra 5G", "5G_ONLY", 1);
            addRow.add("Office Battery Saver", "4G_ONLY", 1);
            prefs.setRoutineShortcutsInitialized(true);
        } else {
            prefs.setRoutineShortcutsInitialized(true);
        }

        isRestoringState = false;
        updateIndicesAndStyles.run();
        syncShortcuts.run();
    }

    public void setKeyboardOpen(boolean open) {
        this.isKeyboardOpen = open;
        if (open) {
            this.updateFabVisibility();
        }
    }

    public boolean isShortcutTabActive() {
        boolean isAutoTabVisible;
        if (activity instanceof MainActivity) {
            isAutoTabVisible = ((MainActivity) activity).getCurrentTabIndex() == 1;
        } else {
            View tabAutomation = activity.findViewById(R.id.tabAutomation);
            isAutoTabVisible = tabAutomation != null && tabAutomation.getVisibility() == View.VISIBLE;
        }
        View pageShortcuts = activity.findViewById(R.id.pageShortcuts);
        return isAutoTabVisible && pageShortcuts != null && pageShortcuts.getVisibility() == View.VISIBLE;
    }

    public void updateFabVisibility() {
        View btnAdd = activity.findViewById(R.id.btnAddShortcut);
        if (btnAdd == null) return;
        View bottomNavPill = activity.findViewById(R.id.bottomNavPill);
        boolean isLandscape = activity.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        boolean navReady = isLandscape || (bottomNavPill != null && bottomNavPill.getVisibility() == View.VISIBLE);

        if (!isKeyboardOpen && navReady && rows.size() < 4 && isShortcutTabActive()) {
            btnAdd.setVisibility(View.VISIBLE);
            btnAdd.setAlpha(1.0f);
            btnAdd.setScaleX(1.0f);
            btnAdd.setScaleY(1.0f);
        } else {
            btnAdd.setVisibility(View.GONE);
        }
    }

    public int getShortcutCount() {
        return rows.size();
    }

    private static NetworkMode parseNetworkMode(String modeString) {
        if (modeString == null) return NetworkMode.UNKNOWN;
        modeString = modeString.toUpperCase().trim();
        switch (modeString) {
            case "5G_ONLY":
            case "5G":
                return NetworkMode.FIVE_G_ONLY;
            case "4G_ONLY":
            case "4G":
            case "LTE":
                return NetworkMode.FOUR_G_ONLY;
            case "PREF_5G":
            case "PREFERRED_5G":
                return NetworkMode.PREFERRED_5G;
            case "PREF_4G":
            case "PREFERRED_4G":
                return NetworkMode.PREFERRED_4G;
            case "PREF_3G":
            case "PREFERRED_3G":
                return NetworkMode.PREFERRED_3G;
            case "2G_ONLY":
            case "2G":
                return NetworkMode.TWO_G_ONLY;
            default:
                return NetworkMode.UNKNOWN;
        }
    }
}

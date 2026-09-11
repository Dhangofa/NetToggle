package com.dhangofa.networktoggle.ui;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.text.Editable;
import android.text.Html;
import android.text.Spanned;
import android.text.TextWatcher;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.dhangofa.networktoggle.MainActivity;
import com.dhangofa.networktoggle.R;
import com.dhangofa.networktoggle.config.AppPreferences;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class BroadcastTabHelper {

    private static String selectedMode = "5G";
    private static int selectedSim = 1;

    private final Activity activity;
    private final AppPreferences prefs;

    private Switch switchExt;
    private TextView badgeStatus;
    private View btnTestBroadcast;
    private boolean isAuthorized = false;
    private boolean updatingUi = false;
    private Runnable refreshCapabilitiesRunnable;

    public BroadcastTabHelper(Activity activity, AppPreferences prefs) {
        this.activity = activity;
        this.prefs = prefs;
    }

    public static BroadcastTabHelper setupTab(Activity activity, AppPreferences prefs) {
        BroadcastTabHelper helper = new BroadcastTabHelper(activity, prefs);
        helper.setup();
        return helper;
    }

    public void setAuthorized(boolean authorized) {
        this.isAuthorized = authorized;
        if (!authorized && switchExt != null && switchExt.isChecked()) {
            updatingUi = true;
            switchExt.setChecked(false);
            prefs.setExternalAutomationEnabled(false);
            updatingUi = false;
        }
        updateStatusBadge();
        updateTestButtonState();
        refreshCapabilities();
    }

    public void refreshCapabilities() {
        if (refreshCapabilitiesRunnable != null) {
            activity.runOnUiThread(refreshCapabilitiesRunnable);
        }
    }

    private List<Integer> getAvailableSimSlots() {
        List<Integer> slots = new ArrayList<>();
        if (activity.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            SubscriptionManager sm = (SubscriptionManager) activity.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
            if (sm != null) {
                try {
                    List<SubscriptionInfo> list = sm.getActiveSubscriptionInfoList();
                    if (list != null) {
                        for (SubscriptionInfo info : list) {
                            int slotIndex = info.getSimSlotIndex();
                            if (slotIndex >= 0) {
                                int simNum = slotIndex + 1;
                                if (!slots.contains(simNum)) {
                                    slots.add(simNum);
                                }
                            }
                        }
                    }
                } catch (SecurityException ignored) {
                }
            }
        }
        if (slots.isEmpty()) {
            slots.add(1);
        }
        return slots;
    }

    private void updateStatusBadge() {
        if (badgeStatus != null && switchExt != null) {
            boolean enabled = switchExt.isChecked();
            if (enabled) {
                badgeStatus.setText(R.string.status_ready);
                badgeStatus.setTextColor(activity.getColor(R.color.status_success_text));
                badgeStatus.setBackgroundResource(R.drawable.shape_status_badge_ready);
            } else {
                badgeStatus.setText(R.string.status_disabled);
                badgeStatus.setTextColor(activity.getColor(R.color.status_disabled_text));
                badgeStatus.setBackgroundResource(R.drawable.shape_status_badge_disabled);
            }
        }
    }

    private void updateTestButtonState() {
        if (btnTestBroadcast != null) {
            boolean active = isAuthorized && prefs.isExternalAutomationEnabled();
            btnTestBroadcast.setAlpha(active ? 1.0f : 0.4f);
        }
    }

    public void setup() {
        if (activity instanceof MainActivity) {
            this.isAuthorized = ((MainActivity) activity).isExecutionAuthorized();
        }

        switchExt = activity.findViewById(R.id.switchExternalAutomation);
        badgeStatus = activity.findViewById(R.id.badgeAutomationStatus);
        btnTestBroadcast = activity.findViewById(R.id.btnTestBroadcast);
        View btnCopyIntent = activity.findViewById(R.id.btnCopyIntent);
        View blockShellCommand = activity.findViewById(R.id.blockShellCommand);
        TextView textShellCommand = activity.findViewById(R.id.textShellCommand);
        EditText editToken = activity.findViewById(R.id.editAutomationToken);
        TextView btnGen = activity.findViewById(R.id.btnGenerateToken);

        // Map mode chips
        Map<String, TextView> modeChips = new HashMap<>();
        modeChips.put("5G", activity.findViewById(R.id.chipMode5G));
        modeChips.put("4G", activity.findViewById(R.id.chipMode4G));
        modeChips.put("PREF_5G", activity.findViewById(R.id.chipModePref5G));
        modeChips.put("PREF_4G", activity.findViewById(R.id.chipModePref4G));
        modeChips.put("PREF_3G", activity.findViewById(R.id.chipModePref3G));
        modeChips.put("2G", activity.findViewById(R.id.chipMode2G));

        // Map sim chips
        Map<Integer, TextView> simChips = new HashMap<>();
        simChips.put(1, activity.findViewById(R.id.chipSim1));
        simChips.put(2, activity.findViewById(R.id.chipSim2));
        simChips.put(3, activity.findViewById(R.id.chipSimBoth));

        if (switchExt != null) {
            updatingUi = true;
            boolean isChecked = isAuthorized && prefs.isExternalAutomationEnabled();
            if (!isAuthorized && prefs.isExternalAutomationEnabled()) {
                prefs.setExternalAutomationEnabled(false);
            }
            switchExt.setChecked(isChecked);
            updatingUi = false;
            updateStatusBadge();
            updateTestButtonState();

            switchExt.setOnTouchListener((v, event) -> {
                if (!isAuthorized) {
                    if (event.getAction() == MotionEvent.ACTION_DOWN) {
                        Toast.makeText(activity, R.string.toast_auth_required_automation, Toast.LENGTH_SHORT).show();
                    }
                    return true;
                }
                return false;
            });

            switchExt.setOnClickListener(v -> {
                if (!isAuthorized) {
                    updatingUi = true;
                    switchExt.setChecked(false);
                    updatingUi = false;
                    Toast.makeText(activity, R.string.toast_auth_required_automation, Toast.LENGTH_SHORT).show();
                }
            });

            switchExt.setOnCheckedChangeListener((buttonView, isCheckedVal) -> {
                if (updatingUi) return;
                if (isCheckedVal && !isAuthorized) {
                    updatingUi = true;
                    buttonView.post(() -> switchExt.setChecked(false));
                    updatingUi = false;
                    Toast.makeText(activity, R.string.toast_auth_required_automation, Toast.LENGTH_SHORT).show();
                    return;
                }
                prefs.setExternalAutomationEnabled(isCheckedVal);
                updateStatusBadge();
                updateTestButtonState();
            });
        }

        View rowToggle = activity.findViewById(R.id.rowExternalAutomationToggle);
        if (rowToggle != null && switchExt != null) {
            rowToggle.setOnClickListener(v -> {
                if (!isAuthorized) {
                    Toast.makeText(activity, R.string.toast_auth_required_automation, Toast.LENGTH_SHORT).show();
                } else {
                    switchExt.setChecked(!switchExt.isChecked());
                }
            });
        }

        // Token input
        if (editToken != null) {
            editToken.setText(prefs.getAutomationToken());
            editToken.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override public void afterTextChanged(Editable s) {
                    prefs.setAutomationToken(s.toString());
                    updateShellPreview(activity, prefs, textShellCommand);
                }
            });
        }

        if (btnGen != null) {
            btnGen.setOnClickListener(v -> {
                String randomToken = UUID.randomUUID().toString().substring(0, 8);
                if (editToken != null) {
                    editToken.setText(randomToken);
                }
                prefs.setAutomationToken(randomToken);
                updateShellPreview(activity, prefs, textShellCommand);
            });
        }

        // SIM and Mode Capability-Aware Logic
        Runnable updateSimChips = () -> {
            for (Map.Entry<Integer, TextView> entry : simChips.entrySet()) {
                TextView chip = entry.getValue();
                if (chip == null) continue;
                boolean isSelected = entry.getKey() == selectedSim;
                if (isSelected) {
                    chip.setBackgroundResource(R.drawable.shape_chip_selected_green);
                    chip.setTextColor(activity.getColor(R.color.status_success_text));
                    chip.setTypeface(null, Typeface.BOLD);
                } else {
                    chip.setBackgroundResource(R.drawable.shape_chip_unselected);
                    chip.setTextColor(activity.getColor(R.color.text_primary));
                    chip.setTypeface(null, Typeface.NORMAL);
                }
            }
        };

        Runnable updateModeChips = () -> {
            for (Map.Entry<String, TextView> entry : modeChips.entrySet()) {
                TextView chip = entry.getValue();
                if (chip == null) continue;
                boolean isSelected = entry.getKey().equals(selectedMode);
                if (isSelected) {
                    chip.setBackgroundResource(R.drawable.shape_chip_selected_orange);
                    chip.setTextColor(activity.getColor(R.color.cycle_accent));
                    chip.setTypeface(null, Typeface.BOLD);
                } else {
                    chip.setBackgroundResource(R.drawable.shape_chip_unselected);
                    chip.setTextColor(activity.getColor(R.color.text_primary));
                    chip.setTypeface(null, Typeface.NORMAL);
                }
            }
        };

        this.refreshCapabilitiesRunnable = () -> {
            List<Integer> availableSims = getAvailableSimSlots();
            boolean multiSim = availableSims.size() > 1;

            TextView chipSim1 = simChips.get(1);
            TextView chipSim2 = simChips.get(2);
            TextView chipSimBoth = simChips.get(3);

            if (chipSim1 != null) {
                chipSim1.setVisibility(availableSims.contains(1) ? View.VISIBLE : View.GONE);
            }
            if (chipSim2 != null) {
                chipSim2.setVisibility(availableSims.contains(2) ? View.VISIBLE : View.GONE);
            }
            if (chipSimBoth != null) {
                chipSimBoth.setVisibility(multiSim ? View.VISIBLE : View.GONE);
            }

            // Ensure selected SIM is valid
            boolean simValid = false;
            if (selectedSim == 1 && availableSims.contains(1)) simValid = true;
            else if (selectedSim == 2 && availableSims.contains(2)) simValid = true;
            else if (selectedSim == 3 && multiSim) simValid = true;

            if (!simValid) {
                selectedSim = availableSims.get(0);
            }
            updateSimChips.run();

            // Determine capabilities for selected SIM
            AppPreferences.NetworkCapabilities deviceCaps = prefs.getDeviceCapabilities();
            if (deviceCaps == null) deviceCaps = AppPreferences.NetworkCapabilities.assumeAll();
            AppPreferences.NetworkCapabilities sim1Caps = prefs.getSlotCapabilities(0);
            if (sim1Caps == null) sim1Caps = deviceCaps;
            AppPreferences.NetworkCapabilities sim2Caps = prefs.getSlotCapabilities(1);
            if (sim2Caps == null) sim2Caps = deviceCaps;

            AppPreferences.NetworkCapabilities caps;
            if (selectedSim == 1) {
                caps = sim1Caps;
            } else if (selectedSim == 2) {
                caps = sim2Caps;
            } else {
                caps = new AppPreferences.NetworkCapabilities(
                    sim1Caps.supports2g && sim2Caps.supports2g,
                    sim1Caps.supports3g && sim2Caps.supports3g,
                    sim1Caps.supports4g && sim2Caps.supports4g,
                    sim1Caps.supports5g && sim2Caps.supports5g
                );
            }

            // Update mode chip visibilities
            TextView chip5G = modeChips.get("5G");
            TextView chip4G = modeChips.get("4G");
            TextView chipPref5G = modeChips.get("PREF_5G");
            TextView chipPref4G = modeChips.get("PREF_4G");
            TextView chipPref3G = modeChips.get("PREF_3G");
            TextView chip2G = modeChips.get("2G");

            if (chip5G != null) chip5G.setVisibility(caps.supports5g ? View.VISIBLE : View.GONE);
            if (chip4G != null) chip4G.setVisibility(caps.supports4g ? View.VISIBLE : View.GONE);
            if (chipPref5G != null) chipPref5G.setVisibility(caps.supports5g ? View.VISIBLE : View.GONE);
            if (chipPref4G != null) chipPref4G.setVisibility(caps.supports4g ? View.VISIBLE : View.GONE);
            if (chipPref3G != null) chipPref3G.setVisibility(caps.supports3g ? View.VISIBLE : View.GONE);
            if (chip2G != null) chip2G.setVisibility(caps.supports2g ? View.VISIBLE : View.GONE);

            // Ensure selected mode is valid for the capabilities
            boolean modeValid = false;
            if ("5G".equals(selectedMode) && caps.supports5g) modeValid = true;
            else if ("4G".equals(selectedMode) && caps.supports4g) modeValid = true;
            else if ("PREF_5G".equals(selectedMode) && caps.supports5g) modeValid = true;
            else if ("PREF_4G".equals(selectedMode) && caps.supports4g) modeValid = true;
            else if ("PREF_3G".equals(selectedMode) && caps.supports3g) modeValid = true;
            else if ("2G".equals(selectedMode) && caps.supports2g) modeValid = true;

            if (!modeValid) {
                if (caps.supports5g) selectedMode = "5G";
                else if (caps.supports4g) selectedMode = "4G";
                else if (caps.supports3g) selectedMode = "PREF_3G";
                else if (caps.supports2g) selectedMode = "2G";
                else selectedMode = "4G";
            }
            updateModeChips.run();
            updateShellPreview(activity, prefs, textShellCommand);
        };

        for (Map.Entry<String, TextView> entry : modeChips.entrySet()) {
            TextView chip = entry.getValue();
            if (chip != null) {
                chip.setOnClickListener(v -> {
                    selectedMode = entry.getKey();
                    updateModeChips.run();
                    updateShellPreview(activity, prefs, textShellCommand);
                });
            }
        }

        for (Map.Entry<Integer, TextView> entry : simChips.entrySet()) {
            TextView chip = entry.getValue();
            if (chip != null) {
                chip.setOnClickListener(v -> {
                    selectedSim = entry.getKey();
                    refreshCapabilitiesRunnable.run();
                });
            }
        }

        refreshCapabilitiesRunnable.run();

        // Shell command initial update
        updateShellPreview(activity, prefs, textShellCommand);

        // Copy shell command on tap
        if (blockShellCommand != null) {
            blockShellCommand.setOnClickListener(v -> {
                String cmd = getShellCommand(prefs);
                copyToClipboard(activity, "ADB Command", cmd);
                Toast.makeText(activity, "ADB command copied", Toast.LENGTH_SHORT).show();
            });
        }

        // Copy Specs button
        if (btnCopyIntent != null) {
            btnCopyIntent.setOnClickListener(v -> {
                String token = prefs.getAutomationToken();
                if (token == null) token = "";
                String specs = "Action Type: Send Intent\n"
                        + "Target: Broadcast\n"
                        + "Action: com.dhangofa.networktoggle.SET_MODE\n"
                        + "Package: com.dhangofa.networktoggle\n"
                        + "Class: com.dhangofa.networktoggle.AutomationReceiver\n"
                        + "Extra 1: mode: " + selectedMode + "\n"
                        + "Extra 2: sim: " + selectedSim + "\n"
                        + "Extra 3: token: " + token;

                copyToClipboard(activity, "Broadcast Specifications", specs);
                Toast.makeText(activity, "Broadcast specifications copied", Toast.LENGTH_SHORT).show();
            });
        }

        // Test Broadcast button
        if (btnTestBroadcast != null) {
            updateTestButtonState();
            btnTestBroadcast.setOnClickListener(v -> {
                if (!prefs.isExternalAutomationEnabled()) {
                    Toast.makeText(activity, R.string.toast_ext_automation_disabled, Toast.LENGTH_SHORT).show();
                    return;
                }
                if (!isAuthorized) {
                    Toast.makeText(activity, R.string.toast_auth_required_automation, Toast.LENGTH_SHORT).show();
                    return;
                }

                Intent intent = new Intent("com.dhangofa.networktoggle.SET_MODE");
                intent.setPackage(activity.getPackageName());
                intent.putExtra("mode", selectedMode);
                intent.putExtra("sim", selectedSim);
                String token = prefs.getAutomationToken();
                if (token != null && !token.isEmpty()) {
                    intent.putExtra("token", token);
                }
                activity.sendBroadcast(intent);

                String simLabel = (selectedSim == 3) ? "Both" : "SIM " + selectedSim;
                Toast.makeText(activity, "Broadcast sent: " + selectedMode + " (" + simLabel + ")", Toast.LENGTH_SHORT).show();
            });
        }
    }

    private static String getShellCommand(AppPreferences prefs) {
        String token = prefs.getAutomationToken();
        if (token == null) token = "";
        return "am broadcast -a com.dhangofa.networktoggle.SET_MODE -n com.dhangofa.networktoggle/.AutomationReceiver --es mode " + selectedMode + " --ei sim " + selectedSim + " --es token " + token;
    }

    private static Spanned getShellCommandHtml(AppPreferences prefs) {
        String token = prefs.getAutomationToken();
        if (token == null) token = "";
        String html = "<font color='#FF8A65'>am broadcast</font> "
                + "<font color='#CFD8DC'>-a</font> "
                + "<font color='#81C784'>com.dhangofa.networktoggle.SET_MODE</font> "
                + "<font color='#CFD8DC'>-n</font> "
                + "<font color='#81C784'>com.dhangofa.networktoggle/.AutomationReceiver</font> "
                + "<font color='#CFD8DC'>--es mode</font> "
                + "<font color='#FF7043'>" + selectedMode + "</font> "
                + "<font color='#CFD8DC'>--ei sim</font> "
                + "<font color='#81C784'>" + selectedSim + "</font> "
                + "<font color='#CFD8DC'>--es token</font> "
                + "<font color='#64B5F6'>" + token + "</font>";
        return Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY);
    }

    private static void updateShellPreview(Context context, AppPreferences prefs, TextView textShellCommand) {
        if (textShellCommand != null) {
            textShellCommand.setText(getShellCommandHtml(prefs));
        }
    }

    private static void copyToClipboard(Context context, String label, String text) {
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            ClipData clip = ClipData.newPlainText(label, text);
            clipboard.setPrimaryClip(clip);
        }
    }
}

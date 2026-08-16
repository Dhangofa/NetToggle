package com.dhangofa.networktoggle.telephony;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;

import com.dhangofa.networktoggle.command.CommandExecutor;
import com.dhangofa.networktoggle.command.CommandExecutorFactory;
import com.dhangofa.networktoggle.config.AppPreferences;
import com.dhangofa.networktoggle.model.CommandResult;
import com.dhangofa.networktoggle.model.ExecutionMode;
import com.dhangofa.networktoggle.model.TargetSim;

public final class SimResolver {
    public static final int INVALID_SLOT_INDEX = -1;
    public static final int INVALID_SUB_ID = -1;

    private final Context context;
    private final AppPreferences appPreferences;

    // Data class to hold all extracted variables in one place
    public static class SimInfo {
        public final int subId;
        public final int slotIndex;
        public final String carrierName;

        public SimInfo(int subId, int slotIndex, String carrierName) {
            this.subId = subId;
            this.slotIndex = slotIndex;
            this.carrierName = carrierName;
        }
    }

    public SimResolver(Context context, AppPreferences appPreferences) {
        this.context = context.getApplicationContext();
        this.appPreferences = appPreferences;
    }

    /**
     * Resolves IDs using Native APIs first.
     * Silently falls back to Shell commands if permission is denied or device is too old.
     */
    public SimInfo resolveTargetSimInfo(ExecutionMode executionMode) {
        TargetSim targetSim = appPreferences.getTargetSim();
        
        int targetSubId = INVALID_SUB_ID;
        int targetSlotIndex = INVALID_SLOT_INDEX;
        String carrierName = "";

        // 1. Safe Native APIs (No permission required)
        if (targetSim.isAuto()) {
            targetSubId = SubscriptionManager.getDefaultDataSubscriptionId();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    int nativeSlot = SubscriptionManager.getSlotIndex(targetSubId);
                    if (isValidSlotIndex(nativeSlot)) targetSlotIndex = nativeSlot;
                } catch (Exception ignored) {}
            }
        } else {
            targetSlotIndex = targetSim.getManualSlotIndex();
        }

        // 2. Protected Native APIs (Requires READ_PHONE_STATE)
        boolean hasPermission = context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED;
        
        if (hasPermission) {
            SubscriptionManager sm = context.getSystemService(SubscriptionManager.class);
            if (sm != null) {
                try {
                    if (isValidSubId(targetSubId)) {
                        SubscriptionInfo info = sm.getActiveSubscriptionInfo(targetSubId);
                        if (info != null) {
                            if (info.getCarrierName() != null) carrierName = info.getCarrierName().toString();
                            if (!isValidSlotIndex(targetSlotIndex)) targetSlotIndex = info.getSimSlotIndex();
                        }
                    } else if (isValidSlotIndex(targetSlotIndex)) {
                        for (SubscriptionInfo info : sm.getActiveSubscriptionInfoList()) {
                            if (info.getSimSlotIndex() == targetSlotIndex) {
                                targetSubId = info.getSubscriptionId();
                                if (info.getCarrierName() != null) carrierName = info.getCarrierName().toString();
                                break;
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }
        }

        // 3. Shell Fallback (If missing permission, or older Android OS failed to map)
        if (!isValidSubId(targetSubId) || !isValidSlotIndex(targetSlotIndex) || carrierName.isEmpty()) {
            String command = "dumpsys isub | grep -E \"\\{id=[0-9]+ .*simSlotIndex=\"";
            CommandExecutor executor = CommandExecutorFactory.forMode(executionMode);
            
            if (executor != null) {
                CommandResult result = executor.execute(command);
                if (result.isSuccess() && !result.getStdout().trim().isEmpty()) {
                    String[] lines = result.getStdout().split("\\n");
                    for (String line : lines) {
                        Integer dumpSubId = ShellValueParser.extractIntByKey(line, "id=");
                        Integer dumpSlotIndex = ShellValueParser.extractIntByKey(line, "simSlotIndex=");
                        String dumpCarrier = ShellValueParser.extractStringByKey(line, "carrierName=", " nameSource=");

                        if (dumpSubId != null && dumpSlotIndex != null) {
                            if (targetSim.isAuto() && dumpSubId == targetSubId) {
                                if (!isValidSlotIndex(targetSlotIndex)) targetSlotIndex = dumpSlotIndex;
                                if (carrierName.isEmpty()) carrierName = dumpCarrier;
                                break;
                            } else if (!targetSim.isAuto() && dumpSlotIndex == targetSlotIndex) {
                                if (!isValidSubId(targetSubId)) targetSubId = dumpSubId;
                                if (carrierName.isEmpty()) carrierName = dumpCarrier;
                                break;
                            }
                        }
                    }
                }
            }
        }

        // 4. Final Validation
        if (isValidSubId(targetSubId) && isValidSlotIndex(targetSlotIndex)) {
            if (targetSim.isAuto()) appPreferences.setAutoSimError(false);
            return new SimInfo(targetSubId, targetSlotIndex, carrierName);
        }

        if (targetSim.isAuto()) appPreferences.setAutoSimError(true);
        return null;
    }

    // Keeping backwards compatibility for existing app logic
    public int resolveTargetSlotIndex(ExecutionMode executionMode) {
        SimInfo info = resolveTargetSimInfo(executionMode);
        return info != null ? info.slotIndex : INVALID_SLOT_INDEX;
    }

    public int resolveTargetSubId(ExecutionMode executionMode) {
        SimInfo info = resolveTargetSimInfo(executionMode);
        return info != null ? info.subId : INVALID_SUB_ID;
    }

    public boolean isValidSlotIndex(int slotIndex) {
        return slotIndex == 0 || slotIndex == 1;
    }

    public boolean isValidSubId(int subId) {
        return subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID && subId != INVALID_SUB_ID;
    }
}

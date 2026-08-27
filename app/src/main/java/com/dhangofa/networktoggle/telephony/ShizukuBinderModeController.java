package com.dhangofa.networktoggle.telephony;

/**
 * Changes the network mode using the Shizuku Binder.
 * It creates a direct binder call to the Android telephony service (ITelephony) to bypass
 * the need for root completely.
 */

import android.annotation.SuppressLint;
import android.os.Build;
import android.os.IBinder;

import com.dhangofa.networktoggle.model.CommandResult;
import com.dhangofa.networktoggle.model.ExecutionMode;
import com.dhangofa.networktoggle.model.NetworkMode;

import java.lang.reflect.Method;

import rikka.shizuku.ShizukuBinderWrapper;
import rikka.shizuku.SystemServiceHelper;

final class ShizukuBinderModeController {
    private final SimResolver simResolver;

    ShizukuBinderModeController(SimResolver simResolver) {
        this.simResolver = simResolver;
    }

    CommandResult apply(NetworkMode networkMode, ExecutionMode executionMode) {
        if (executionMode != ExecutionMode.SHIZUKU) {
            return CommandResult.failed("", "Invalid execution mode for Shizuku binder.");
        }

        if (networkMode == null || networkMode == NetworkMode.UNKNOWN) {
            return CommandResult.failed("", "Invalid network mode selected.");
        }

        SimResolver.SimInfo simInfo = simResolver.resolveTargetSimInfo(executionMode);
        if (simInfo == null || !simResolver.isValidSlotIndex(simInfo.slotIndex) || !simResolver.isValidSubId(simInfo.subId)) {
            return CommandResult.failed("", "Unable to resolve the target SIM.");
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                org.lsposed.hiddenapibypass.HiddenApiBypass.addHiddenApiExemptions("Lcom/android/internal/telephony/");
            }

            IBinder binder = new ShizukuBinderWrapper(SystemServiceHelper.getSystemService("phone"));
            if (binder == null) {
                return CommandResult.failed("", "Failed to get Shizuku 'phone' service binder.");
            }

            @SuppressLint("PrivateApi")
            Class<?> iTelephonyStubClass = Class.forName("com.android.internal.telephony.ITelephony$Stub");
            Method asInterfaceMethod = iTelephonyStubClass.getDeclaredMethod("asInterface", IBinder.class);
            Object iTelephony = asInterfaceMethod.invoke(null, binder);

            if (iTelephony == null) {
                return CommandResult.failed("", "Failed to get ITelephony interface via Shizuku.");
            }

            Class<?> iTelephonyClass = Class.forName("com.android.internal.telephony.ITelephony");

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Android 12+ (API 31+)
                if (networkMode.getBinaryMask() == null) {
                    return CommandResult.failed("", "Invalid network mode mask for A12+.");
                }
                long bitmask = Long.parseLong(networkMode.getBinaryMask(), 2);
                
                Method setAllowedMethod = null;
                for (Method m : iTelephonyClass.getDeclaredMethods()) {
                    if (m.getName().equals("setAllowedNetworkTypesForReason")) {
                        setAllowedMethod = m;
                        break;
                    }
                }

                if (setAllowedMethod == null) {
                    return CommandResult.failed("AIDL: setAllowedNetworkTypesForReason", "Method not found in ITelephony on this OEM device.");
                }

                Class<?>[] pTypes = setAllowedMethod.getParameterTypes();
                if (pTypes.length == 3) {
                    setAllowedMethod.invoke(iTelephony, simInfo.subId, 0, bitmask);
                } else if (pTypes.length == 4 && pTypes[3] == String.class) {
                    setAllowedMethod.invoke(iTelephony, simInfo.subId, 0, bitmask, "com.dhangofa.networktoggle");
                } else if (pTypes.length == 2) {
                    // Some builds might just take subId and bitmask
                    setAllowedMethod.invoke(iTelephony, simInfo.subId, bitmask);
                } else {
                    return CommandResult.failed("AIDL: setAllowedNetworkTypesForReason", "Unknown method signature length: " + pTypes.length);
                }
                
                return CommandResult.completed("AIDL: setAllowedNetworkTypesForReason", 0, "Network mode applied successfully via Shizuku Binder.", "");
            } else {
                // Android 11- (API <= 30)
                if (networkMode.getLegacyMode() < 0) {
                    return CommandResult.failed("", "Invalid legacy network mode.");
                }
                
                Method setPrefMethod = null;
                for (Method m : iTelephonyClass.getDeclaredMethods()) {
                    if (m.getName().equals("setPreferredNetworkType")) {
                        setPrefMethod = m;
                        break;
                    }
                }

                if (setPrefMethod == null) {
                    return CommandResult.failed("AIDL: setPreferredNetworkType", "Method not found in ITelephony on this OEM device.");
                }

                Object result = null;
                Class<?>[] pTypes = setPrefMethod.getParameterTypes();
                if (pTypes.length == 2) {
                    result = setPrefMethod.invoke(iTelephony, simInfo.subId, networkMode.getLegacyMode());
                } else if (pTypes.length == 3 && pTypes[2] == String.class) {
                    result = setPrefMethod.invoke(iTelephony, simInfo.subId, networkMode.getLegacyMode(), "com.dhangofa.networktoggle");
                } else {
                     return CommandResult.failed("AIDL: setPreferredNetworkType", "Unknown method signature length: " + pTypes.length);
                }
                
                if (result instanceof Boolean && !((Boolean) result)) {
                    return CommandResult.failed("AIDL: setPreferredNetworkType", "ITelephony returned false.");
                }
                
                return CommandResult.completed("AIDL: setPreferredNetworkType", 0, "Legacy network mode applied successfully via Shizuku Binder.", "");
            }
        } catch (Exception e) {
            e.printStackTrace();
            return CommandResult.failed("AIDL Binder Call", "Exception: " + e.toString());
        }
    }
}

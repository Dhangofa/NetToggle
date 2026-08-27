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
    
    private static final String PACKAGE = "com.dhangofa.networktoggle";
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
            
            IBinder raw = SystemServiceHelper.getSystemService("phone");
            if (raw == null || !raw.pingBinder()) {
                return CommandResult.failed("AIDL: phone service", "Android phone service Binder is unavailable.");
            }
            
            IBinder binder = new ShizukuBinderWrapper(raw);
            if (!binder.pingBinder()) {
                return CommandResult.failed("AIDL: phone service", "Shizuku phone service Binder is not responding.");
            }
            
            @SuppressLint("PrivateApi") 
            Class<?> stub = Class.forName("com.android.internal.telephony.ITelephony$Stub");
            Object phone = stub.getDeclaredMethod("asInterface", IBinder.class).invoke(null, binder);
            
            if (phone == null) {
                return CommandResult.failed("", "Failed to get ITelephony interface via Shizuku.");
            }
            
            Class<?> api = Class.forName("com.android.internal.telephony.ITelephony");
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (networkMode.getBinaryMask() == null) {
                    return CommandResult.failed("", "Invalid network mode mask for A12+.");
                }
                
                long mask = Long.parseLong(networkMode.getBinaryMask(), 2);
                Method method = TelephonyMethodHelper.find(
                        api, 
                        "setAllowedNetworkTypesForReason",
                        new Class<?>[] {int.class, int.class, long.class},
                        new Class<?>[] {int.class, int.class, long.class, String.class},
                        new Class<?>[] {int.class, long.class}
                );
                
                if (method == null) {
                    return CommandResult.failed("AIDL: setAllowedNetworkTypesForReason", "No compatible method signature found in ITelephony.");
                }
                
                Object result = method.getParameterCount() == 3 
                        ? method.invoke(phone, simInfo.subId, 0, mask)
                        : method.getParameterCount() == 4 
                                ? method.invoke(phone, simInfo.subId, 0, mask, PACKAGE)
                                : method.invoke(phone, simInfo.subId, mask);
                                
                if (result instanceof Boolean && !((Boolean) result)) {
                    return CommandResult.failed("AIDL: setAllowedNetworkTypesForReason", "ITelephony returned false.");
                }
                
                return CommandResult.completed("AIDL: setAllowedNetworkTypesForReason", 0, "Network mode applied successfully via Shizuku Binder.", "");
            }
            
            if (networkMode.getLegacyMode() < 0) {
                return CommandResult.failed("", "Invalid legacy network mode.");
            }
            
            Method method = TelephonyMethodHelper.find(
                    api, 
                    "setPreferredNetworkType",
                    new Class<?>[] {int.class, int.class}, 
                    new Class<?>[] {int.class, int.class, String.class}
            );
            
            if (method == null) {
                return CommandResult.failed("AIDL: setPreferredNetworkType", "No compatible method signature found in ITelephony.");
            }
            
            Object result = method.getParameterCount() == 2 
                    ? method.invoke(phone, simInfo.subId, networkMode.getLegacyMode()) 
                    : method.invoke(phone, simInfo.subId, networkMode.getLegacyMode(), PACKAGE);
                    
            if (result instanceof Boolean && !((Boolean) result)) {
                return CommandResult.failed("AIDL: setPreferredNetworkType", "ITelephony returned false.");
            }
            
            return CommandResult.completed("AIDL: setPreferredNetworkType", 0, "Legacy network mode applied successfully via Shizuku Binder.", "");
            
        } catch (Throwable throwable) {
            return CommandResult.failed("AIDL Binder Call", TelephonyMethodHelper.describe(throwable));
        }
    }
}

package com.dhangofa.networktoggle.telephony;

/**
 * Reads the current network mode using the Shizuku Binder.
 * Like the controller, it makes direct binder calls to ITelephony to read the state without root.
 */

import android.annotation.SuppressLint;
import android.os.Build;
import android.os.IBinder;

import com.dhangofa.networktoggle.model.ExecutionMode;
import com.dhangofa.networktoggle.model.NetworkMode;

import java.lang.reflect.Method;

import rikka.shizuku.ShizukuBinderWrapper;
import rikka.shizuku.SystemServiceHelper;

final class ShizukuBinderModeReader {

    private static final String PACKAGE = "com.dhangofa.networktoggle";
    private final SimResolver simResolver;

    ShizukuBinderModeReader(SimResolver simResolver) {
        this.simResolver = simResolver;
    }

    NetworkMode readCurrentMode(ExecutionMode executionMode) {
        if (executionMode != ExecutionMode.SHIZUKU) {
            return NetworkMode.UNKNOWN;
        }

        int subId = simResolver.resolveTargetSubId(executionMode);
        
        if (!simResolver.isValidSubId(subId)) {
            return NetworkMode.UNKNOWN;
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                org.lsposed.hiddenapibypass.HiddenApiBypass.addHiddenApiExemptions("Lcom/android/internal/telephony/");
            }

            IBinder raw = SystemServiceHelper.getSystemService("phone");
            if (raw == null || !raw.pingBinder()) {
                return NetworkMode.UNKNOWN;
            }

            IBinder binder = new ShizukuBinderWrapper(raw);
            if (!binder.pingBinder()) {
                return NetworkMode.UNKNOWN;
            }

            @SuppressLint("PrivateApi") 
            Class<?> stub = Class.forName("com.android.internal.telephony.ITelephony$Stub");
            Object phone = stub.getDeclaredMethod("asInterface", IBinder.class).invoke(null, binder);
            
            Class<?> api = Class.forName("com.android.internal.telephony.ITelephony");

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Method method = TelephonyMethodHelper.find(
                        api, 
                        "getAllowedNetworkTypesForReason",
                        new Class<?>[] {int.class, int.class}, 
                        new Class<?>[] {int.class, int.class, String.class}, 
                        new Class<?>[] {int.class}
                );
                
                if (method == null) {
                    return NetworkMode.UNKNOWN;
                }

                Object value = method.getParameterCount() == 2 
                        ? method.invoke(phone, subId, 0)
                        : method.getParameterCount() == 3 
                                ? method.invoke(phone, subId, 0, PACKAGE) 
                                : method.invoke(phone, subId);
                                
                if (!(value instanceof Number)) {
                    return NetworkMode.UNKNOWN;
                }
                
                long bitmask = ((Number) value).longValue();
                
                for (NetworkMode mode : NetworkMode.values()) {
                    if (mode.getBinaryMask() != null && Long.parseLong(mode.getBinaryMask(), 2) == bitmask) {
                        return mode;
                    }
                }
                
                if ((bitmask & (1L << 19)) != 0) {
                    return (bitmask & (1L << 12)) == 0 ? NetworkMode.FIVE_G_ONLY : NetworkMode.PREFERRED_5G;
                }
                
                if ((bitmask & (1L << 12)) != 0) {
                    return (bitmask & (1L << 13)) == 0 && (bitmask & (1L << 9)) == 0 
                            ? NetworkMode.FOUR_G_ONLY 
                            : NetworkMode.PREFERRED_4G;
                }
                
                return NetworkMode.PREFERRED_3G;
            }

            Method method = TelephonyMethodHelper.find(
                    api, 
                    "getPreferredNetworkType",
                    new Class<?>[] {int.class}, 
                    new Class<?>[] {int.class, String.class}
            );
            
            if (method == null) {
                return NetworkMode.UNKNOWN;
            }
            
            Object value = method.getParameterCount() == 1 
                    ? method.invoke(phone, subId) 
                    : method.invoke(phone, subId, PACKAGE);
                    
            return value instanceof Number 
                    ? NetworkMode.fromLegacyMode(((Number) value).intValue()) 
                    : NetworkMode.UNKNOWN;
                    
        } catch (Throwable ignored) {
            return NetworkMode.UNKNOWN;
        }
    }
}

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
    private final SimResolver simResolver;

    ShizukuBinderModeReader(SimResolver simResolver) {
        this.simResolver = simResolver;
    }

    NetworkMode readCurrentMode(ExecutionMode executionMode) {
        if (executionMode != ExecutionMode.SHIZUKU) {
            return NetworkMode.UNKNOWN;
        }

        int targetSubId = simResolver.resolveTargetSubId(executionMode);
        if (!simResolver.isValidSubId(targetSubId)) {
            return NetworkMode.UNKNOWN;
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                org.lsposed.hiddenapibypass.HiddenApiBypass.addHiddenApiExemptions("Lcom/android/internal/telephony/");
            }

            IBinder binder = new ShizukuBinderWrapper(SystemServiceHelper.getSystemService("phone"));
            if (binder == null) {
                return NetworkMode.UNKNOWN;
            }

            @SuppressLint("PrivateApi")
            Class<?> iTelephonyStubClass = Class.forName("com.android.internal.telephony.ITelephony$Stub");
            Method asInterfaceMethod = iTelephonyStubClass.getDeclaredMethod("asInterface", IBinder.class);
            Object iTelephony = asInterfaceMethod.invoke(null, binder);

            if (iTelephony == null) {
                return NetworkMode.UNKNOWN;
            }

            Class<?> iTelephonyClass = Class.forName("com.android.internal.telephony.ITelephony");

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Method getAllowedMethod = null;
                for (Method m : iTelephonyClass.getDeclaredMethods()) {
                    if (m.getName().equals("getAllowedNetworkTypesForReason")) {
                        getAllowedMethod = m;
                        break;
                    }
                }

                if (getAllowedMethod != null) {
                    Class<?>[] pTypes = getAllowedMethod.getParameterTypes();
                    long bitmask = -1;
                    if (pTypes.length == 2) {
                        bitmask = (long) getAllowedMethod.invoke(iTelephony, targetSubId, 0 /* reason user */);
                    } else if (pTypes.length == 3 && pTypes[2] == String.class) {
                        bitmask = (long) getAllowedMethod.invoke(iTelephony, targetSubId, 0, "com.dhangofa.networktoggle");
                    } else if (pTypes.length == 1) {
                         bitmask = (long) getAllowedMethod.invoke(iTelephony, targetSubId);
                    }
                    
                    if (bitmask != -1) {
                        // Find closest exact matching NetworkMode by bitmask
                        for (NetworkMode mode : NetworkMode.values()) {
                            if (mode.getBinaryMask() != null) {
                                long modeMask = Long.parseLong(mode.getBinaryMask(), 2);
                                if (modeMask == bitmask) {
                                    return mode;
                                }
                            }
                        }
                        
                        // Approximate mapping if exact bitmask fails
                        if ((bitmask & (1L << 19 /* NR */)) != 0) {
                             if ((bitmask & (1L << 12 /* LTE */)) == 0) return NetworkMode.FIVE_G_ONLY;
                             return NetworkMode.PREFERRED_5G;
                        }
                        if ((bitmask & (1L << 12 /* LTE */)) != 0) {
                             if ((bitmask & (1L << 13 /* TD_SCDMA */)) == 0 && (bitmask & (1L << 9 /* WCDMA */)) == 0) return NetworkMode.FOUR_G_ONLY;
                             return NetworkMode.PREFERRED_4G;
                        }
                        return NetworkMode.PREFERRED_3G;
                    }
                }
            } else {
                Method getPrefMethod = null;
                for (Method m : iTelephonyClass.getDeclaredMethods()) {
                    if (m.getName().equals("getPreferredNetworkType")) {
                        getPrefMethod = m;
                        break;
                    }
                }

                if (getPrefMethod != null) {
                    Class<?>[] pTypes = getPrefMethod.getParameterTypes();
                    int legacyMode = -1;
                    if (pTypes.length == 1) {
                        legacyMode = (int) getPrefMethod.invoke(iTelephony, targetSubId);
                    } else if (pTypes.length == 2 && pTypes[1] == String.class) {
                        legacyMode = (int) getPrefMethod.invoke(iTelephony, targetSubId, "com.dhangofa.networktoggle");
                    }
                    
                    if (legacyMode != -1) {
                        return NetworkMode.fromLegacyMode(legacyMode);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return NetworkMode.UNKNOWN;
    }
}

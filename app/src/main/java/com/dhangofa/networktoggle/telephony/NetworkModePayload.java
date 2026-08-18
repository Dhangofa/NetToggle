package com.dhangofa.networktoggle.telephony;

import android.annotation.SuppressLint;
import android.os.IBinder;
import androidx.annotation.Keep;
import java.lang.reflect.Method;

@Keep
public class NetworkModePayload {
    @Keep
    public static void main(String[] args) {
        try {
            if (args.length < 2) {
                System.err.println("Usage: <subId> <networkMode>");
                System.exit(1);
            }
            int subId = Integer.parseInt(args[0]);
            int networkMode = Integer.parseInt(args[1]);
            
            @SuppressLint("PrivateApi")
            Class<?> serviceManagerClass = Class.forName("android.os.ServiceManager");
            Method getServiceMethod = serviceManagerClass.getDeclaredMethod("getService", String.class);
            IBinder binder = (IBinder) getServiceMethod.invoke(null, "phone");
            
            if (binder == null) {
                System.err.println("Failed to get 'phone' service binder.");
                System.exit(1);
            }
            
            @SuppressLint("PrivateApi")
            Class<?> iTelephonyStubClass = Class.forName("com.android.internal.telephony.ITelephony$Stub");
            Method asInterfaceMethod = iTelephonyStubClass.getDeclaredMethod("asInterface", IBinder.class);
            Object iTelephony = asInterfaceMethod.invoke(null, binder);
            
            if (iTelephony == null) {
                System.err.println("Failed to get ITelephony interface.");
                System.exit(1);
            }
            
            Method setPrefNetworkTypeMethod = iTelephony.getClass().getMethod("setPreferredNetworkType", int.class, int.class);
            Object result = setPrefNetworkTypeMethod.invoke(iTelephony, subId, networkMode);
            
            if (result instanceof Boolean && !((Boolean) result)) {
                System.err.println("ITelephony returned false.");
                System.exit(1);
            } else {
                System.out.println("Success");
                System.exit(0);
            }
        } catch (Exception e) {
            e.printStackTrace(System.err);
            System.exit(1);
        }
    }
}

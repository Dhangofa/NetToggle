package com.dhangofa.networktoggle;

import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import com.topjohnwu.superuser.ipc.RootService;
import java.lang.reflect.Method;

public class RootNetworkService extends RootService {

    @Override
    public IBinder onBind(Intent intent) {
        return new INetworkService.Stub() {
            @Override
            public void setNetworkMode(long bitmask) {
                try {
                    TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
                    
                    // The hidden Android API used by FivegTile
                    Method setModeMethod = TelephonyManager.class.getMethod("setAllowedNetworkTypesForReason", int.class, long.class);
                    
                    int dataSubId = SubscriptionManager.getDefaultDataSubscriptionId();
                    if (dataSubId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                        TelephonyManager tmSub = tm.createForSubscriptionId(dataSubId);
                        // 0 = ALLOWED_NETWORK_TYPES_REASON_USER
                        setModeMethod.invoke(tmSub, 0, bitmask); 
                    } else {
                        setModeMethod.invoke(tm, 0, bitmask);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };
    }
}

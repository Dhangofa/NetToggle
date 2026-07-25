package com.dhangofa.networktoggle;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import com.topjohnwu.superuser.Shell;
import com.topjohnwu.superuser.ipc.RootService;

public class NetworkTileService extends TileService {

    private static final String PREFS_NAME = "NetTogglePrefs";
    private static final String STATE_KEY = "net_state";

    // Direct TelephonyManager Bitmasks
    private static final long MASK_4G_ONLY = (1L << 12); 
    private static final long MASK_5G_ONLY = (1L << 19); 
    private static final long MASK_PREF_4G = (1L << 12) | (1L << 14) | (1L << 7) | (1L << 2) | (1L << 1) | (1L << 0);
    private static final long MASK_PREF_5G = (1L << 19) | MASK_PREF_4G;

    static {
        // Pre-configure the root shell
        Shell.enableVerboseLogging = false;
        Shell.setDefaultBuilder(Shell.Builder.create()
                .setFlags(Shell.FLAG_REDIRECT_STDERR)
                .setTimeout(10));
    }

    @Override
    public void onStartListening() {
        super.onStartListening();
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        updateTileUI(prefs.getInt(STATE_KEY, 1));
    }

    @Override
    public void onClick() {
        super.onClick();
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        int currentState = prefs.getInt(STATE_KEY, 1);

        int nextState = (currentState % 4) + 1;
        long targetBitmask = MASK_4G_ONLY;

        switch (nextState) {
            case 1: targetBitmask = MASK_4G_ONLY; break;
            case 2: targetBitmask = MASK_5G_ONLY; break;
            case 3: targetBitmask = MASK_PREF_5G; break;
            case 4: targetBitmask = MASK_PREF_4G; break;
        }

        applyNetworkMode(targetBitmask, nextState);
    }

    private void updateTileUI(int state) {
        Tile tile = getQsTile();
        if (tile == null) return;
        tile.setState(Tile.STATE_ACTIVE);
        switch (state) {
            case 1: tile.setLabel("4G"); break;
            case 2: tile.setLabel("5G"); break;
            case 3: tile.setLabel("P5G"); break;
            case 4: tile.setLabel("P4G"); break;
        }
        tile.updateTile();
    }

    private void applyNetworkMode(long bitmask, int nextState) {
        Intent intent = new Intent(this, RootNetworkService.class);
        
        // Connects to the Root process in the background
        RootService.bind(intent, new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                INetworkService rootService = INetworkService.Stub.asInterface(service);
                try {
                    rootService.setNetworkMode(bitmask);
                    
                    // Only save state and update UI after the API successfully executes
                    SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
                    prefs.edit().putInt(STATE_KEY, nextState).apply();
                    updateTileUI(nextState);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                // Unbind immediately to save battery
                RootService.unbind(this); 
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {}
        });
    }
}

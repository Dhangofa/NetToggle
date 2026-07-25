package com.dhangofa.networktoggle;

import android.content.SharedPreferences;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import java.io.DataOutputStream;

public class NetworkTileService extends TileService {

    private static final String PREFS_NAME = "NetTogglePrefs";
    private static final String STATE_KEY = "net_state";

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

        // Cycle through states: 1 (4G) -> 2 (5G) -> 3 (P5G) -> 4 (P4G)
        int nextState = (currentState % 4) + 1;
        int networkId = 11; 

        switch (nextState) {
            case 1: networkId = 11; break; // Strict 4G (LTE only)
            case 2: networkId = 23; break; // Strict 5G (NR only)
            case 3: networkId = 33; break; // Preferred 5G (NR/LTE/...)
            case 4: networkId = 9;  break; // Preferred 4G (LTE/WCDMA/...)
        }

        applyNetworkMode(networkId);

        prefs.edit().putInt(STATE_KEY, nextState).apply();
        updateTileUI(nextState);
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

    private void applyNetworkMode(int networkId) {
        try {
            Process process = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(process.getOutputStream());

            os.writeBytes("settings put global preferred_network_mode " + networkId + "\n");
            os.writeBytes("settings put global preferred_network_mode1 " + networkId + "\n");
            os.writeBytes("settings put global preferred_network_mode2 " + networkId + "\n");

            os.writeBytes("cmd connectivity airplane-mode enable\n");
            os.writeBytes("sleep 1\n");
            os.writeBytes("cmd connectivity airplane-mode disable\n");

            os.writeBytes("exit\n");
            os.flush();
            process.waitFor();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

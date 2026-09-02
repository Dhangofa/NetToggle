package com.dhangofa.networktoggle;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

public class ShortcutActionActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        Intent intent = getIntent();
        if (intent != null) {
            String mode = intent.getStringExtra("mode");
            int sim = intent.getIntExtra("sim", -1);
            
            if (mode != null) {
                Intent broadcastIntent = new Intent(this, AutomationReceiver.class);
                broadcastIntent.setAction("com.dhangofa.networktoggle.SET_MODE");
                broadcastIntent.putExtra("mode", mode);
                broadcastIntent.putExtra("sim", sim);
                sendBroadcast(broadcastIntent);
                
                String displayMode = mode.replace("_ONLY", " Only").replace("PREF_", "Pref ");
                String target = "";
                if (sim == 1) target = " on SIM 1";
                else if (sim == 2) target = " on SIM 2";
                else if (sim == 3) target = " on Both SIMs";
                
                Toast.makeText(getApplicationContext(), getString(R.string.toast_applying_mode, displayMode, target), Toast.LENGTH_SHORT).show();
            }
        }
        
        finish();
    }
}

package com.dhangofa.networktoggle;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import com.dhangofa.networktoggle.automation.AutomationExecutor;
import com.dhangofa.networktoggle.automation.AutomationRequest;
import com.dhangofa.networktoggle.model.NetworkMode;
import com.dhangofa.networktoggle.model.TargetSim;
import com.dhangofa.networktoggle.util.AppExecutors;

public class ShortcutActionActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        if (intent != null) {
            String mode = intent.getStringExtra("mode");
            int sim = intent.getIntExtra("sim", -1);

            if (mode != null) {
                TargetSim targetSim = TargetSim.AUTO;
                if (sim == 1) targetSim = TargetSim.SIM_1;
                else if (sim == 2) targetSim = TargetSim.SIM_2;
                else if (sim == 3) targetSim = TargetSim.BOTH;
                else {
                    com.dhangofa.networktoggle.config.AppPreferences prefs = new com.dhangofa.networktoggle.config.AppPreferences(this);
                    targetSim = prefs.getTargetSim();
                }

                NetworkMode targetMode = NetworkMode.fromString(mode);
                if (targetMode != NetworkMode.UNKNOWN) {
                    final TargetSim finalTargetSim = targetSim;
                    AppExecutors.executeTelephony(() -> {
                        AutomationRequest request = new AutomationRequest(targetMode, finalTargetSim, false, "Shortcut");
                        AutomationExecutor.execute(getApplicationContext(), request);
                    });
                }

                String displayMode = mode.replace("_ONLY", " Only").replace("PREF_", "Pref ");
                String target = "";
                if (sim == 1) target = " on SIM 1";
                else if (sim == 2) target = " on SIM 2";
                else if (sim == 3) target = " on Both SIMs";

                Toast.makeText(getApplicationContext(), getString(R.string.toast_applying) + " " + displayMode + target + "...", Toast.LENGTH_SHORT).show();
            }
        }

        finish();
    }
}

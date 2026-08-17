package com.dhangofa.networktoggle.util;

import android.os.Build;
import com.dhangofa.networktoggle.config.AppPreferences;
import com.dhangofa.networktoggle.model.DiagnosticError;
import com.dhangofa.networktoggle.telephony.SimResolver;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class DiagnosticReporter {
    public static String generateReport(AppPreferences prefs, SimResolver simResolver) {
        DiagnosticError error = prefs.getLastError();
        if (error == null) {
            return "No recent errors recorded.";
        }

        StringBuilder sb = new StringBuilder();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);

        sb.append("--- NETWORK TOGGLE DIAGNOSTIC REPORT ---\n");
        sb.append("Time: ").append(sdf.format(new Date(error.timestamp))).append("\n\n");

        sb.append("[DEVICE INFO]\n");
        sb.append("Manufacturer: ").append(Build.MANUFACTURER).append("\n");
        sb.append("Brand: ").append(Build.BRAND).append("\n");
        sb.append("Device: ").append(Build.DEVICE).append("\n");
        sb.append("Model: ").append(Build.MODEL).append("\n");
        sb.append("Android Version: ").append(Build.VERSION.RELEASE).append("\n");
        sb.append("SDK Level: ").append(Build.VERSION.SDK_INT).append("\n\n");

        sb.append("[APP STATE]\n");
        sb.append("Execution Mode: ").append(prefs.getExecutionMode().name()).append("\n");
        sb.append("Target SIM Setting: ").append(prefs.getTargetSim().name()).append("\n");

        int slotIndex = simResolver.resolveTargetSlotIndex(prefs.getExecutionMode());
        sb.append("Resolved Slot Index: ").append(slotIndex).append("\n\n");

        sb.append("[ERROR DETAILS]\n");
        sb.append("Command Attempted:\n").append(error.command).append("\n\n");

        sb.append("Exit Code: ").append(error.exitCode).append("\n\n");

        if (error.exceptionMessage != null && !error.exceptionMessage.isEmpty()) {
            sb.append("Exception Message:\n").append(error.exceptionMessage).append("\n\n");
        }

        if (error.stdout != null && !error.stdout.isEmpty()) {
            sb.append("Standard Output (stdout):\n").append(error.stdout).append("\n\n");
        }

        sb.append("Standard Error (stderr):\n").append(error.stderr != null && !error.stderr.isEmpty() ? error.stderr : "(empty)").append("\n\n");

        sb.append("--- END OF REPORT ---");
        return sb.toString();
    }
}

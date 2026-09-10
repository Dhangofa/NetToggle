package com.dhangofa.networktoggle.util;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import com.dhangofa.networktoggle.config.AppPreferences;
import com.dhangofa.networktoggle.model.DiagnosticError;
import com.dhangofa.networktoggle.model.ExecutionMode;
import com.dhangofa.networktoggle.telephony.SimResolver;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import rikka.shizuku.Shizuku;

public class DiagnosticReporter {

    public static String generateReport(Context context, AppPreferences prefs, SimResolver simResolver) {
        StringBuilder sb = new StringBuilder();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);

        sb.append("--- NETWORK TOGGLE DIAGNOSTIC REPORT ---\n");
        sb.append("Generated: ").append(sdf.format(new Date())).append("\n\n");

        sb.append("[DEVICE INFO]\n");
        sb.append("Manufacturer: ").append(Build.MANUFACTURER).append("\n");
        sb.append("Brand: ").append(Build.BRAND).append("\n");
        sb.append("Device: ").append(Build.DEVICE).append("\n");
        sb.append("Product: ").append(Build.PRODUCT).append("\n");
        sb.append("Model: ").append(Build.MODEL).append("\n");
        sb.append("Android Version: ").append(Build.VERSION.RELEASE)
                .append(" (SDK ").append(Build.VERSION.SDK_INT).append(")\n\n");

        sb.append("[APP STATE]\n");
        try {
            PackageInfo pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            long versionCode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ? pInfo.getLongVersionCode() : pInfo.versionCode;
            sb.append("App Version: ").append(pInfo.versionName).append(" (").append(versionCode).append(")\n");
        } catch (Exception e) {
            sb.append("App Version: Unknown\n");
        }
        sb.append("Execution Mode: ").append(prefs.getExecutionMode().name()).append("\n");
        sb.append("Target SIM Setting: ").append(prefs.getTargetSim().name()).append("\n");
        int slotIndex = simResolver.resolveTargetSlotIndex(prefs.getExecutionMode());
        sb.append("Resolved Slot Index: ").append(slotIndex).append("\n\n");

        ExecutionMode selectedMode = prefs.getExecutionMode();
        if (selectedMode == ExecutionMode.ROOT) {
            sb.append("[ROOT STATUS]\n");
            Process process = null;
            try {
                process = Runtime.getRuntime().exec(new String[]{"su", "-c", "id"});
                int exitCode = -1;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    boolean finished = process.waitFor(2, TimeUnit.SECONDS);
                    if (finished) {
                        exitCode = process.exitValue();
                    } else {
                        process.destroy();
                    }
                } else {
                    exitCode = process.waitFor();
                }
                boolean granted = (exitCode == 0);
                sb.append("Root Access: ").append(granted ? "Granted" : "Denied/Unavailable").append("\n");
                if (granted) {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                        String line = reader.readLine();
                        if (line != null && !line.trim().isEmpty()) {
                            sb.append("Identity: ").append(line.trim()).append("\n");
                        }
                    }
                } else {
                    sb.append("Exit Code: ").append(exitCode).append("\n");
                }
            } catch (Throwable e) {
                sb.append("Root Access: Unavailable (").append(e.getClass().getSimpleName()).append(")\n");
            } finally {
                if (process != null) {
                    process.destroy();
                }
            }
            sb.append("\n");
        } else if (selectedMode == ExecutionMode.SHIZUKU) {
            sb.append("[SHIZUKU STATUS]\n");
            try {
                boolean isBinderAlive = Shizuku.pingBinder();
                sb.append("Binder Alive: ").append(isBinderAlive).append("\n");
                if (isBinderAlive) {
                    sb.append("Shizuku Version: ").append(Shizuku.getVersion()).append("\n");
                    boolean hasPermission = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
                    sb.append("Permission Granted: ").append(hasPermission).append("\n");
                }
            } catch (Throwable e) {
                sb.append("Status: Error reading Shizuku status (").append(e.getClass().getSimpleName()).append(")\n");
            }
            sb.append("\n");
        }

        sb.append("[LAST ERROR EVENT]\n");
        DiagnosticError error = prefs.getLastError();
        if (error != null) {
            sb.append("Time: ").append(sdf.format(new Date(error.timestamp))).append("\n\n");
            sb.append("Command Attempted:\n").append(error.command != null ? error.command : "N/A").append("\n\n");
            sb.append("Exit Code: ").append(error.exitCode).append("\n\n");
            if (error.exceptionMessage != null && !error.exceptionMessage.isEmpty()) {
                sb.append("Exception Message:\n").append(error.exceptionMessage).append("\n\n");
            }
            if (error.stdout != null && !error.stdout.isEmpty()) {
                sb.append("Standard Output (stdout):\n").append(error.stdout).append("\n\n");
            }
            sb.append("Standard Error (stderr):\n").append(error.stderr != null && !error.stderr.isEmpty() ? error.stderr : "(empty)").append("\n");
        } else {
            sb.append("No recent errors recorded.\n");
        }

        sb.append("\n--- END OF REPORT ---");
        return sb.toString();
    }
}

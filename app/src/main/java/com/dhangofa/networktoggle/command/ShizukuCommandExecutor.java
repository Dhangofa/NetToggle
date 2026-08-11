package com.dhangofa.networktoggle.command;

import android.content.pm.PackageManager;
import com.dhangofa.networktoggle.model.CommandResult;
import java.lang.reflect.Method;
import rikka.shizuku.Shizuku;

public final class ShizukuCommandExecutor implements CommandExecutor {
    private static Method newProcessMethod;

    @Override
    public CommandResult execute(String command) {
        Process process = null;
        try {
            if (!Shizuku.pingBinder()) return CommandResult.failed(command, "Shizuku is not running.");
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                return CommandResult.failed(command, "Shizuku permission is not granted.");
            }
            process = (Process) getNewProcessMethod().invoke(
                    null, new String[]{"sh", "-c", command}, null, null);
            if (process == null) return CommandResult.failed(command, "Shizuku did not create a shell process.");
            return ProcessResultReader.collect(command, process);
        } catch (Exception e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            String message = cause.getMessage();
            return CommandResult.failed(command,
                    message == null || message.trim().isEmpty()
                            ? cause.getClass().getSimpleName()
                            : cause.getClass().getSimpleName() + ": " + message);
        } finally {
            if (process != null) process.destroy();
        }
    }

    private static synchronized Method getNewProcessMethod() throws NoSuchMethodException {
        if (newProcessMethod == null) {
            newProcessMethod = Shizuku.class.getDeclaredMethod(
                    "newProcess", String[].class, String[].class, String.class);
            newProcessMethod.setAccessible(true);
        }
        return newProcessMethod;
    }
}

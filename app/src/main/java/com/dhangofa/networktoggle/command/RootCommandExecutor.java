package com.dhangofa.networktoggle.command;

/**
 * Implementation of CommandExecutor that uses standard su (Root) privileges.
 * It spawns a root shell, runs the command, and collects the output via ProcessResultReader.
 */

import com.dhangofa.networktoggle.model.CommandResult;

public final class RootCommandExecutor implements CommandExecutor {
    @Override
    public CommandResult execute(String command) {
        Process process = null;
        try {
            process = Runtime.getRuntime().exec(new String[]{"su", "-c", command});
            return ProcessResultReader.collect(command, process);
        } catch (Exception e) {
            return CommandResult.failed(command, describe(e));
        } finally {
            if (process != null) process.destroy();
        }
    }

    private String describe(Exception e) {
        String message = e.getMessage();
        return message == null || message.trim().isEmpty()
                ? e.getClass().getSimpleName()
                : e.getClass().getSimpleName() + ": " + message;
    }
}

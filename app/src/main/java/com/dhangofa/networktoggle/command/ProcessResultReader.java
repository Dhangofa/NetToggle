package com.dhangofa.networktoggle.command;

/**
 * Helper to consume standard output and error streams from a process.
 * This prevents deadlocks that can happen if the output buffer fills up before it is read.
 */

import com.dhangofa.networktoggle.model.CommandResult;

final class ProcessResultReader {
    private ProcessResultReader() {}

    static CommandResult collect(String command, Process process) throws Exception {
        StreamCollector stdout = new StreamCollector("NetToggle-stdout", process.getInputStream());
        StreamCollector stderr = new StreamCollector("NetToggle-stderr", process.getErrorStream());
        stdout.start();
        stderr.start();
        int exitCode = process.waitFor();
        stdout.join();
        stderr.join();
        return CommandResult.completed(command, exitCode, stdout.getOutput(), stderr.getOutput());
    }
}

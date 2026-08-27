package com.dhangofa.networktoggle.model;

/**
 * Wrapper to pass back the results of shell commands (stdout, stderr, exit code).
 * Helps easily check if a command succeeded or failed.
 */

public final class CommandResult {
    private final String command;
    private final int exitCode;
    private final String stdout;
    private final String stderr;
    private final String exceptionMessage;

    private CommandResult(String command, int exitCode, String stdout, String stderr, String exceptionMessage) {
        this.command = command == null ? "" : command;
        this.exitCode = exitCode;
        this.stdout = stdout == null ? "" : stdout;
        this.stderr = stderr == null ? "" : stderr;
        this.exceptionMessage = exceptionMessage == null ? "" : exceptionMessage;
    }

    public static CommandResult completed(String command, int exitCode, String stdout, String stderr) {
        return new CommandResult(command, exitCode, stdout, stderr, "");
    }

    public static CommandResult failed(String command, String message) {
        return new CommandResult(command, -1, "", "", message);
    }

    public boolean isSuccess() { return exitCode == 0; }
    public String getCommand() { return command; }
    public int getExitCode() { return exitCode; }
    public String getStdout() { return stdout; }
    public String getStderr() { return stderr; }
    public String getExceptionMessage() { return exceptionMessage; }
}

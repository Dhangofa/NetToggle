package com.dhangofa.networktoggle.model;

/**
 * Tracks diagnostic errors (like Shizuku Binder death, or Root permission denied)
 * to display meaningful error banners in the app.
 */

public class DiagnosticError {
    public final String command;
    public final int exitCode;
    public final String stdout;
    public final String stderr;
    public final String exceptionMessage;
    public final long timestamp;

    public DiagnosticError(String command, int exitCode, String stdout, String stderr, String exceptionMessage, long timestamp) {
        this.command = command;
        this.exitCode = exitCode;
        this.stdout = stdout;
        this.stderr = stderr;
        this.exceptionMessage = exceptionMessage;
        this.timestamp = timestamp;
    }
}

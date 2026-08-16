package com.dhangofa.networktoggle.model;

public class DiagnosticError {
    public final String command;
    public final String stderr;
    public final long timestamp;

    public DiagnosticError(String command, String stderr, long timestamp) {
        this.command = command;
        this.stderr = stderr;
        this.timestamp = timestamp;
    }
}

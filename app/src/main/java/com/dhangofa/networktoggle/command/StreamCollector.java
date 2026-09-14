package com.dhangofa.networktoggle.command;

/**
 * Utility class used internally by ProcessResultReader to asynchronously read an InputStream
 * into a String. It runs on a separate thread to keep things non-blocking.
 */

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

final class StreamCollector extends Thread {
    private final InputStream inputStream;
    private final StringBuilder output = new StringBuilder();

    StreamCollector(String name, InputStream inputStream) {
        super(name);
        this.inputStream = inputStream;
        setDaemon(true);
    }

    @Override
    public void run() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append('\n');
            }
        } catch (Exception ignored) {
        }
    }

    String getOutput() {
        return output.toString().trim();
    }
}

package com.dhangofa.networktoggle.telephony;

/**
 * Utility to parse the raw text output from the `cmd phone` shell commands
 * back into the NetworkMode enum.
 */

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ShellValueParser {
    // Updated regex to support negative numbers (e.g., -1 for invalid slot)
    private static final Pattern NUMBER_PATTERN = Pattern.compile("-?\\d+");

    private ShellValueParser() {
    }

    public static Integer extractFirstInt(String text) {
        try {
            if (text == null
                    || text.trim().isEmpty()
                    || text.trim().equalsIgnoreCase("null")) {
                return null;
            }

            Matcher matcher = NUMBER_PATTERN.matcher(text);
            return matcher.find() ? Integer.parseInt(matcher.group()) : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * Finds a specific key (e.g., "simSlotIndex=") and extracts the integer immediately after it.
     */
    public static Integer extractIntByKey(String text, String key) {
        if (text == null || key == null) return null;
        int index = text.indexOf(key);
        if (index == -1) return null;

        // Pass only the remainder of the string to find the first int
        String remainder = text.substring(index + key.length());
        return extractFirstInt(remainder);
    }

    /**
     * Extracts a string value located between a starting key and an ending delimiter.
     */
    public static String extractStringByKey(String text, String key, String endDelimiter) {
        if (text == null || key == null) return "";
        int start = text.indexOf(key);
        if (start == -1) return "";
        start += key.length();

        int end = text.indexOf(endDelimiter, start);
        if (end == -1) {
            end = text.indexOf(" ", start); // fallback to next space
            if (end == -1) end = text.length();
        }

        return text.substring(start, end).trim();
    }
}

package com.dhangofa.networktoggle.telephony;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ShellValueParser {
    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\d+");

    private ShellValueParser() {
    }

    static Integer extractFirstInt(String text) {
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
}

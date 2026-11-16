package com.applocker.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reads the project .env file into system properties before Spring starts.
 * Values already set in the environment or as system properties stay as they are.
 */
public final class LocalEnvFile {

    private LocalEnvFile() {
    }

    public static void load() throws IOException {
        Path file = Path.of(".env");
        if (!Files.isRegularFile(file)) {
            file = Path.of("..", ".env");
        }
        if (!Files.isRegularFile(file)) {
            return;
        }
        for (Map.Entry<String, String> entry : parse(Files.readString(file)).entrySet()) {
            if (setting(entry.getKey()) == null) {
                System.setProperty(entry.getKey(), entry.getValue());
            }
        }
    }

    static Map<String, String> parse(String text) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String rawLine : text.split("\\R")) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (line.startsWith("export ")) {
                line = line.substring("export ".length()).trim();
            }
            int split = line.indexOf('=');
            if (split <= 0) {
                continue;
            }
            String key = line.substring(0, split).trim();
            String value = stripQuotes(line.substring(split + 1).trim());
            if (!key.isEmpty() && !value.isEmpty()) {
                values.put(key, value);
            }
        }
        return values;
    }

    public static String setting(String name) {
        String fromEnvironment = System.getenv(name);
        if (fromEnvironment != null && !fromEnvironment.isBlank()) {
            return fromEnvironment;
        }
        String property = System.getProperty(name);
        if (property != null && !property.isBlank()) {
            return property;
        }
        return null;
    }

    private static String stripQuotes(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }
}

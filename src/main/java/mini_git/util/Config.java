package mini_git.util;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class Config {

    private final Map<String, Map<String, String>> sections = new LinkedHashMap<>();

    public void load(Path path) {
        sections.clear();

        if (!Files.exists(path)) {
            return;
        }

        try (BufferedReader reader = Files.newBufferedReader(path)) {

            String currentSection = null;
            String line;

            while ((line = reader.readLine()) != null) {
                line = line.trim();

                if (line.isEmpty() || line.startsWith("#") || line.startsWith(";")) {
                    continue;
                }

                if (line.startsWith("[") && line.endsWith("]")) {
                    currentSection = parseSection(line);
                    sections.putIfAbsent(currentSection, new LinkedHashMap<>());
                }
                else if (line.contains("=") && currentSection != null) {
                    parseKeyValue(currentSection, line);
                }
                else {
                    throw new IllegalArgumentException("Invalid config line: " + line);
                }
            }

        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load config", e);
        }
    }

    public void save(Path path) {
        try (BufferedWriter writer = Files.newBufferedWriter(path)) {

            for (var sectionEntry : sections.entrySet()) {

                writer.write("[" + sectionEntry.getKey() + "]");
                writer.newLine();

                for (var keyEntry : sectionEntry.getValue().entrySet()) {
                    writer.write(keyEntry.getKey() + " = " + keyEntry.getValue());
                    writer.newLine();
                }

                writer.newLine();
            }

        } catch (IOException e) {
            throw new UncheckedIOException("Failed to save config", e);
        }
    }

    public String get(String section, String key) {
        Map<String, String> map = sections.get(section);
        if (map == null) return null;
        return map.get(key);
    }

    public void set(String section, String key, String value) {
        sections
            .computeIfAbsent(section, s -> new LinkedHashMap<>())
            .put(key, value);
    }

    public boolean hasSection(String section) {
        return sections.containsKey(section);
    }

    public Map<String, String> getSection(String section) {
        return sections.getOrDefault(section, Collections.emptyMap());
    }

    private String parseSection(String line) {
        return line.substring(1, line.length() - 1).trim();
    }

    private void parseKeyValue(String section, String line) {
        String[] parts = line.split("=", 2);

        String key = parts[0].trim();
        String value = parts[1].trim();

        sections.get(section).put(key, value);
    }
}

package mini_git.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class IndexManager {

    private static final Path INDEX_PATH = Path.of(".minigit", "index");

    public static Map<String, String> loadIndex() {
        if (!Files.exists(INDEX_PATH)) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, String> index = new LinkedHashMap<>();
            for (String line : Files.readAllLines(INDEX_PATH)) {
                String[] parts = line.split(" ", 2);
                if (parts.length == 2) {
                    index.put(parts[1], parts[0]); // path -> hash
                }
            }
            return index;
        } catch (IOException e) {
            System.err.println("Failed to read index: " + e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    public static void writeIndex(Map<String, String> index) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : index.entrySet()) {
            sb.append(entry.getValue()).append(" ").append(entry.getKey()).append(System.lineSeparator());
        }
        try {
            Files.writeString(INDEX_PATH, sb.toString());
        } catch (IOException e) {
            System.err.println("Failed to write index: " + e.getMessage());
        }
    }
}

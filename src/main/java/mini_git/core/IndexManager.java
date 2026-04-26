package mini_git.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public class IndexManager {

    private static final Path INDEX_PATH = Path.of(".minigit", "index");

    /**
     * Reads the index file and returns a map of tracked files to their blob SHAs
     * @return Map of FilePath → Sha
     */
    public static Map<FilePath, Sha> loadIndex() {
        if (!Files.exists(INDEX_PATH)) {
            return new LinkedHashMap<>();
        }
        try {
            Map<FilePath, Sha> index = new LinkedHashMap<>();
            for (String line : Files.readAllLines(INDEX_PATH)) {
                String[] parts = line.split(" ", 2);
                if (parts.length == 2) {
                    index.put(new FilePath(parts[1]), new Sha(parts[0]));
                }
            }
            return index;
        } catch (IOException e) {
            System.err.println("Failed to read index: " + e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    /**
     * Writes the index map back to disk
     * @param index Map of FilePath → Sha
     */
    public static void writeIndex(Map<FilePath, Sha> index) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<FilePath, Sha> entry : index.entrySet()) {
            sb.append(entry.getValue().value()).append(" ").append(entry.getKey().value()).append(System.lineSeparator());
        }
        try {
            Files.writeString(INDEX_PATH, sb.toString());
        } catch (IOException e) {
            System.err.println("Failed to write index: " + e.getMessage());
        }
    }
}

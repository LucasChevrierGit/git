package mini_git.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

public class IgnoreManager {

    private static final Path IGNORE_PATH = Path.of(".minigit", "ignore");

    public static Set<String> loadIgnore() {
        if (!Files.exists(IGNORE_PATH)) {
            return Collections.emptySet();
        }
        try {
            return Files.readAllLines(IGNORE_PATH).stream()
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .collect(Collectors.toSet());
        } catch (IOException e) {
            return Collections.emptySet();
        }
    }

    public static boolean isIgnored(String filePath, Set<String> patterns) {
        for (String pattern : patterns) {
            String regex = pattern
                .replace(".", "\\.")
                .replace("*", ".*");
            if (filePath.matches(regex) || filePath.matches(".*/" + regex)) {
                return true;
            }
        }
        return false;
    }
}

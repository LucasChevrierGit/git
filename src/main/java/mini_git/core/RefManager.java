package mini_git.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class RefManager {

    private static final Path HEAD_PATH = Path.of(".minigit", "HEAD");

    public static String resolveHead() throws IOException {
        if (!Files.exists(HEAD_PATH)) return null;

        String head = Files.readString(HEAD_PATH).trim();
        if (head.startsWith("ref: ")) {
            Path refPath = Path.of(".minigit", head.substring(5));
            if (Files.exists(refPath)) {
                return Files.readString(refPath).trim();
            }
            return null;
        }
        return head;
    }

    public static void updateHead(String commitSha) throws IOException {
        String head = Files.readString(HEAD_PATH).trim();
        if (head.startsWith("ref: ")) {
            Path refPath = Path.of(".minigit", head.substring(5));
            Files.createDirectories(refPath.getParent());
            Files.writeString(refPath, commitSha + "\n");
        } else {
            Files.writeString(HEAD_PATH, commitSha + "\n");
        }
    }

    public static String getRefName() {
        try {
            String head = Files.readString(HEAD_PATH).trim();
            if (head.startsWith("ref: refs/")) {
                return head.substring("ref: refs/".length());
            }
            return head.substring(0, 7);
        } catch (IOException e) {
            return "unknown";
        }
    }
}

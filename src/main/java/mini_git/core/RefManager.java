package mini_git.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class RefManager {

    private static final Path HEAD_PATH = Path.of(".minigit", "HEAD");

    /**
     * Follows HEAD to return the current commit SHA
     * @return the commit SHA, or null if the branch has no commits yet
     * @throws IOException on file read
     */
    public static String resolveHead() throws IOException {
        if (!Files.exists(HEAD_PATH)) return null;

        String head = Files.readString(HEAD_PATH).trim();
        if (head.startsWith("ref: ")) {
            Path refPath = Path.of(".minigit", head.substring("ref: ".length()));
            if (Files.exists(refPath)) {
                return Files.readString(refPath).trim();
            }
            return null;
        }
        return head;
    }

    /**
     * Advances the current branch to the given commit SHA
     * <ul>
     *   <li>If HEAD is a ref → update the ref file</li>
     *   <li>If HEAD is a SHA → update HEAD directly (detached mode)</li>
     * </ul>
     * @param commitSha the new tip commit
     * @throws IOException on file write
     */
    public static void updateHead(String commitSha) throws IOException {
        String head = Files.readString(HEAD_PATH).trim();
        if (head.startsWith("ref: ")) {
            Path refPath = Path.of(".minigit", head.substring("ref: ".length()));
            Files.createDirectories(refPath.getParent());
            Files.writeString(refPath, commitSha + "\n");
        } else {
            Files.writeString(HEAD_PATH, commitSha + "\n");
        }
    }

    /**
     * Returns a human-readable name for the current HEAD
     * @return branch name (e.g. "main"), short SHA in detached-HEAD mode, or "unknown" on error
     */
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

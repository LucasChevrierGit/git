package mini_git.command;

import mini_git.core.ObjectStore;
import mini_git.core.RefManager;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

@CommandLine.Command(name = "commit", description = "Commit staged files locally")
public class CommitCommand implements Runnable {

    private static final String ANSI_GREEN = "\u001B[32m";
    private static final String ANSI_RESET = "\u001B[0m";

    private final Path indexPath = Path.of(".minigit", "index");

    @CommandLine.Parameters(description = "Message for the commit")
    private String message;

    @Override
    public void run() {
        if (!Files.exists(indexPath)) {
            System.err.println("Nothing to commit. Stage files first with: minigit add <files>");
            return;
        }

        List<String> indexLines;
        try {
            indexLines = Files.readAllLines(indexPath);
        } catch (IOException e) {
            System.err.println("Failed to read index: " + e.getMessage());
            return;
        }

        if (indexLines.isEmpty()) {
            System.err.println("Nothing to commit. Stage files first with: minigit add <files>");
            return;
        }

        try {
            StringBuilder treeContent = new StringBuilder();
            for (String line : indexLines) {
                String[] parts = line.split(" ", 2);
                if (parts.length < 2) continue;
                String hash = parts[0];
                String filePath = parts[1];
                treeContent.append("100644 ").append(hash).append(" ").append(filePath).append("\n");
            }

            String treeSha = ObjectStore.writeObject(treeContent.toString());

            String parentSha = RefManager.resolveHead();

            StringBuilder commitContent = new StringBuilder();
            commitContent.append("tree ").append(treeSha).append("\n");
            if (parentSha != null) {
                commitContent.append("parent ").append(parentSha).append("\n");
            }
            commitContent.append("timestamp ").append(Instant.now().getEpochSecond()).append("\n");
            commitContent.append("\n");
            commitContent.append(message).append("\n");

            String commitSha = ObjectStore.writeObject(commitContent.toString());

            RefManager.updateHead(commitSha);

            Files.writeString(indexPath, "");

            System.out.println(ANSI_GREEN + "[" + RefManager.getRefName() + " " + commitSha.substring(0, 7) + "] " + message + ANSI_RESET);
            System.out.println(indexLines.size() + " file(s) committed");

        } catch (Exception e) {
            System.err.println("Commit failed: " + e.getMessage());
        }
    }
}

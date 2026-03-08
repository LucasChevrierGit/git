package mini_git.command;

import mini_git.core.RefManager;
import mini_git.core.RemoteContext;
import mini_git.util.Json;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@CommandLine.Command(name = "push", description = "Push local commits to GitHub")
public class PushCommand implements Runnable {

    private static final String ANSI_GREEN = "\u001B[32m";
    private static final String ANSI_RED = "\u001B[31m";
    private static final String ANSI_RESET = "\u001B[0m";

    private final Path configPath = Path.of(".minigit", "config");
    private final Path objectsDir = Path.of(".minigit", "objects");

    @CommandLine.Option(names = {"--branch"}, description = "Branch to push to", defaultValue = "master")
    private String branch;

    @Override
    public void run() {
        RemoteContext remote;
        try {
            remote = RemoteContext.load(configPath);
        } catch (IllegalStateException e) {
            System.err.println(e.getMessage());
            return;
        }

        String localCommitSha;
        try {
            localCommitSha = RefManager.resolveHead();
        } catch (IOException e) {
            System.err.println("Failed to read HEAD: " + e.getMessage());
            return;
        }

        if (localCommitSha == null) {
            System.err.println("Nothing to push. Commit first with: minigit commit <message>");
            return;
        }

        String commitContent;
        try {
            commitContent = Files.readString(objectsDir.resolve(localCommitSha), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("Failed to read commit object: " + e.getMessage());
            return;
        }

        String treeSha = extractField(commitContent, "tree");
        String commitMessage = extractMessage(commitContent);

        String treeContent;
        try {
            treeContent = Files.readString(objectsDir.resolve(treeSha), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("Failed to read tree object: " + e.getMessage());
            return;
        }

        List<String[]> treeEntries = new ArrayList<>();
        for (String line : treeContent.split("\n")) {
            String[] entryParts = line.split(" ", 3);
            if (entryParts.length == 3) {
                treeEntries.add(entryParts);
            }
        }

        if (treeEntries.isEmpty()) {
            System.err.println("Nothing to push. Empty tree.");
            return;
        }

        try {
            String refResponse = remote.getRef(branch);
            String remoteHeadSha = Json.getString(refResponse, "sha");

            String remoteCommitResponse = remote.getCommit(remoteHeadSha);
            String baseTreeSha = Json.getNestedString(remoteCommitResponse, "tree", "sha");

            List<String> remoteTreeParts = new ArrayList<>();
            int success = 0;
            int failed = 0;

            for (String[] entry : treeEntries) {
                String blobHash = entry[1];
                String filePath = entry[2];

                try {
                    byte[] content = Files.readAllBytes(objectsDir.resolve(blobHash));
                    String encoded = Base64.getEncoder().encodeToString(content);

                    String blobBody = "{\"content\":\"" + encoded + "\",\"encoding\":\"base64\"}";
                    String blobResponse = remote.createBlob(blobBody);
                    String remoteBlobSha = Json.getString(blobResponse, "sha");

                    remoteTreeParts.add("{\"path\":\"" + Json.escape(filePath) + "\",\"mode\":\"100644\",\"type\":\"blob\",\"sha\":\"" + remoteBlobSha + "\"}");
                    System.out.println(ANSI_GREEN + "BLOB " + filePath + ANSI_RESET);
                    success++;
                } catch (Exception e) {
                    System.out.println(ANSI_RED + "FAIL " + filePath + " (" + e.getMessage() + ")" + ANSI_RESET);
                    failed++;
                }
            }

            if (remoteTreeParts.isEmpty()) {
                System.err.println("No blobs created. Nothing to push.");
                return;
            }

            String treeBody = "{\"base_tree\":\"" + baseTreeSha + "\",\"tree\":[" + String.join(",", remoteTreeParts) + "]}";
            String treeResponse = remote.createTree(treeBody);
            String newTreeSha = Json.getString(treeResponse, "sha");

            String commitBody = "{\"message\":\"" + Json.escape(commitMessage) + "\",\"tree\":\"" + newTreeSha + "\",\"parents\":[\"" + remoteHeadSha + "\"]}";
            String newCommitResponse = remote.createCommit(commitBody);
            String newCommitSha = Json.getString(newCommitResponse, "sha");

            String refBody = "{\"sha\":\"" + newCommitSha + "\"}";
            remote.updateRef(branch, refBody);

            System.out.println();
            System.out.println("Push complete: " + success + " files in commit " + newCommitSha.substring(0, 7));
            if (failed > 0) {
                System.out.println(ANSI_RED + failed + " files failed" + ANSI_RESET);
            }

        } catch (Exception e) {
            System.err.println("Push failed: " + e.getMessage());
        }
    }

    private String extractField(String content, String field) {
        for (String line : content.split("\n")) {
            if (line.startsWith(field + " ")) {
                return line.substring(field.length() + 1);
            }
        }
        return null;
    }

    private String extractMessage(String content) {
        int emptyLine = content.indexOf("\n\n");
        if (emptyLine >= 0) {
            return content.substring(emptyLine + 2).trim();
        }
        return "minigit push";
    }
}

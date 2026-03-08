package mini_git.command;

import mini_git.core.IndexManager;
import mini_git.core.RefManager;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

@CommandLine.Command(name = "merge", description = "Merge a fetched remote branch into the working tree")
public class MergeCommand implements Runnable {

    private static final String ANSI_GREEN = "\u001B[32m";
    private static final String ANSI_RED = "\u001B[31m";
    private static final String ANSI_YELLOW = "\u001B[33m";
    private static final String ANSI_RESET = "\u001B[0m";

    @CommandLine.Parameters(description = "Remote ref to merge (e.g. origin/main)")
    private String remoteRef;

    @Override
    public void run() {
        Path root = Path.of(".minigit");
        if (!Files.exists(root)) {
            System.err.println("Not a mini_git repository (no .minigit directory).");
            return;
        }

        String[] parts = remoteRef.split("/", 2);
        if (parts.length != 2 || !parts[0].equals("origin")) {
            System.err.println("Invalid remote ref. Use format: origin/<branch>");
            return;
        }
        String branch = parts[1];

        try {
            Path refPath = Path.of(".minigit", "refs", "remotes", "origin", branch);
            if (!Files.exists(refPath)) {
                System.err.println("Remote ref not found: " + remoteRef);
                System.err.println("Run 'minigit fetch --branch " + branch + "' first.");
                return;
            }

            String commitSha = Files.readString(refPath).trim();

            Path commitPath = Path.of(".minigit", "objects", commitSha);
            if (!Files.exists(commitPath)) {
                System.err.println("Commit object not found: " + commitSha);
                return;
            }

            String commitContent = Files.readString(commitPath);
            String remoteTreeSha = extractTreeSha(commitContent);
            if (remoteTreeSha == null) {
                System.err.println("No tree found in commit " + commitSha.substring(0, 7));
                return;
            }

            // Load remote tree
            Map<String, String> remoteTree = loadTree(remoteTreeSha);
            if (remoteTree == null) {
                System.err.println("Tree object not found: " + remoteTreeSha);
                return;
            }

            // Load local HEAD tree
            Map<String, String> localTree = loadHeadTree();

            // Two-way merge
            Map<String, String> newIndex = new LinkedHashMap<>(localTree);
            int addedCount = 0;
            int skippedCount = 0;
            int conflictCount = 0;

            for (Map.Entry<String, String> entry : remoteTree.entrySet()) {
                String filePath = entry.getKey();
                String remoteHash = entry.getValue();
                String localHash = localTree.get(filePath);

                Path targetFile = Path.of(filePath);
                if (Files.isDirectory(targetFile)) {
                    // Skip entries that collide with existing directories
                    skippedCount++;
                    continue;
                }

                if (localHash == null) {
                    // New file from remote — add it
                    Path blobPath = Path.of(".minigit", "objects", remoteHash);
                    if (!Files.exists(blobPath)) {
                        System.err.println("Warning: blob not found for " + filePath);
                        continue;
                    }
                    byte[] content = Files.readAllBytes(blobPath);
                    if (targetFile.getParent() != null) {
                        Files.createDirectories(targetFile.getParent());
                    }
                    Files.write(targetFile, content);
                    newIndex.put(filePath, remoteHash);
                    addedCount++;
                    System.out.println(ANSI_GREEN + "  A " + filePath + ANSI_RESET);

                } else if (localHash.equals(remoteHash)) {
                    // Same content — skip
                    skippedCount++;

                } else {
                    // Conflict — different content on both sides
                    Path localBlobPath = Path.of(".minigit", "objects", localHash);
                    Path remoteBlobPath = Path.of(".minigit", "objects", remoteHash);
                    if (!Files.exists(localBlobPath) || !Files.exists(remoteBlobPath)) {
                        System.err.println("Warning: blob not found for " + filePath);
                        continue;
                    }
                    byte[] ours = Files.readAllBytes(localBlobPath);
                    byte[] theirs = Files.readAllBytes(remoteBlobPath);
                    writeConflictFile(Path.of(filePath), ours, theirs, remoteRef);
                    conflictCount++;
                    System.out.println(ANSI_RED + "  C " + filePath + ANSI_RESET);
                }
            }

            // Local-only files are already in newIndex and left untouched

            System.out.println();
            if (conflictCount > 0) {
                System.out.println(ANSI_YELLOW + "Merge completed with conflicts." + ANSI_RESET);
                System.out.println(addedCount + " file(s) added, "
                        + skippedCount + " file(s) up to date, "
                        + conflictCount + " file(s) conflicted.");
                System.out.println("Fix the conflicts and then commit the result.");
            } else {
                IndexManager.writeIndex(newIndex);
                RefManager.updateHead(commitSha);
                System.out.println("Merged " + remoteRef + " (" + commitSha.substring(0, 7) + ")");
                System.out.println(addedCount + " file(s) added, "
                        + skippedCount + " file(s) up to date.");
            }

        } catch (IOException e) {
            System.err.println("Merge failed: " + e.getMessage());
        }
    }

    private String extractTreeSha(String commitContent) {
        for (String line : commitContent.split("\n")) {
            if (line.startsWith("tree ")) {
                return line.substring(5).trim();
            }
        }
        return null;
    }

    private Map<String, String> loadTree(String treeSha) throws IOException {
        Path treePath = Path.of(".minigit", "objects", treeSha);
        if (!Files.exists(treePath)) return null;

        Map<String, String> tree = new LinkedHashMap<>();
        for (String line : Files.readAllLines(treePath)) {
            // format: "100644 <hash> <path>"
            String[] parts = line.split(" ", 3);
            if (parts.length == 3) {
                tree.put(parts[2], parts[1]); // path -> hash
            }
        }
        return tree;
    }

    private Map<String, String> loadHeadTree() throws IOException {
        String headSha = RefManager.resolveHead();
        if (headSha == null) return new LinkedHashMap<>();

        Path commitPath = Path.of(".minigit", "objects", headSha);
        if (!Files.exists(commitPath)) return new LinkedHashMap<>();

        String commitContent = Files.readString(commitPath);
        String treeSha = extractTreeSha(commitContent);
        if (treeSha == null) return new LinkedHashMap<>();

        Map<String, String> tree = loadTree(treeSha);
        return tree != null ? tree : new LinkedHashMap<>();
    }

    private void writeConflictFile(Path file, byte[] ours, byte[] theirs, String branch) throws IOException {
        String oursContent = new String(ours);
        String theirsContent = new String(theirs);

        String conflicted = "<<<<<<< HEAD\n"
                + oursContent
                + (oursContent.endsWith("\n") ? "" : "\n")
                + "=======\n"
                + theirsContent
                + (theirsContent.endsWith("\n") ? "" : "\n")
                + ">>>>>>> " + branch + "\n";

        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        Files.writeString(file, conflicted);
    }
}

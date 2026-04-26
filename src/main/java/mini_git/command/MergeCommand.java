package mini_git.command;

import com.github.difflib.DiffUtils;
import com.github.difflib.patch.AbstractDelta;
import com.github.difflib.patch.DeltaType;
import com.github.difflib.patch.Patch;
import mini_git.core.FilePath;
import mini_git.core.IndexManager;
import mini_git.core.RefManager;
import mini_git.core.Sha;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@CommandLine.Command(name = "merge", description = "Merge a fetched remote branch into the working tree")
public class MergeCommand implements Runnable {

    private static final String ANSI_GREEN = "[32m";
    private static final String ANSI_RED = "[31m";
    private static final String ANSI_YELLOW = "[33m";
    private static final String ANSI_RESET = "[0m";

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

            Map<FilePath, Sha> remoteTree = loadTree(remoteTreeSha);
            if (remoteTree == null) {
                System.err.println("Tree object not found: " + remoteTreeSha);
                return;
            }

            Map<FilePath, Sha> localTree = loadHeadTree();

            // Two-way merge
            Map<FilePath, Sha> newIndex = new LinkedHashMap<>(localTree);
            int addedCount = 0;
            int skippedCount = 0;
            int conflictCount = 0;

            for (Map.Entry<FilePath, Sha> entry : remoteTree.entrySet()) {
                FilePath filePath = entry.getKey();
                Sha remoteHash = entry.getValue();
                Sha localHash = localTree.get(filePath);

                Path targetFile = Path.of(filePath.value());
                if (Files.isDirectory(targetFile)) {
                    skippedCount++;
                    continue;
                }
                Path remoteBlobPath = Path.of(".minigit", "objects", remoteHash.value());
                if (localHash == null) {
                    // New file from remote — add it
                    if (!Files.exists(remoteBlobPath)) {
                        System.err.println("Warning: blob not found for " + filePath.value());
                        continue;
                    }
                    byte[] content = Files.readAllBytes(remoteBlobPath);
                    if (targetFile.getParent() != null) {
                        Files.createDirectories(targetFile.getParent());
                    }
                    Files.write(targetFile, content);
                    newIndex.put(filePath, remoteHash);
                    addedCount++;
                    System.out.println(ANSI_GREEN + "  A " + filePath.value() + ANSI_RESET);

                } else if (localHash.equals(remoteHash)) {
                    // Same content — skip
                    skippedCount++;

                } else {
                    // Conflict — different content on both sides
                    Path localBlobPath = Path.of(".minigit", "objects", localHash.value());
                    if (!Files.exists(localBlobPath) || !Files.exists(remoteBlobPath)) {
                        System.err.println("Warning: blob not found for " + filePath.value());
                        continue;
                    }

                    byte[] ours = Files.readAllBytes(localBlobPath);
                    byte[] theirs = Files.readAllBytes(remoteBlobPath);

                    Sha commonAncestor = commonAncestor(new Sha(RefManager.resolveHead()), new Sha(commitSha));
                    if (commonAncestor != null) {
                        byte[] base = getFileFromCommit(commonAncestor, filePath);
                        writeConflictFile(targetFile, base, ours, theirs, remoteRef);
                    }
                    else{
                        System.err.println("No common ancestor found for");
                        writeConflictFile(targetFile, ours, theirs, remoteRef);
                    }

                    conflictCount++;
                    System.out.println(ANSI_RED + "  C " + filePath.value() + ANSI_RESET);
                }
            }

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


    private Set<Sha> getAllAncestors(Sha startCommit) throws IOException {
        Set<Sha> visited = new HashSet<>();
        Sha current = startCommit;

        while (current != null) {
            Path objectPath = Path.of(".minigit", "objects", current.value());
            if (!Files.exists(objectPath)) return visited;
            String content = Files.readString(objectPath);

            Sha parent = null;
            for (String line : content.split("\n")) {
                if (line.startsWith("parent ")) {
                    parent = new Sha(line.split(" ")[1]);
                    visited.add(parent);
                    break;
                }
            }
            current = parent;
        }
        return visited;
    }


    private Sha commonAncestor(Sha local, Sha remote) throws IOException {
        Set<Sha> localHistory = getAllAncestors(local);
        localHistory.add(local);

        Sha current = remote;

        if (localHistory.contains(current)) return current;

        while (current != null) {
            Path objectPath = Path.of(".minigit", "objects", current.value());
            if (!Files.exists(objectPath)) return null;
            String content = Files.readString(objectPath);

            Sha parent = null;
            for (String line : content.split("\n")) {
                if (line.startsWith("parent ")) {
                    parent = new Sha(line.split(" ")[1]);
                    if (localHistory.contains(parent)) return parent;
                    break;
                }
            }
            current = parent;
        }
        return null;
    }


    private byte[] getFileFromCommit(Sha commit, FilePath filePath) throws IOException {
        Path commitPath = Path.of(".minigit", "objects", commit.value());
        if (!Files.exists(commitPath)) return null;

        String commitContent = Files.readString(commitPath);

        String treeSha = extractTreeSha(commitContent);
        if (treeSha == null) return null;

        Map<FilePath, Sha> tree = loadTree(treeSha);
        if (tree == null) return null;

        Sha blobSha = tree.get(filePath);
        if (blobSha == null) return null;

        Path blobPath = Path.of(".minigit", "objects", blobSha.value());
        if (!Files.exists(blobPath)) return null;

        return Files.readAllBytes(blobPath);

    }

    /**
     * Parses a commit object and returns the tree SHA
     * @param commitContent raw content of the commit object
     * @return the tree SHA, or null if not found
     */
    private String extractTreeSha(String commitContent) {
        for (String line : commitContent.split("\n")) {
            if (line.startsWith("tree ")) {
                return line.substring(5).trim();
            }
        }
        return null;
    }

    /**
     * Reads a tree object and returns a map of file paths to blob SHAs
     * @param treeSha SHA of the tree object to load
     * @return map of FilePath → Sha, or null if the tree object does not exist
     * @throws IOException on file read
     */
    private Map<FilePath, Sha> loadTree(String treeSha) throws IOException {
        Path treePath = Path.of(".minigit", "objects", treeSha);
        if (!Files.exists(treePath)) return null;

        Map<FilePath, Sha> tree = new LinkedHashMap<>();
        for (String line : Files.readAllLines(treePath)) {
            // format: "100644 <hash> <path>"
            String[] parts = line.split(" ", 3);
            if (parts.length == 3) {
                tree.put(new FilePath(parts[2]), new Sha(parts[1]));
            }
        }
        return tree;
    }

    /**
     * Loads the tree of the current HEAD commit
     * <ol>
     *   <li>Resolve HEAD → commit SHA via {@link RefManager#resolveHead()}</li>
     *   <li>Read the commit object to extract the tree SHA (tree, parent, timestamp, message)</li>
     *   <li>Read the tree object → full snapshot of all tracked files at that commit</li>
     * </ol>
     * @return map of FilePath → Sha, or an empty map if HEAD has no commits yet
     * @throws IOException on file read
     */
    private Map<FilePath, Sha> loadHeadTree() throws IOException {
        String headSha = RefManager.resolveHead();
        if (headSha == null) return new LinkedHashMap<>();

        Path commitPath = Path.of(".minigit", "objects", headSha);
        if (!Files.exists(commitPath)) return new LinkedHashMap<>();

        String commitContent = Files.readString(commitPath);
        String treeSha = extractTreeSha(commitContent);
        if (treeSha == null) return new LinkedHashMap<>();

        Map<FilePath, Sha> tree = loadTree(treeSha);
        return tree != null ? tree : new LinkedHashMap<>();
    }

    /**
     * Writes a conflict file with standard diff markers
     * @param file path of the conflicted file to write
     * @param ours local file content
     * @param theirs remote file content
     * @param branch name of the remote branch (used in the closing marker)
     * @throws IOException on file write
     */
    void writeConflictFile(Path file, byte[] ours, byte[] theirs, String branch) throws IOException {
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

  void writeConflictFile(Path file, byte[] base, byte[] ours, byte[] theirs, String branch) throws IOException {
        String baseContent = new String(base);
        String oursContent = new String(ours);
        String theirsContent = new String(theirs);

        List<String> baseLines = List.of(baseContent.split("\n"));
        List<String> oursLines = List.of(oursContent.split("\n"));
        List<String> theirsLines = List.of(theirsContent.split("\n"));

        Patch<String> oursPatch = DiffUtils.diff(baseLines, oursLines);
        Patch<String> theirsPatch = DiffUtils.diff(baseLines, theirsLines);
        StringBuilder output = new StringBuilder();
        int i = 0;
        while (i < baseLines.size()) {
            AbstractDelta<String> ourDelta   = findDeltaAt(oursPatch, i);
            AbstractDelta<String> theirDelta = findDeltaAt(theirsPatch, i);

            if (ourDelta == null && theirDelta == null) {
                output.append(baseLines.get(i)).append("\n");
                i++;
            } else if (ourDelta != null && theirDelta == null) {
                output.append("<<<<<<< HEAD\n");
                for (String line : ourDelta.getTarget().getLines())
                    output.append(line).append("\n");
                output.append("=======\n");
                output.append(">>>>>>> ").append(branch).append("\n");
                i += ourDelta.getSource().size();
            } else if (ourDelta == null && theirDelta != null) {
                output.append("<<<<<<< HEAD\n");
                output.append("=======\n");
                for (String line : theirDelta.getTarget().getLines())
                    output.append(line).append("\n");
                output.append(">>>>>>> ").append(branch).append("\n");
                i += theirDelta.getSource().size();
            } else {
                output.append("<<<<<<< HEAD\n");
                for (String line : ourDelta.getTarget().getLines())
                    output.append(line).append("\n");
                output.append("=======\n");
                for (String line : theirDelta.getTarget().getLines())
                    output.append(line).append("\n");
                output.append(">>>>>>> ").append(branch).append("\n");
                i += Math.max(ourDelta.getSource().size(), theirDelta.getSource().size());
            }
        }
        Files.writeString(file, output.toString());

    }


    private AbstractDelta<String> findDeltaAt(Patch<String> patch, int position){
        List<AbstractDelta<String>> deltas = patch.getDeltas();

        for(AbstractDelta<String> delta: deltas){
            if(delta.getSource().getPosition() == position) return delta;
        }

        return null;
    }
}


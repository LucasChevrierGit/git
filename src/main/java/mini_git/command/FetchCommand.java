package mini_git.command;

import mini_git.core.ObjectStore;
import mini_git.core.RemoteContext;
import mini_git.util.Json;
import picocli.CommandLine;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.Base64;
import java.util.List;

import static mini_git.util.Json.getNestedString;
import static mini_git.util.Json.getString;

@CommandLine.Command(name = "fetch", description = "Fetch updates from remote repository")
public class FetchCommand implements Runnable {

    @CommandLine.Option(names = {"--branch"}, description = "Branch to fetch", defaultValue = "master")
    private String branch;

    private static final String ANSI_GREEN = "\u001B[32m";
    private static final String ANSI_RESET = "\u001B[0m";

    private final Path configPath = Path.of(".minigit", "config");

    @Override
    public void run() {
        RemoteContext remote;
        try {
            remote = RemoteContext.load(configPath);
        } catch (IllegalStateException e) {
            System.err.println(e.getMessage());
            return;
        }
        branch = (branch == null || branch.isEmpty()) ? remote.branch() : branch;

        try {
            String refResponse = remote.getRef(branch);
            String remoteHeadSha = getString(refResponse, "sha");
            System.out.println("Remote HEAD: " + remoteHeadSha.substring(0, 7));

            String commitResponse = remote.getCommit(remoteHeadSha);
            String remoteTreeSha = getNestedString(commitResponse, "tree", "sha");
            String commitMessage = getString(commitResponse, "message");

            String parentSha = extractParentSha(commitResponse);

            String treeResponse = remote.getTree(remoteTreeSha, true);
            List<String> treeEntries = Json.getObjectArray(treeResponse, "tree");

            String localTreeSha = buildLocalTree(treeEntries, remote);
            String localCommitSha = buildLocalCommit(localTreeSha, parentSha, commitMessage);

            Path remoteRefPath = Path.of(".minigit", "refs", "remotes", "origin", branch);
            Files.createDirectories(remoteRefPath.getParent());
            Files.writeString(remoteRefPath, localCommitSha + "\n");

            System.out.println();
            System.out.println("Fetched from " + branch);
            System.out.println("Remote ref: " + localCommitSha.substring(0, 7) + " -> .minigit/refs/remotes/origin/" + branch);

        } catch (Exception e) {
            System.err.println("Fetch failed: " + e.getMessage());
        }
    }

    private String buildLocalCommit(String treeSha, String parentSha, String message) throws Exception {
        StringBuilder content = new StringBuilder();
        content.append("tree ").append(treeSha).append("\n");
        if (parentSha != null) {
            content.append("parent ").append(parentSha).append("\n");
        }
        content.append("timestamp ").append(java.time.Instant.now().getEpochSecond()).append("\n");
        content.append("\n");
        content.append(message).append("\n");
        return ObjectStore.writeObject(content.toString());
    }

    private String buildLocalTree(List<String> treeEntries, RemoteContext remote) throws Exception {
        StringBuilder localTreeContent = new StringBuilder();
        for (String entry : treeEntries) {
            String type = getString(entry, "type");
            if (!"blob".equals(type)) continue;

            String blobPath = getString(entry, "path");
            String remoteBlobSha = getString(entry, "sha");

            String blobResponse = remote.getBlob(remoteBlobSha);
            String base64Content = getString(blobResponse, "content");

            base64Content = base64Content.replace("\\n", "").replace("\n", "").replace("\r", "");
            byte[] rawContent = Base64.getDecoder().decode(base64Content);

            String localBlobSha = ObjectStore.storeBlob(rawContent);

            localTreeContent.append("100644 ").append(localBlobSha).append(" ").append(blobPath).append("\n");
            System.out.println(ANSI_GREEN + "FETCH " + blobPath + ANSI_RESET);
        }
        return ObjectStore.writeObject(localTreeContent.toString());
    }

    private String extractParentSha(String commitResponse) {
        int parentsIdx = commitResponse.indexOf("\"parents\"");
        if (parentsIdx < 0) return null;

        String parentsSection = commitResponse.substring(parentsIdx);
        int bracketStart = parentsSection.indexOf("[");
        int bracketEnd = parentsSection.indexOf("]");
        if (bracketStart >= 0 && bracketEnd > bracketStart) {
            String inner = parentsSection.substring(bracketStart, bracketEnd + 1);
            if (inner.contains("\"sha\"")) {
                return getString(inner, "sha");
            }
        }
        return null;
    }
}

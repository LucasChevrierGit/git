package mini_git;

import picocli.CommandLine;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@CommandLine.Command(name = "push", description = "Push staged files to GitHub")
public class PushCommand implements Runnable {

    private static final String ANSI_GREEN = "\u001B[32m";
    private static final String ANSI_RED = "\u001B[31m";
    private static final String ANSI_RESET = "\u001B[0m";
    private static final String API_BASE = "https://api.github.com/repos/";

    private final Path configPath = Path.of(".minigit", "config");
    private final Path indexPath = Path.of(".minigit", "index");

    @CommandLine.Option(names = {"-m", "--message"}, description = "Commit message", defaultValue = "minigit push")
    private String message;

    @CommandLine.Option(names = {"--branch"}, description = "Branch to push to", defaultValue = "master")
    private String branch;

    @Override
    public void run() {
        Config config = new Config();
        config.load(configPath);

        String url = config.get("remote", "url");
        String token = config.get("remote", "token");

        if (url == null || token == null) {
            System.err.println("No remote configured. Use: minigit remote add origin <url> --token <token>");
            return;
        }

        String[] parts = url.replace("https://github.com/", "").split("/");
        if (parts.length < 2) {
            System.err.println("Invalid remote URL: " + url);
            return;
        }
        String owner = parts[0];
        String repo = parts[1];

        if (!Files.exists(indexPath)) {
            System.err.println("Nothing to push. Stage files first with: minigit add <files>");
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
            System.err.println("Nothing to push. Stage files first with: minigit add <files>");
            return;
        }

        HttpClient client = HttpClient.newHttpClient();
        String apiBase = API_BASE + owner + "/" + repo;

        try {
            // Step 1: Get current HEAD ref
            String refResponse = getJson(client, apiBase + "/git/refs/heads/" + branch, token);
            String headSha = extractJsonValue(refResponse, "sha");

            // Step 2: Get base tree SHA
            String commitResponse = getJson(client, apiBase + "/git/commits/" + headSha, token);
            String baseTreeSha = extractNestedJsonValue(commitResponse, "tree", "sha");

            // Step 3: Create a blob for each staged file
            List<String> treeParts = new ArrayList<>();
            int success = 0;
            int failed = 0;

            for (String line : indexLines) {
                String[] lineParts = line.split(" ", 2);
                if (lineParts.length < 2) continue;

                String filePath = lineParts[1];
                Path file = Path.of(filePath);

                if (!Files.exists(file)) {
                    System.out.println(ANSI_RED + "SKIP " + filePath + " (file not found)" + ANSI_RESET);
                    failed++;
                    continue;
                }

                try {
                    byte[] content = Files.readAllBytes(file);
                    String encoded = Base64.getEncoder().encodeToString(content);

                    String blobBody = "{\"content\":\"" + encoded + "\",\"encoding\":\"base64\"}";
                    String blobResponse = postJson(client, apiBase + "/git/blobs", token, blobBody);
                    String blobSha = extractJsonValue(blobResponse, "sha");

                    treeParts.add("{\"path\":\"" + escapeJson(filePath) + "\",\"mode\":\"100644\",\"type\":\"blob\",\"sha\":\"" + blobSha + "\"}");
                    System.out.println(ANSI_GREEN + "BLOB " + filePath + ANSI_RESET);
                    success++;
                } catch (Exception e) {
                    System.out.println(ANSI_RED + "FAIL " + filePath + " (" + e.getMessage() + ")" + ANSI_RESET);
                    failed++;
                }
            }

            if (treeParts.isEmpty()) {
                System.err.println("No blobs created. Nothing to push.");
                return;
            }

            // Step 4: Create a tree referencing all blobs
            String treeBody = "{\"base_tree\":\"" + baseTreeSha + "\",\"tree\":[" + String.join(",", treeParts) + "]}";
            String treeResponse = postJson(client, apiBase + "/git/trees", token, treeBody);
            String newTreeSha = extractJsonValue(treeResponse, "sha");

            // Step 5: Create a commit
            String commitBody = "{\"message\":\"" + escapeJson(message) + "\",\"tree\":\"" + newTreeSha + "\",\"parents\":[\"" + headSha + "\"]}";
            String newCommitResponse = postJson(client, apiBase + "/git/commits", token, commitBody);
            String newCommitSha = extractJsonValue(newCommitResponse, "sha");

            // Step 6: Update branch ref
            String refBody = "{\"sha\":\"" + newCommitSha + "\"}";
            patchJson(client, apiBase + "/git/refs/heads/" + branch, token, refBody);

            System.out.println();
            System.out.println("Push complete: " + success + " files in commit " + newCommitSha.substring(0, 7));
            if (failed > 0) {
                System.out.println(ANSI_RED + failed + " files failed" + ANSI_RESET);
            }

        } catch (Exception e) {
            System.err.println("Push failed: " + e.getMessage());
        }
    }

    private String getJson(HttpClient client, String url, String token) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github+json")
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("GET " + url + " returned " + response.statusCode() + ": " + response.body());
        }
        return response.body();
    }

    private String postJson(HttpClient client, String url, String token, String body) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github+json")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("POST " + url + " returned " + response.statusCode() + ": " + response.body());
        }
        return response.body();
    }

    private String patchJson(HttpClient client, String url, String token, String body) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github+json")
                .header("Content-Type", "application/json")
                .method("PATCH", HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("PATCH " + url + " returned " + response.statusCode() + ": " + response.body());
        }
        return response.body();
    }

    private String extractJsonValue(String json, String key) {
        String searchKey = "\"" + key + "\"";
        int keyIndex = json.indexOf(searchKey);
        if (keyIndex < 0) {
            throw new RuntimeException("Key \"" + key + "\" not found in JSON response");
        }
        int colonIndex = json.indexOf(":", keyIndex + searchKey.length());
        int start = json.indexOf("\"", colonIndex + 1) + 1;
        int end = json.indexOf("\"", start);
        return json.substring(start, end);
    }

    private String extractNestedJsonValue(String json, String outerKey, String innerKey) {
        String searchKey = "\"" + outerKey + "\"";
        int outerIndex = json.indexOf(searchKey);
        if (outerIndex < 0) {
            throw new RuntimeException("Key \"" + outerKey + "\" not found in JSON response");
        }
        String remaining = json.substring(outerIndex);
        return extractJsonValue(remaining, innerKey);
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
}

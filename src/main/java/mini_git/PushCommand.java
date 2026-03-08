package mini_git;

import picocli.CommandLine;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;

@CommandLine.Command(name = "push", description = "Push staged files to GitHub")
public class PushCommand implements Runnable {

    private static final String ANSI_GREEN = "\u001B[32m";
    private static final String ANSI_RED = "\u001B[31m";
    private static final String ANSI_RESET = "\u001B[0m";

    private final Path configPath = Path.of(".minigit", "config");
    private final Path indexPath = Path.of(".minigit", "index");

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

        // Parse owner/repo from URL like https://github.com/owner/repo
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

                // Check if file already exists on GitHub (need its SHA to update)
                String sha = getFileSha(client, owner, repo, filePath, token);

                String jsonBody;
                if (sha != null) {
                    jsonBody = "{\"message\":\"minigit push: " + filePath + "\","
                            + "\"content\":\"" + encoded + "\","
                            + "\"sha\":\"" + sha + "\"}";
                } else {
                    jsonBody = "{\"message\":\"minigit push: " + filePath + "\","
                            + "\"content\":\"" + encoded + "\"}";
                }

                String apiUrl = "https://api.github.com/repos/" + owner + "/" + repo + "/contents/" + filePath;

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(apiUrl))
                        .header("Authorization", "Bearer " + token)
                        .header("Accept", "application/vnd.github+json")
                        .header("Content-Type", "application/json")
                        .PUT(HttpRequest.BodyPublishers.ofString(jsonBody))
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200 || response.statusCode() == 201) {
                    System.out.println(ANSI_GREEN + "OK   " + filePath + ANSI_RESET);
                    success++;
                } else {
                    System.out.println(ANSI_RED + "FAIL " + filePath + " (" + response.statusCode() + ")" + ANSI_RESET);
                    failed++;
                }

            } catch (Exception e) {
                System.out.println(ANSI_RED + "FAIL " + filePath + " (" + e.getMessage() + ")" + ANSI_RESET);
                failed++;
            }
        }

        System.out.println();
        System.out.println("Push complete: " + success + " succeeded, " + failed + " failed.");
    }

    private String getFileSha(HttpClient client, String owner, String repo, String path, String token) {
        try {
            String apiUrl = "https://api.github.com/repos/" + owner + "/" + repo + "/contents/" + path;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "application/vnd.github+json")
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                // Extract SHA from JSON response (simple parsing without a JSON library)
                String body = response.body();
                int shaIndex = body.indexOf("\"sha\":");
                if (shaIndex >= 0) {
                    int start = body.indexOf("\"", shaIndex + 6) + 1;
                    int end = body.indexOf("\"", start);
                    return body.substring(start, end);
                }
            }
        } catch (Exception e) {
            // File doesn't exist yet, that's fine
        }
        return null;
    }
}

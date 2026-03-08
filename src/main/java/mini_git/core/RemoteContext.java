package mini_git.core;

import mini_git.util.Config;
import mini_git.util.GitHubHttpClient;

import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.file.Path;

public class RemoteContext {
    private final String owner;
    private final String repo;
    private final String token;
    private final String branch;
    private final HttpClient client;
    private final String apiBase;

    private RemoteContext(String owner, String repo, String token, String branch, HttpClient client, String apiBase) {
        this.owner = owner;
        this.repo = repo;
        this.token = token;
        this.branch = branch;
        this.client = client;
        this.apiBase = apiBase;
    }

    public static RemoteContext load(Path configPath) {
        Config config = new Config();
        config.load(configPath);

        String url = config.get("remote", "url");
        String token = config.get("remote", "token");

        if (url == null || token == null) {
            throw new IllegalStateException("No remote configured. Use: minigit remote add origin <url> --token <token>");
        }

        String[] parts = url.replace("https://github.com/", "").split("/");
        if (parts.length < 2) {
            throw new IllegalStateException("Invalid remote URL: " + url);
        }
        String owner = parts[0];
        String repo = parts[1].replaceAll("\\.git$", "");

        String branch = config.get("remote", "branch");

        HttpClient client = HttpClient.newHttpClient();
        String apiBase = "https://api.github.com/repos/" + owner + "/" + repo;

        return new RemoteContext(owner, repo, token, branch, client, apiBase);
    }

    // --- Git References ---

    public String getRef(String branch) throws IOException, InterruptedException {
        return GitHubHttpClient.get(client, apiBase + "/git/refs/heads/" + branch, token);
    }

    public String updateRef(String branch, String body) throws IOException, InterruptedException {
        return GitHubHttpClient.patch(client, apiBase + "/git/refs/heads/" + branch, token, body);
    }

    // --- Git Commits ---

    public String getCommit(String sha) throws IOException, InterruptedException {
        return GitHubHttpClient.get(client, apiBase + "/git/commits/" + sha, token);
    }

    public String createCommit(String body) throws IOException, InterruptedException {
        return GitHubHttpClient.post(client, apiBase + "/git/commits", token, body);
    }

    // --- Git Trees ---

    public String getTree(String sha, boolean recursive) throws IOException, InterruptedException {
        String url = apiBase + "/git/trees/" + sha;
        if (recursive) url += "?recursive=1";
        return GitHubHttpClient.get(client, url, token);
    }

    public String createTree(String body) throws IOException, InterruptedException {
        return GitHubHttpClient.post(client, apiBase + "/git/trees", token, body);
    }

    // --- Git Blobs ---

    public String getBlob(String sha) throws IOException, InterruptedException {
        return GitHubHttpClient.get(client, apiBase + "/git/blobs/" + sha, token);
    }

    public String createBlob(String body) throws IOException, InterruptedException {
        return GitHubHttpClient.post(client, apiBase + "/git/blobs", token, body);
    }

    // --- Field accessors ---

    public String owner() { return owner; }
    public String repo() { return repo; }
    public String token() { return token; }
    public String branch() { return branch; }
    public HttpClient client() { return client; }
    public String apiBase() { return apiBase; }
}

package mini_git.util;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * HTTP transport layer for the GitHub API.
 *
 * <p>Provides convenience methods for GET, POST, and PATCH requests
 * authenticated with a Bearer token. Every method validates the response
 * status code and throws {@link IOException} on non-2xx responses.
 */
public class GitHubHttpClient {

    /**
     * Sends an authenticated HTTP GET request and returns the response body.
     *
     * @param client the {@link HttpClient} to use
     * @param url    the target URL
     * @param token  GitHub personal-access token (sent as Bearer)
     * @return the response body as a string
     * @throws IOException          if the request fails or returns a non-2xx status
     * @throws InterruptedException if the request is interrupted
     */
    public static String get(HttpClient client, String url, String token) throws IOException, InterruptedException {
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

    /**
     * Sends an authenticated HTTP POST request with a JSON body and returns the response body.
     *
     * @param client the {@link HttpClient} to use
     * @param url    the target URL
     * @param token  GitHub personal-access token (sent as Bearer)
     * @param body   JSON request body
     * @return the response body as a string
     * @throws IOException          if the request fails or returns a non-2xx status
     * @throws InterruptedException if the request is interrupted
     */
    public static String post(HttpClient client, String url, String token, String body) throws IOException, InterruptedException {
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

    /**
     * Sends an authenticated HTTP PATCH request with a JSON body and returns the response body.
     *
     * @param client the {@link HttpClient} to use
     * @param url    the target URL
     * @param token  GitHub personal-access token (sent as Bearer)
     * @param body   JSON request body
     * @return the response body as a string
     * @throws IOException          if the request fails or returns a non-2xx status
     * @throws InterruptedException if the request is interrupted
     */
    public static String patch(HttpClient client, String url, String token, String body) throws IOException, InterruptedException {
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
}


package com.varnyra.pterodactylpush;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;

public class PterodactylClient {

    private static final String ACCEPT =
            "Application/vnd.pterodactyl.v1+json";

    private final HttpClient client;
    private final ObjectMapper mapper;

    private final String panelUrl;
    private final String serverId;
    private final String token;

    public PterodactylClient(
            String panelUrl,
            String serverId,
            String token
    ) {
        this.panelUrl = panelUrl.replaceAll("/+$", "");
        this.serverId = serverId;
        this.token = token;

        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();

        this.mapper = new ObjectMapper();
    }

    private HttpRequest.Builder authorizedRequest(String endpoint) {
        return HttpRequest.newBuilder()
                .uri(URI.create(panelUrl + endpoint))
                .header("Authorization", "Bearer " + token)
                .header("Accept", ACCEPT)
                .timeout(Duration.ofMinutes(5));
    }

    private String serverEndpoint(String endpoint) {
        return "/api/client/servers/"
                + serverId + "/files/" + endpoint;
    }

    public void createDirectory(String directory)
            throws IOException, InterruptedException {

        String parent = directory.substring(
                0, directory.lastIndexOf('/')
        );

        if (parent.isEmpty()) {
            parent = "/";
        }

        String name = directory.substring(
                directory.lastIndexOf('/') + 1
        );

        if (name.isEmpty()) {
            return;
        }

        String json = mapper.writeValueAsString(
                java.util.Map.of(
                        "name", name,
                        "root", parent
                )
        );

        HttpRequest request = authorizedRequest(
                serverEndpoint("create-folder")
        )
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = client.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );

        checkResponse(response.statusCode(), response.body());
    }

    public String getUploadUrl()
            throws IOException, InterruptedException {

        HttpRequest request = authorizedRequest(
                serverEndpoint("upload")
        )
                .GET()
                .build();

        HttpResponse<String> response = client.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );

        checkResponse(response.statusCode(), response.body());

        JsonNode json = mapper.readTree(response.body());

        String url = json.path("attributes")
                .path("url")
                .asText(null);

        if (url == null || url.isBlank()) {
            throw new IOException(
                    "Pterodactyl did not return a signed upload URL."
            );
        }

        URI uri = URI.create(url);

        if (!"https".equalsIgnoreCase(uri.getScheme())
                && !"http".equalsIgnoreCase(uri.getScheme())) {
            throw new IOException("Invalid upload URL scheme.");
        }

        return url;
    }

    public void uploadFile(
            Path file,
            String remoteDirectory
    ) throws IOException, InterruptedException {

        String signedUrl = getUploadUrl();

        String boundary = "----PterodactylPush"
                + UUID.randomUUID().toString().replace("-", "");

        String filename = file.getFileName().toString();

        String safeFilename = filename
                .replace("\\", "_")
                .replace("\"", "_")
                .replace("\r", "_")
                .replace("\n", "_");

        String normalizedDirectory = remoteDirectory.replace("\\", "/");

        if (!normalizedDirectory.startsWith("/")) {
            normalizedDirectory = "/" + normalizedDirectory;
        }

        if (!normalizedDirectory.endsWith("/")) {
            normalizedDirectory += "/";
        }

        String directoryField =
                "--" + boundary + "\r\n"
                        + "Content-Disposition: form-data; name=\"directory\"\r\n\r\n"
                        + normalizedDirectory + "\r\n";

        String fileHeader =
                "--" + boundary + "\r\n"
                        + "Content-Disposition: form-data; name=\"files\"; filename=\""
                        + safeFilename + "\"\r\n"
                        + "Content-Type: application/octet-stream\r\n\r\n";

        String footer = "\r\n--" + boundary + "--\r\n";

        HttpRequest.BodyPublisher body =
                HttpRequest.BodyPublishers.concat(
                        HttpRequest.BodyPublishers.ofString(
                                directoryField + fileHeader
                        ),
                        HttpRequest.BodyPublishers.ofFile(file),
                        HttpRequest.BodyPublishers.ofString(footer)
                );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(signedUrl))
                .timeout(Duration.ofMinutes(10))
                .header(
                        "Content-Type",
                        "multipart/form-data; boundary=" + boundary
                )
                .POST(body)
                .build();

        HttpResponse<String> response = client.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );

        checkResponse(response.statusCode(), response.body());
    }

    private void checkResponse(int status, String body)
            throws IOException {

        if (status < 200 || status >= 300) {
            throw new IOException(
                    "Pterodactyl API returned HTTP "
                            + status + ": " + body
            );
        }
    }


    public void ensureDirectory(String directory)
            throws IOException, InterruptedException {

        String normalized = directory.replace("\\", "/");

        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }

        String current = "";

        for (String part : normalized.split("/")) {
            if (part.isBlank()) {
                continue;
            }

            current += "/" + part;

            if (!directoryExists(current)) {
                createDirectory(current);
            }
        }
    }

    private boolean directoryExists(String directory)
            throws IOException, InterruptedException {

        int separator = directory.lastIndexOf('/');
        String parent = separator <= 0
                ? "/"
                : directory.substring(0, separator);

        String name = directory.substring(separator + 1);

        String endpoint = serverEndpoint("list")
                + "?directory="
                + java.net.URLEncoder.encode(
                parent, StandardCharsets.UTF_8
        );

        HttpRequest request = authorizedRequest(endpoint)
                .GET()
                .build();

        HttpResponse<String> response = client.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );

        checkResponse(response.statusCode(), response.body());

        JsonNode data = mapper.readTree(response.body()).path("data");

        for (JsonNode entry : data) {
            JsonNode attributes = entry.path("attributes");

            if (name.equals(attributes.path("name").asText())
                    && !attributes.path("is_file").asBoolean()) {
                return true;
            }
        }

        return false;
    }
}

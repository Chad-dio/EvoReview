package com.evoreview.context.acquisition;

import com.evoreview.context.ContextProperties;
import com.evoreview.github.GitHubAppClient;
import com.evoreview.github.GitHubProperties;
import org.kohsuke.github.GHFileNotFoundException;
import org.kohsuke.github.GHRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * One-request-per-PR head snapshot via the zipball endpoint, cached on disk by
 * head SHA. Falls back to lazy contents-API access when the archive fails the
 * size guards or cannot be downloaded. Old-side (merge-base) files are always
 * materialized lazily through the contents API.
 */
@Component
public class GitHubRepoSnapshotProvider implements RepoSnapshotProvider {

    private static final Logger log = LoggerFactory.getLogger(GitHubRepoSnapshotProvider.class);
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    private final GitHubAppClient gitHubAppClient;
    private final GitHubProperties gitHubProperties;
    private final ContextProperties contextProperties;

    public GitHubRepoSnapshotProvider(
            GitHubAppClient gitHubAppClient,
            GitHubProperties gitHubProperties,
            ContextProperties contextProperties
    ) {
        this.gitHubAppClient = gitHubAppClient;
        this.gitHubProperties = gitHubProperties;
        this.contextProperties = contextProperties;
    }

    @Override
    public RepoSnapshot openHeadSnapshot(long installationId, String owner, String repo, String headSha)
            throws IOException {
        Path cacheDir = contextProperties.resolvedSnapshotCacheDir().resolve("head-" + headSha);
        if (Files.isDirectory(cacheDir)) {
            return new ArchiveRepoSnapshot(cacheDir);
        }
        String token = gitHubAppClient.createInstallationToken(installationId);
        try {
            downloadArchive(token, owner, repo, headSha, cacheDir);
            return new ArchiveRepoSnapshot(cacheDir);
        } catch (SnapshotGuardException ex) {
            log.warn("Archive for {}/{}@{} rejected by guard ({}), falling back to contents API",
                    owner, repo, headSha, ex.getMessage());
        } catch (IOException ex) {
            log.warn("Archive download failed for {}/{}@{} ({}), falling back to contents API",
                    owner, repo, headSha, ex.getMessage());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("Archive download interrupted for {}/{}@{}, falling back to contents API",
                    owner, repo, headSha);
        }
        deleteQuietly(cacheDir);
        GHRepository repository =
                gitHubAppClient.loginAsInstallation(installationId).getRepository(owner + "/" + repo);
        return new ContentsApiRepoSnapshot(repository, headSha);
    }

    @Override
    public Optional<String> mergeBaseFileContent(
            long installationId, String owner, String repo, String mergeBaseSha, String path)
            throws IOException {
        GHRepository repository =
                gitHubAppClient.loginAsInstallation(installationId).getRepository(owner + "/" + repo);
        try {
            return Optional.of(repository.getFileContent(path, mergeBaseSha).getContent());
        } catch (GHFileNotFoundException ex) {
            return Optional.empty();
        }
    }

    private void downloadArchive(String token, String owner, String repo, String headSha, Path targetDir)
            throws IOException, InterruptedException {
        String url = gitHubProperties.getApiBaseUrl()
                + "/repos/" + owner + "/" + repo + "/zipball/" + headSha;
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .timeout(Duration.ofSeconds(120))
                .GET()
                .build();
        HttpResponse<InputStream> response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            throw new IOException("zipball endpoint returned HTTP " + response.statusCode());
        }
        try (InputStream body = response.body()) {
            ZipSnapshots.extract(
                    body,
                    targetDir,
                    contextProperties.getMaxFileKb() * 1024,
                    contextProperties.getMaxSnapshotMb() * 1024 * 1024);
        }
    }

    private static void deleteQuietly(Path dir) {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // best effort cleanup of a partially extracted archive
                }
            });
        } catch (IOException ignored) {
            // best effort cleanup
        }
    }
}

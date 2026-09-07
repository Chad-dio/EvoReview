package com.evoreview.context.acquisition;

import com.evoreview.context.ContextProperties;
import com.evoreview.github.GitHubAppClient;
import com.evoreview.github.GitHubProperties;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kohsuke.github.GHContent;
import org.kohsuke.github.GHFileNotFoundException;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHTree;
import org.kohsuke.github.GHTreeEntry;
import org.kohsuke.github.GitHub;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@WireMockTest
class GitHubRepoSnapshotProviderTest {

    @TempDir
    Path tempDir;

    private GitHubAppClient appClient;
    private GitHubRepoSnapshotProvider provider;
    private Path cacheDir;

    @BeforeEach
    void setUp(WireMockRuntimeInfo wm) throws Exception {
        appClient = mock(GitHubAppClient.class);
        when(appClient.createInstallationToken(7L)).thenReturn("test-token");

        GitHubProperties gitHubProperties = new GitHubProperties();
        gitHubProperties.setApiBaseUrl(wm.getHttpBaseUrl());

        ContextProperties contextProperties = new ContextProperties();
        cacheDir = tempDir.resolve("snapshots");
        contextProperties.setSnapshotCacheDir(cacheDir.toString());

        provider = new GitHubRepoSnapshotProvider(appClient, gitHubProperties, contextProperties);
    }

    @Test
    void downloadsArchiveOnceAndReusesDiskCache(WireMockRuntimeInfo wm) throws Exception {
        byte[] zip = ZipSnapshotsTest.zipOf(Map.of(
                "o-r-abc123/README.md", "hi",
                "o-r-abc123/src/A.java", "class A {}"));
        stubFor(get(urlEqualTo("/repos/o/r/zipball/abc123"))
                .withHeader("Authorization", equalTo("Bearer test-token"))
                .willReturn(aResponse().withStatus(200).withBody(zip)));

        RepoSnapshot snapshot = provider.openHeadSnapshot(7L, "o", "r", "abc123");

        assertThat(snapshot.kind()).isEqualTo(RepoSnapshot.Kind.ARCHIVE_FULL);
        assertThat(snapshot.readFile("src/A.java")).contains("class A {}");
        assertThat(snapshot.readFile("missing.java")).isEmpty();
        assertThat(snapshot.listFiles()).containsExactly("README.md", "src/A.java");

        wm.getWireMock().resetMappings();

        RepoSnapshot cached = provider.openHeadSnapshot(7L, "o", "r", "abc123");
        assertThat(cached.kind()).isEqualTo(RepoSnapshot.Kind.ARCHIVE_FULL);
        assertThat(cached.readFile("README.md")).contains("hi");
    }

    @Test
    void fallsBackToContentsApiWhenArchiveUnavailable() throws Exception {
        stubFor(get(urlEqualTo("/repos/o/r/zipball/deadbeef"))
                .willReturn(aResponse().withStatus(404)));
        mockContentsApi("deadbeef");

        RepoSnapshot snapshot = provider.openHeadSnapshot(7L, "o", "r", "deadbeef");

        assertThat(snapshot.kind()).isEqualTo(RepoSnapshot.Kind.CONTENTS_LAZY);
        assertThat(snapshot.readFile("src/A.java")).contains("class A {}");
        assertThat(snapshot.listFiles()).containsExactly("src/A.java");
    }

    @Test
    void fallsBackAndCleansUpWhenArchiveTripsGuard(WireMockRuntimeInfo wm) throws Exception {
        ContextProperties tinyLimits = new ContextProperties();
        tinyLimits.setSnapshotCacheDir(cacheDir.toString());
        tinyLimits.setMaxFileKb(1);
        GitHubProperties gitHubProperties = new GitHubProperties();
        gitHubProperties.setApiBaseUrl(wm.getHttpBaseUrl());
        provider = new GitHubRepoSnapshotProvider(appClient, gitHubProperties, tinyLimits);

        byte[] zip = ZipSnapshotsTest.zipOf(Map.of(
                "o-r-bad/big.txt", "x".repeat(4096)));
        stubFor(get(urlEqualTo("/repos/o/r/zipball/bad"))
                .willReturn(aResponse().withStatus(200).withBody(zip)));
        mockContentsApi("bad");

        RepoSnapshot snapshot = provider.openHeadSnapshot(7L, "o", "r", "bad");

        assertThat(snapshot.kind()).isEqualTo(RepoSnapshot.Kind.CONTENTS_LAZY);
        assertThat(Files.isDirectory(cacheDir.resolve("head-bad"))).isFalse();
    }

    @Test
    void materializesMergeBaseFilesLazily() throws Exception {
        GitHub gitHub = mock(GitHub.class);
        GHRepository repository = mock(GHRepository.class);
        when(appClient.loginAsInstallation(7L)).thenReturn(gitHub);
        when(gitHub.getRepository("o/r")).thenReturn(repository);
        GHContent old = mock(GHContent.class);
        when(old.getContent()).thenReturn("old content");
        when(repository.getFileContent("src/Old.java", "mb1")).thenReturn(old);
        when(repository.getFileContent("src/Gone.java", "mb1"))
                .thenThrow(new GHFileNotFoundException("gone"));

        Optional<String> found = provider.mergeBaseFileContent(7L, "o", "r", "mb1", "src/Old.java");
        Optional<String> missing = provider.mergeBaseFileContent(7L, "o", "r", "mb1", "src/Gone.java");

        assertThat(found).contains("old content");
        assertThat(missing).isEmpty();
    }

    private void mockContentsApi(String ref) throws Exception {
        GitHub gitHub = mock(GitHub.class);
        GHRepository repository = mock(GHRepository.class);
        when(appClient.loginAsInstallation(7L)).thenReturn(gitHub);
        when(gitHub.getRepository("o/r")).thenReturn(repository);

        GHContent content = mock(GHContent.class);
        when(content.getContent()).thenReturn("class A {}");
        when(repository.getFileContent("src/A.java", ref)).thenReturn(content);

        GHTreeEntry entry = mock(GHTreeEntry.class);
        when(entry.getType()).thenReturn("blob");
        when(entry.getPath()).thenReturn("src/A.java");
        GHTree tree = mock(GHTree.class);
        when(tree.getTree()).thenReturn(List.of(entry));
        when(repository.getTreeRecursive(ref, 1)).thenReturn(tree);
    }
}

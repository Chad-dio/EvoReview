package com.evoreview.context.acquisition;

import com.evoreview.context.model.ChangeType;
import org.junit.jupiter.api.Test;
import org.kohsuke.github.GHCommitPointer;
import org.kohsuke.github.GHCompare;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHPullRequestFileDetail;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.PagedIterable;
import org.kohsuke.github.PagedIterator;

import java.util.Iterator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GitHubPatchProviderTest {

    private final GitHubPatchProvider provider = new GitHubPatchProvider();

    @Test
    void freezesRevisionTripleAndMapsFiles() throws Exception {
        GHCommitPointer base = mock(GHCommitPointer.class);
        when(base.getSha()).thenReturn("base1");
        GHCommitPointer head = mock(GHCommitPointer.class);
        when(head.getSha()).thenReturn("head1");

        GHCompare.Commit mergeBase = mock(GHCompare.Commit.class);
        when(mergeBase.getSHA1()).thenReturn("mb1");
        GHCompare compare = mock(GHCompare.class);
        when(compare.getMergeBaseCommit()).thenReturn(mergeBase);

        GHRepository repository = mock(GHRepository.class);
        when(repository.getFullName()).thenReturn("octo/repo");
        when(repository.getCompare("base1", "head1")).thenReturn(compare);

        GHPullRequestFileDetail modified = file("src/A.java", null, "modified", "@@ -1 +1 @@\n-a\n+b\n", 1, 1);
        GHPullRequestFileDetail renamed = file("src/B.java", "src/OldB.java", "renamed", "@@ -1 +1 @@\n-x\n+y\n", 1, 1);

        PagedIterable<GHPullRequestFileDetail> paged = mockPagedIterable(List.of(modified, renamed));

        GHPullRequest pullRequest = mock(GHPullRequest.class);
        when(pullRequest.getBase()).thenReturn(base);
        when(pullRequest.getHead()).thenReturn(head);
        when(pullRequest.getRepository()).thenReturn(repository);
        when(pullRequest.getNumber()).thenReturn(42);
        when(pullRequest.listFiles()).thenReturn(paged);

        PatchAcquisition acquisition = provider.fetch(pullRequest);

        assertThat(acquisition.revision().repoId()).isEqualTo("octo/repo");
        assertThat(acquisition.revision().prNumber()).isEqualTo(42);
        assertThat(acquisition.revision().baseSha()).isEqualTo("base1");
        assertThat(acquisition.revision().mergeBaseSha()).isEqualTo("mb1");
        assertThat(acquisition.revision().headSha()).isEqualTo("head1");
        assertThat(acquisition.revision().patchSha()).hasSize(64);

        List<RawFilePatch> files = acquisition.patch().files();
        assertThat(files).hasSize(2);
        assertThat(files.get(0).changeType()).isEqualTo(ChangeType.MODIFY);
        assertThat(files.get(1).changeType()).isEqualTo(ChangeType.RENAME);
        assertThat(files.get(1).previousPath()).isEqualTo("src/OldB.java");
    }

    @Test
    void mapsGitHubStatuses() {
        assertThat(GitHubPatchProvider.mapStatus("added")).isEqualTo(ChangeType.ADD);
        assertThat(GitHubPatchProvider.mapStatus("modified")).isEqualTo(ChangeType.MODIFY);
        assertThat(GitHubPatchProvider.mapStatus("removed")).isEqualTo(ChangeType.DELETE);
        assertThat(GitHubPatchProvider.mapStatus("renamed")).isEqualTo(ChangeType.RENAME);
        assertThat(GitHubPatchProvider.mapStatus("copied")).isEqualTo(ChangeType.MODIFY);
        assertThat(GitHubPatchProvider.mapStatus(null)).isEqualTo(ChangeType.MODIFY);
    }

    private static GHPullRequestFileDetail file(
            String name, String previous, String status, String patch, int additions, int deletions) {
        GHPullRequestFileDetail file = mock(GHPullRequestFileDetail.class);
        when(file.getFilename()).thenReturn(name);
        when(file.getPreviousFilename()).thenReturn(previous);
        when(file.getStatus()).thenReturn(status);
        when(file.getPatch()).thenReturn(patch);
        when(file.getAdditions()).thenReturn(additions);
        when(file.getDeletions()).thenReturn(deletions);
        return file;
    }

    @SuppressWarnings("unchecked")
    private static PagedIterable<GHPullRequestFileDetail> mockPagedIterable(
            List<GHPullRequestFileDetail> files) {
        Iterator<GHPullRequestFileDetail> delegate = files.iterator();
        PagedIterator<GHPullRequestFileDetail> iterator = mock(PagedIterator.class);
        when(iterator.hasNext()).thenAnswer(invocation -> delegate.hasNext());
        when(iterator.next()).thenAnswer(invocation -> delegate.next());
        PagedIterable<GHPullRequestFileDetail> paged = mock(PagedIterable.class);
        when(paged.iterator()).thenReturn(iterator);
        return paged;
    }
}

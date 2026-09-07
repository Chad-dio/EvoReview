package com.evoreview.context.acquisition;

import com.evoreview.context.ContextIds;
import com.evoreview.context.model.ChangeType;
import com.evoreview.context.model.RevisionSpec;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHPullRequestFileDetail;
import org.kohsuke.github.GHRepository;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Fetches the PR's file list with unified-diff patches and freezes the revision
 * triple (base / merge-base / head). The merge base comes from the compare API
 * because GitHub PR diffs use three-dot semantics: base.sha is the moving tip
 * of the target branch, not a valid old-side revision.
 */
@Component
public class GitHubPatchProvider implements PatchProvider {

    @Override
    public PatchAcquisition fetch(GHPullRequest pullRequest) throws IOException {
        String baseSha = pullRequest.getBase().getSha();
        String headSha = pullRequest.getHead().getSha();
        GHRepository repository = pullRequest.getRepository();
        String mergeBaseSha = repository.getCompare(baseSha, headSha).getMergeBaseCommit().getSHA1();

        List<RawFilePatch> files = new ArrayList<>();
        for (GHPullRequestFileDetail file : pullRequest.listFiles()) {
            files.add(new RawFilePatch(
                    file.getFilename(),
                    file.getPreviousFilename(),
                    mapStatus(file.getStatus()),
                    file.getPatch(),
                    file.getAdditions(),
                    file.getDeletions()
            ));
        }

        String repoId = repository.getFullName();
        int prNumber = pullRequest.getNumber();
        RevisionSpec revision = new RevisionSpec(
                repoId, prNumber, baseSha, mergeBaseSha, headSha, ContextIds.patchSha(files));
        return new PatchAcquisition(revision, new RawPatch(repoId, prNumber, files));
    }

    static ChangeType mapStatus(String status) {
        if (status == null) {
            return ChangeType.MODIFY;
        }
        return switch (status) {
            case "added" -> ChangeType.ADD;
            case "removed" -> ChangeType.DELETE;
            case "renamed" -> ChangeType.RENAME;
            default -> ChangeType.MODIFY;
        };
    }
}

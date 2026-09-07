package com.evoreview.context.acquisition;

import java.io.IOException;
import java.util.Optional;

public interface RepoSnapshotProvider {

    RepoSnapshot openHeadSnapshot(long installationId, String owner, String repo, String headSha)
            throws IOException;

    Optional<String> mergeBaseFileContent(
            long installationId, String owner, String repo, String mergeBaseSha, String path)
            throws IOException;
}

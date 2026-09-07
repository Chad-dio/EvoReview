package com.evoreview.context.acquisition;

import org.kohsuke.github.GHPullRequest;

import java.io.IOException;

public interface PatchProvider {

    PatchAcquisition fetch(GHPullRequest pullRequest) throws IOException;
}

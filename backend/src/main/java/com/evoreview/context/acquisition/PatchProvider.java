package com.evoreview.context.acquisition;

import java.io.IOException;

public interface PatchProvider {

    PatchAcquisition fetch(long installationId, String owner, String repo, int prNumber)
            throws IOException;
}

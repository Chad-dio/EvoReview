package com.evoreview.context.rank;

import com.evoreview.context.model.ContextItem;

/**
 * Versioned selection policy. The version string is part of ContextId, so two
 * policies over the same corpus produce distinct, replayable contexts.
 */
public interface ContextPolicy {

    String version();

    double score(ContextItem candidate);
}

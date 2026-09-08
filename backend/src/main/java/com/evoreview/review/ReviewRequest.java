package com.evoreview.review;

import java.util.List;

public record ReviewRequest(
        String contextId,
        String sliceId,
        String repo,
        int prNumber,
        List<ReviewRequestItem> items) {}

package com.evoreview.review;

public record ReviewRequestItem(
        String itemId,
        String kind,
        String path,
        String revision,
        String symbolId,
        Integer startLine,
        Integer endLine,
        boolean required,
        String content) {}

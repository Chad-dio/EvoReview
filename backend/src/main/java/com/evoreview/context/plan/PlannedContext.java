package com.evoreview.context.plan;

import com.evoreview.context.model.ContextItem;

import java.util.List;
import java.util.Map;

/**
 * @param optionalItemOwners itemId → owning changed-file path, used to assign
 *                           related items to the slice of the change they relate to
 * @param sensitiveSkipped   paths whose content was withheld by the sensitive classifier
 */
public record PlannedContext(
        List<ContextItem> items,
        Map<String, String> optionalItemOwners,
        List<String> sensitiveSkipped
) {
    public PlannedContext {
        items = items == null ? List.of() : List.copyOf(items);
        optionalItemOwners = optionalItemOwners == null ? Map.of() : Map.copyOf(optionalItemOwners);
        sensitiveSkipped = sensitiveSkipped == null ? List.of() : List.copyOf(sensitiveSkipped);
    }
}

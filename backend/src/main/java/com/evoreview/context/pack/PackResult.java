package com.evoreview.context.pack;

import com.evoreview.context.model.ContextItem;
import com.evoreview.context.model.DroppedItem;

import java.util.List;

public record PackResult(List<ContextItem> included, List<DroppedItem> dropped, int estimatedTokens) {

    public PackResult {
        included = included == null ? List.of() : List.copyOf(included);
        dropped = dropped == null ? List.of() : List.copyOf(dropped);
    }
}

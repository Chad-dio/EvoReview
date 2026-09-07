package com.evoreview.context.model;

import java.util.Objects;

public record DroppedItem(String itemId, String dropReason) {

    public DroppedItem {
        Objects.requireNonNull(itemId, "itemId");
        Objects.requireNonNull(dropReason, "dropReason");
    }
}

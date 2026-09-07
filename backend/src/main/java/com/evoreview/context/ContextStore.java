package com.evoreview.context;

import com.evoreview.context.model.ContextSnapshot;

import java.util.Optional;

public interface ContextStore {

    Optional<ContextSnapshot> find(String contextId);

    void save(ContextSnapshot snapshot);
}

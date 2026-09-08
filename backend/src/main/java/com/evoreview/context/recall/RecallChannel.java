package com.evoreview.context.recall;

import com.evoreview.context.model.ContextEdge;
import com.evoreview.context.model.RecallSource;

import java.util.List;

public interface RecallChannel {

    RecallSource source();

    List<ContextEdge> recall(RecallInput input);
}

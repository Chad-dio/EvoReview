package com.evoreview.context.semantic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pass-1 output: who calls what, keyed by method name + arity. Built during the
 * same parse pass as declarations so recall never has to guess "plausibly
 * related" files to rescan.
 */
public record InvocationIndex(Map<InvocationKey, List<InvocationSite>> sites) {

    public InvocationIndex {
        Map<InvocationKey, List<InvocationSite>> copy = new LinkedHashMap<>();
        sites.forEach((key, value) -> copy.put(key, List.copyOf(value)));
        sites = Collections.unmodifiableMap(copy);
    }

    public static InvocationIndex empty() {
        return new InvocationIndex(Map.of());
    }

    public List<InvocationSite> lookup(String methodName, int arity) {
        return sites.getOrDefault(new InvocationKey(methodName, arity), List.of());
    }

    public boolean isEmpty() {
        return sites.isEmpty();
    }

    static InvocationIndex of(Map<InvocationKey, List<InvocationSite>> mutable) {
        return new InvocationIndex(mutable);
    }

    static void record(Map<InvocationKey, List<InvocationSite>> sink, InvocationSite site,
                       String methodName, int arity) {
        sink.computeIfAbsent(new InvocationKey(methodName, arity), k -> new ArrayList<>()).add(site);
    }
}

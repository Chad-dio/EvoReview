package com.evoreview.context;

public interface TokenEstimator {

    String version();

    int estimate(String content);
}

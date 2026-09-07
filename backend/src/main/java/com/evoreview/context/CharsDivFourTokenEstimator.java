package com.evoreview.context;

import org.springframework.stereotype.Component;

@Component
public class CharsDivFourTokenEstimator implements TokenEstimator {

    public static final String VERSION = "chars-div-4-v1";

    @Override
    public String version() {
        return VERSION;
    }

    @Override
    public int estimate(String content) {
        if (content == null || content.isEmpty()) {
            return 0;
        }
        return (content.length() + 3) / 4;
    }
}

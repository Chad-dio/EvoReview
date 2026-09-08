package com.evoreview.review;

public class LlmReviewException extends RuntimeException {

    public LlmReviewException(String message) {
        super(message);
    }

    public LlmReviewException(String message, Throwable cause) {
        super(message, cause);
    }
}

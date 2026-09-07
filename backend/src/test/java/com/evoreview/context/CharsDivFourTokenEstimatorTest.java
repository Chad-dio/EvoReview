package com.evoreview.context;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CharsDivFourTokenEstimatorTest {

    private final CharsDivFourTokenEstimator estimator = new CharsDivFourTokenEstimator();

    @Test
    void estimatesByCharsDividedByFourRoundedUp() {
        assertThat(estimator.estimate("")).isZero();
        assertThat(estimator.estimate(null)).isZero();
        assertThat(estimator.estimate("abcd")).isEqualTo(1);
        assertThat(estimator.estimate("abcde")).isEqualTo(2);
    }

    @Test
    void hasAVersionedName() {
        assertThat(estimator.version()).isEqualTo("chars-div-4-v1");
    }
}

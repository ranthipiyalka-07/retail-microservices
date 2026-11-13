package com.retail.common.outbox;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TraceParentsTest {

    @Test
    void buildsAW3cTraceparentAndPadsShortIds() {
        assertThat(TraceParents.format("4bf92f3577b34da6a3ce929d0e0e4736", "00f067aa0ba902b7", true))
                .isEqualTo("00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01");
        assertThat(TraceParents.format("A3CE929D0E0E4736", "b7", false))
                .isEqualTo("00-0000000000000000a3ce929d0e0e4736-00000000000000b7-00");
    }

    @Test
    void anUnknownSamplingDecisionIsNotSampled() {
        assertThat(TraceParents.format("1", "2", null)).endsWith("-00");
    }

    @Test
    void unusableIdsGiveNoTraceparent() {
        assertThat(TraceParents.format(null, "00f067aa0ba902b7", true)).isNull();
        assertThat(TraceParents.format("zz", "00f067aa0ba902b7", true)).isNull();
        assertThat(TraceParents.format("0000", "00f067aa0ba902b7", true)).isNull();
        assertThat(TraceParents.format("4bf92f3577b34da6a3ce929d0e0e4736", "0", true)).isNull();
        assertThat(TraceParents.format("4bf92f3577b34da6a3ce929d0e0e4736".repeat(2), "1", true)).isNull();
    }
}
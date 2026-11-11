package com.retail.support.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TicketMapperTest {

    @Test
    void textKeepsLineBreaksButDropsOtherControlCharactersAndTrims() {
        assertThat(TicketMapper.text("  first\nsecond\u0000\u0007 third\t! ")).isEqualTo("first\nsecond third\t!");
    }

    @Test
    void lineTurnsEveryControlCharacterIntoOneSpace() {
        assertThat(TicketMapper.line("  Charged \n\n twice\u0000 ")).isEqualTo("Charged twice");
    }

    @Test
    void nullBecomesAnEmptyString() {
        assertThat(TicketMapper.text(null)).isEmpty();
        assertThat(TicketMapper.line(null)).isEmpty();
        assertThat(TicketMapper.line("\u0001\u0002 ")).isEmpty();
    }
}
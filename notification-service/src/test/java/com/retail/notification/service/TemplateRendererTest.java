package com.retail.notification.service;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TemplateRendererTest {

    private final TemplateRenderer renderer = new TemplateRenderer();

    @Test
    void replacesPlaceholdersToleratesSpacesAndTurnsUnknownNamesIntoNothing() {
        TemplateRenderer.Rendered result = renderer.render("Order {{ orderNumber }} {{missing}}", "Hello {{name}}!",
                Map.of("orderNumber", "ORD-1", "name", "John"));

        assertThat(result.subject()).isEqualTo("Order ORD-1");
        assertThat(result.body()).isEqualTo("Hello John!");
    }

    @Test
    void valuesWithRegexCharactersAreInsertedAsTyped() {
        TemplateRenderer.Rendered result = renderer.render("S", "Total: {{total}}", Map.of("total", "$5.00 \\ 100%"));

        assertThat(result.body()).isEqualTo("Total: $5.00 \\ 100%");
    }

    @Test
    void aValueCannotAddMailHeadersThroughTheSubject() {
        TemplateRenderer.Rendered result = renderer.render("Re: {{subject}}", "x",
                Map.of("subject", "Hi\r\nBcc: attacker@example.com"));

        assertThat(result.subject()).doesNotContain("\r").doesNotContain("\n");
        assertThat(result.subject()).isEqualTo("Re: Hi Bcc: attacker@example.com");
    }

    @Test
    void theBodyKeepsLineBreaksNormalizesWindowsEndingsAndDropsOtherControlCharacters() {
        TemplateRenderer.Rendered result = renderer.render("S", "a\r\nb\rc\u0000\td", Map.of());

        assertThat(result.body()).isEqualTo("a\nb\nc\td");
    }

    @Test
    void aLongSubjectIsCutToWhatTheDatabaseColumnHolds() {
        TemplateRenderer.Rendered result = renderer.render("{{s}}", "x", Map.of("s", "x".repeat(400)));

        assertThat(result.subject()).hasSize(255);
    }

    @Test
    void placeholdersAreListedInOrderOfFirstUseWithoutDuplicates() {
        assertThat(TemplateRenderer.placeholdersIn("{{b}} text {{a}} {{ b }}")).containsExactly("b", "a");
        assertThat(TemplateRenderer.placeholdersIn(null)).isEmpty();
    }
}
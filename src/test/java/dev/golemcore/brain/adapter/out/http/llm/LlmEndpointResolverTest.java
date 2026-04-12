package dev.golemcore.brain.adapter.out.http.llm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LlmEndpointResolverTest {

    @Test
    void shouldAcceptCustomHttpAndHttpsEndpoints() {
        assertEquals("https://llm.example.internal/custom/v1",
                LlmEndpointResolver.canonicalBaseUrl(
                        "https://llm.example.internal/custom/v1/",
                        "https://api.openai.com/v1"));
        assertEquals("http://llm.example.internal/custom/v1",
                LlmEndpointResolver.canonicalBaseUrl(
                        "http://llm.example.internal/custom/v1/",
                        "https://api.openai.com/v1"));
    }

    @Test
    void shouldKeepCanonicalFallbackForKnownDefaultProviders() {
        assertEquals("https://api.openai.com/v1",
                LlmEndpointResolver.canonicalBaseUrl(null, "https://api.openai.com/v1"));
        assertEquals("https://generativelanguage.googleapis.com/v1beta",
                LlmEndpointResolver.canonicalBaseUrl(null, "https://generativelanguage.googleapis.com/v1beta"));
    }

    @Test
    void shouldCanonicalizeBareKnownProviderEndpoint() {
        assertEquals("https://api.openai.com/v1",
                LlmEndpointResolver.canonicalBaseUrl("https://api.openai.com/", "https://api.openai.com/v1"));
    }

    @Test
    void shouldRejectUnsupportedEndpointScheme() {
        assertThrows(IllegalArgumentException.class,
                () -> LlmEndpointResolver.canonicalBaseUrl(
                        "ftp://llm.example.internal/v1",
                        "https://api.openai.com/v1"));
    }
}

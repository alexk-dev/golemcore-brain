package dev.golemcore.brain.adapter.out.http.llm;

import java.io.IOException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HttpLlmProviderCheckAdapterTest {

    @Test
    void shouldUseReadableFallbackWhenNetworkErrorHasNoMessage() {
        IOException exception = new IOException((String) null);

        assertEquals("Provider check failed: could not reach the provider endpoint",
                HttpLlmProviderCheckAdapter.networkFailureMessage(exception));
    }

    @Test
    void shouldUseReadableCauseWhenNetworkErrorMessageIsEmpty() {
        IOException exception = new IOException("null");
        exception.initCause(new IllegalStateException("connection reset"));

        assertEquals("Provider check failed: connection reset",
                HttpLlmProviderCheckAdapter.networkFailureMessage(exception));
    }

    @Test
    void shouldKeepProviderNetworkErrorDetailsWhenPresent() {
        IOException exception = new IOException("Connection refused");

        assertEquals("Provider check failed: Connection refused",
                HttpLlmProviderCheckAdapter.networkFailureMessage(exception));
    }
}

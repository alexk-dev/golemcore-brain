package dev.golemcore.brain.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SpaIndexControllerTest {

    @Test
    void shouldRenderServletContextBaseHrefIntoIndexHtml() {
        String html = """
                <!doctype html>
                <html lang="en">
                  <head>
                    <base href="/" data-brain-app-base />
                    <script type="module" src="./assets/index.js"></script>
                  </head>
                  <body><div id="root"></div></body>
                </html>
                """;

        String transformed = SpaIndexController.renderIndexHtml(html, "/brain");

        assertThat(transformed).contains("<base href=\"/brain/\" data-brain-app-base />");
        assertThat(transformed.indexOf("<base href=\"/brain/\" data-brain-app-base />"))
                .isLessThan(transformed.indexOf("<script type=\"module\" src=\"./assets/index.js\"></script>"));
    }

    @Test
    void shouldUseRootBaseHrefWhenServletContextIsEmpty() {
        String html = "<html><head><base href=\"/\" data-brain-app-base /></head><body></body></html>";

        String transformed = SpaIndexController.renderIndexHtml(html, "");

        assertThat(transformed).contains("<base href=\"/\" data-brain-app-base />");
    }

    @Test
    void shouldFailWhenIndexHtmlDoesNotDeclareTheAppBaseContract() {
        String html = "<html><head><script src=\"./assets/index.js\"></script></head></html>";

        assertThatThrownBy(() -> SpaIndexController.renderIndexHtml(html, "/brain"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Brain app base tag");
    }
}

/*
 * Copyright 2026 Aleksei Kuleshov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contact: alex@kuleshov.tech
 */

package me.golemcore.brain.web;

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

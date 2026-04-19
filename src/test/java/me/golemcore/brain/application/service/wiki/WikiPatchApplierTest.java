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

package me.golemcore.brain.application.service.wiki;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import me.golemcore.brain.domain.WikiPatchOperation;
import org.junit.jupiter.api.Test;

class WikiPatchApplierTest {

    @Test
    void shouldReplaceSectionWithoutTouchingOtherSections() {
        WikiPatchApplier applier = new WikiPatchApplier();
        WikiPatchRequest request = WikiPatchRequest.builder()
                .operation(WikiPatchOperation.REPLACE_SECTION)
                .heading("Status")
                .content("Updated status line.\n")
                .build();

        String patched = applier.apply(
                "# Runbook\n\n## Status\nOld status text.\n\n## Next Steps\nReview backlog.\n",
                request);

        assertTrue(patched.contains("Updated status line."));
        assertTrue(patched.contains("## Next Steps"));
        assertTrue(patched.contains("Review backlog."));
        assertFalse(patched.contains("Old status text."));
        assertEquals("Rewrote section 'Status' (+8 chars).",
                applier.buildSummary(request, "old", "new content"));
        assertEquals("Patch (replace section: Status)", applier.buildReason(request));
    }
}

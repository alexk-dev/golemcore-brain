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

package me.golemcore.brain.application.exception;

import me.golemcore.brain.domain.WikiPageDocument;

public class WikiEditConflictException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String expectedRevision;
    private final transient WikiPageDocument currentPage;

    public WikiEditConflictException(String expectedRevision, WikiPageDocument currentPage) {
        super("This page was updated in another session. Reload the latest version or merge your draft.");
        this.expectedRevision = expectedRevision;
        this.currentPage = currentPage;
    }

    public String getExpectedRevision() {
        return expectedRevision;
    }

    public String getCurrentRevision() {
        return currentPage.getRevision();
    }

    public WikiPageDocument getCurrentPage() {
        return currentPage;
    }
}

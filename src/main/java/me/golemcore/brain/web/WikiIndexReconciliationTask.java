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

import me.golemcore.brain.application.service.index.WikiIndexReconciliationScheduler;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WikiIndexReconciliationTask {

    private final WikiIndexReconciliationScheduler scheduler;

    @EventListener(ApplicationReadyEvent.class)
    public void runInitialReconciliation() {
        scheduler.reconcileAllNow();
    }

    @Scheduled(fixedDelayString = "${brain.indexing.reconciliation-delay-ms:300000}", initialDelayString = "${brain.indexing.reconciliation-initial-delay-ms:60000}")
    public void reconcile() {
        scheduler.reconcileAll();
    }
}

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

package me.golemcore.brain.application.service.index;

import me.golemcore.brain.application.port.out.SpaceRepository;
import me.golemcore.brain.domain.space.Space;
import java.util.logging.Level;
import java.util.logging.Logger;
import lombok.RequiredArgsConstructor;

/**
 * Periodic safety net that reconciles every known space against the on-disk
 * catalog. Runs on a fixed delay independently of the HTTP request path so that
 * drift introduced by out-of-band changes is picked up without punishing
 * latency-sensitive endpoints like {@code /search}.
 */
@RequiredArgsConstructor
public class WikiIndexReconciliationScheduler {

    private static final Logger LOGGER = Logger.getLogger(WikiIndexReconciliationScheduler.class.getName());

    private final SpaceRepository spaceRepository;
    private final WikiIndexingService wikiIndexingService;

    public void reconcileAll() {
        for (Space space : spaceRepository.listSpaces()) {
            wikiIndexingService.scheduleSynchronize(space.getId());
        }
    }

    /**
     * Synchronously reconcile every known space. Intended for application startup
     * so that the first search after boot returns a ready index.
     */
    public void reconcileAllNow() {
        for (Space space : spaceRepository.listSpaces()) {
            try {
                wikiIndexingService.synchronizeSpace(space.getId());
            } catch (RuntimeException exception) {
                // One broken space must not prevent the remaining spaces from
                // being reconciled at startup — otherwise the first bad space
                // blocks every other space from becoming searchable.
                LOGGER.log(Level.WARNING, exception,
                        () -> "Failed to reconcile space " + space.getId() + " at startup");
            }
        }
    }
}

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

package me.golemcore.brain.adapter.in.web;

import me.golemcore.brain.adapter.in.web.auth.AuthContextResolver;
import me.golemcore.brain.application.service.space.SpaceService;
import me.golemcore.brain.domain.auth.AuthContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Administrative API for queueing full search-index rebuilds.
 */
@RestController
@RequestMapping("/api/admin/spaces")
@RequiredArgsConstructor
public class AdminSpaceReindexController {

    private final SpaceService spaceService;
    private final AuthContextResolver authContextResolver;

    @PostMapping("/reindex")
    public ResponseEntity<ReindexResponse> reindexAllSpaces(HttpServletRequest request) {
        AuthContext context = authContextResolver.requireAuthenticated(request);
        int spacesQueued = spaceService.reindexAllSpaces(context);
        return ResponseEntity.accepted().body(new ReindexResponse("queued", spacesQueued));
    }

    @PostMapping("/{slug}/reindex")
    public ResponseEntity<ReindexResponse> reindexSpace(@PathVariable String slug, HttpServletRequest request) {
        AuthContext context = authContextResolver.requireAuthenticated(request);
        spaceService.reindexSpace(context, slug);
        return ResponseEntity.accepted().body(new ReindexResponse("queued", 1));
    }

    public record ReindexResponse(String status, int spacesQueued) {
    }
}

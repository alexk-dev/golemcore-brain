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
import me.golemcore.brain.domain.space.Space;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/spaces")
@RequiredArgsConstructor
public class SpaceController {

    private final SpaceService spaceService;
    private final AuthContextResolver authContextResolver;

    @GetMapping
    public List<Space> listSpaces(HttpServletRequest request) {
        AuthContext context = authContextResolver.resolve(request);
        return spaceService.listVisibleSpaces(context);
    }

    @PostMapping
    public ResponseEntity<Space> createSpace(@Valid @RequestBody CreateSpaceRequest payload,
            HttpServletRequest request) {
        AuthContext context = authContextResolver.requireAuthenticated(request);
        Space space = spaceService.createSpace(context, payload.getSlug(), payload.getName());
        return ResponseEntity.status(201).body(space);
    }

    @DeleteMapping("/{slug}")
    public ResponseEntity<Void> deleteSpace(@PathVariable String slug, HttpServletRequest request) {
        AuthContext context = authContextResolver.requireAuthenticated(request);
        spaceService.deleteSpace(context, slug);
        return ResponseEntity.noContent().build();
    }

    @Data
    public static class CreateSpaceRequest {
        @NotBlank
        @Size(max = 63)
        private String slug;

        @Size(max = 120)
        private String name;
    }
}

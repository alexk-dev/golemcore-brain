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
import me.golemcore.brain.application.service.dynamicapi.DynamicSpaceApiService;
import me.golemcore.brain.domain.auth.AuthContext;
import me.golemcore.brain.domain.dynamicapi.DynamicSpaceApiConfig;
import me.golemcore.brain.domain.dynamicapi.DynamicSpaceApiRunResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * CRUD and execution API for space-scoped Dynamic APIs backed by configured LLM
 * models.
 */
@RestController
@RequestMapping("/api/spaces/{slug}/dynamic-apis")
@RequiredArgsConstructor
public class DynamicSpaceApiController {

    private final DynamicSpaceApiService dynamicSpaceApiService;
    private final AuthContextResolver authContextResolver;

    @GetMapping
    public List<DynamicSpaceApiConfig> listApis(@PathVariable String slug, HttpServletRequest request) {
        AuthContext context = authContextResolver.requireAuthenticated(request);
        return dynamicSpaceApiService.listApis(context, slug);
    }

    @PostMapping
    public ResponseEntity<DynamicSpaceApiConfig> createApi(
            @PathVariable String slug,
            @Valid @RequestBody SaveDynamicSpaceApiRequest payload,
            HttpServletRequest request) {
        AuthContext context = authContextResolver.requireAuthenticated(request);
        DynamicSpaceApiConfig api = dynamicSpaceApiService.createApi(context, slug, toCommand(payload));
        return ResponseEntity.status(201).body(api);
    }

    @PutMapping("/{apiId}")
    public DynamicSpaceApiConfig updateApi(
            @PathVariable String slug,
            @PathVariable String apiId,
            @Valid @RequestBody SaveDynamicSpaceApiRequest payload,
            HttpServletRequest request) {
        AuthContext context = authContextResolver.requireAuthenticated(request);
        return dynamicSpaceApiService.updateApi(context, slug, apiId, toCommand(payload));
    }

    @DeleteMapping("/{apiId}")
    public ResponseEntity<Void> deleteApi(
            @PathVariable String slug,
            @PathVariable String apiId,
            HttpServletRequest request) {
        AuthContext context = authContextResolver.requireAuthenticated(request);
        dynamicSpaceApiService.deleteApi(context, slug, apiId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{apiSlug}/run")
    public DynamicSpaceApiRunResult runApi(
            @PathVariable String slug,
            @PathVariable String apiSlug,
            @RequestBody(required = false) Map<String, Object> payload,
            HttpServletRequest request) {
        AuthContext context = authContextResolver.resolve(request);
        return dynamicSpaceApiService.runApi(context, slug, apiSlug, payload);
    }

    private DynamicSpaceApiService.SaveCommand toCommand(SaveDynamicSpaceApiRequest payload) {
        return DynamicSpaceApiService.SaveCommand.builder()
                .slug(payload.getSlug())
                .name(payload.getName())
                .description(payload.getDescription())
                .modelConfigId(payload.getModelConfigId())
                .systemPrompt(payload.getSystemPrompt())
                .enabled(payload.getEnabled())
                .maxIterations(payload.getMaxIterations())
                .build();
    }

    @Data
    public static class SaveDynamicSpaceApiRequest {
        @NotBlank
        @Size(max = 80)
        private String slug;

        @Size(max = 120)
        private String name;

        @Size(max = 500)
        private String description;

        @NotBlank
        @Size(max = 120)
        private String modelConfigId;

        @NotBlank
        @Size(max = 12000)
        private String systemPrompt;

        private Boolean enabled;
        private Integer maxIterations;
    }
}

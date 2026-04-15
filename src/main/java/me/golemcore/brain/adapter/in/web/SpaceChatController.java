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
import me.golemcore.brain.application.service.chat.SpaceChatResponse;
import me.golemcore.brain.application.service.chat.SpaceChatService;
import me.golemcore.brain.domain.auth.AuthContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/spaces/{slug}/chat")
@RequiredArgsConstructor
public class SpaceChatController {

    private final SpaceChatService spaceChatService;
    private final AuthContextResolver authContextResolver;

    @PostMapping
    public SpaceChatResponse chat(
            @PathVariable String slug,
            @Valid @RequestBody SpaceChatRequest payload,
            HttpServletRequest request) {
        AuthContext context = authContextResolver.resolve(request);
        return spaceChatService.chat(context, slug, SpaceChatService.ChatCommand.builder()
                .message(payload.getMessage())
                .modelConfigId(payload.getModelConfigId())
                .summary(payload.getSummary())
                .turnCount(payload.getTurnCount())
                .history(toHistory(payload.getHistory()))
                .build());
    }

    private List<SpaceChatService.ChatMessage> toHistory(List<SpaceChatMessageRequest> history) {
        if (history == null) {
            return List.of();
        }
        return history.stream()
                .map(item -> SpaceChatService.ChatMessage.builder()
                        .role(item.getRole())
                        .content(item.getContent())
                        .build())
                .toList();
    }

    @Data
    public static class SpaceChatRequest {
        @NotBlank
        @Size(max = 8000)
        private String message;

        @Size(max = 120)
        private String modelConfigId;

        @Size(max = 12000)
        private String summary;

        private Integer turnCount;

        private List<SpaceChatMessageRequest> history;
    }

    @Data
    public static class SpaceChatMessageRequest {
        @NotBlank
        @Size(max = 20)
        private String role;

        @NotBlank
        @Size(max = 8000)
        private String content;
    }
}

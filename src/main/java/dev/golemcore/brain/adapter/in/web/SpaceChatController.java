package dev.golemcore.brain.adapter.in.web;

import dev.golemcore.brain.adapter.in.web.auth.AuthContextResolver;
import dev.golemcore.brain.application.service.chat.SpaceChatResponse;
import dev.golemcore.brain.application.service.chat.SpaceChatService;
import dev.golemcore.brain.domain.auth.AuthContext;
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

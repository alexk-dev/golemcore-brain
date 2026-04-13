package dev.golemcore.brain.application.service.chat;

import dev.golemcore.brain.application.exception.WikiNotFoundException;
import dev.golemcore.brain.application.port.out.LlmChatPort;
import dev.golemcore.brain.application.port.out.LlmSettingsRepository;
import dev.golemcore.brain.application.port.out.SpaceRepository;
import dev.golemcore.brain.application.port.out.WikiDocumentCatalogPort;
import dev.golemcore.brain.application.service.auth.AuthAccessDeniedException;
import dev.golemcore.brain.application.service.auth.AuthUnauthorizedException;
import dev.golemcore.brain.domain.Secret;
import dev.golemcore.brain.domain.WikiIndexedDocument;
import dev.golemcore.brain.domain.auth.AuthContext;
import dev.golemcore.brain.domain.auth.UserRole;
import dev.golemcore.brain.domain.llm.LlmChatMessage;
import dev.golemcore.brain.domain.llm.LlmChatRequest;
import dev.golemcore.brain.domain.llm.LlmChatResponse;
import dev.golemcore.brain.domain.llm.LlmModelConfig;
import dev.golemcore.brain.domain.llm.LlmModelKind;
import dev.golemcore.brain.domain.llm.LlmProviderConfig;
import dev.golemcore.brain.domain.llm.LlmSettings;
import dev.golemcore.brain.domain.space.Space;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.Value;

@RequiredArgsConstructor
public class SpaceChatService {

    private static final int MAX_CONTEXT_DOCUMENTS = 8;
    private static final int MAX_DOCUMENT_CHARS = 2200;
    private static final int COMPACTION_INTERVAL = 6;
    private static final java.util.Set<String> COMMON_QUESTION_WORDS = java.util.Set.of(
            "what", "does", "say", "tell", "about", "where", "when", "which", "that", "this", "the",
            "and", "are", "was", "were", "with", "from", "your", "into", "onto", "have", "has",
            "\u0447\u0442\u043e", "\u0433\u0434\u0435", "\u043a\u0430\u043a", "\u043a\u043e\u0433\u0434\u0430",
            "\u043f\u0440\u043e",
            "\u044d\u0442\u043e", "\u044d\u0442\u043e\u0442", "\u044d\u0442\u0430", "\u043c\u043d\u0435");
    private static final String SYSTEM_PROMPT = """
            You answer questions using only the provided space knowledge context.
            If the context does not contain enough information, say that the knowledge base does not contain the answer.
            Mention source page paths that support the answer.
            """;

    private final SpaceRepository spaceRepository;
    private final WikiDocumentCatalogPort wikiDocumentCatalogPort;
    private final LlmSettingsRepository llmSettingsRepository;
    private final LlmChatPort llmChatPort;

    public SpaceChatResponse chat(AuthContext authContext, String spaceSlug, ChatCommand command) {
        Space space = requireSpaceAccess(authContext, spaceSlug, UserRole.VIEWER);
        String message = requireMessage(command);
        ResolvedModel resolvedModel = resolveChatModel(command.getModelConfigId());
        List<WikiIndexedDocument> documents = selectDocuments(space.getId(), message + " "
                + (command.getSummary() == null ? "" : command.getSummary()));
        List<LlmChatMessage> requestMessages = buildMessages(command.getSummary(), command.getHistory(), message,
                documents);
        LlmChatResponse llmResponse = llmChatPort.chat(LlmChatRequest.builder()
                .provider(resolvedModel.provider())
                .model(resolvedModel.model())
                .systemPrompt(SYSTEM_PROMPT)
                .messages(requestMessages)
                .build());
        String answer = requireAnswer(llmResponse);
        String nextSummary = command.getSummary();
        boolean compacted = shouldCompact(command.getTurnCount());
        if (compacted) {
            nextSummary = compactSummary(resolvedModel, command.getSummary(), command.getHistory(), message, answer);
        }
        return SpaceChatResponse.builder()
                .answer(answer)
                .modelConfigId(resolvedModel.model().getId())
                .summary(nextSummary)
                .compacted(compacted)
                .sources(documents.stream().map(this::toSource).toList())
                .build();
    }

    private Space requireSpaceAccess(AuthContext authContext, String spaceSlug, UserRole requiredRole) {
        Space space = spaceRepository.findBySlug(spaceSlug)
                .orElseThrow(() -> new WikiNotFoundException("Space not found: " + spaceSlug));
        if (!authContext.isAuthDisabled() && !authContext.isAuthenticated() && !authContext.isReadOnlyAnonymous()) {
            throw new AuthUnauthorizedException("Authentication required");
        }
        if (!authContext.canAccessSpace(space.getId(), requiredRole)) {
            throw new AuthAccessDeniedException("Access to space '" + spaceSlug + "' denied");
        }
        return space;
    }

    private ResolvedModel resolveChatModel(String requestedModelConfigId) {
        LlmSettings settings = normalizeSettings(llmSettingsRepository.load());
        LlmModelConfig model = settings.getModels().stream()
                .filter(candidate -> candidate.getKind() == LlmModelKind.CHAT)
                .filter(candidate -> !Boolean.FALSE.equals(candidate.getEnabled()))
                .filter(candidate -> requestedModelConfigId == null || requestedModelConfigId.isBlank()
                        || requestedModelConfigId.equals(candidate.getId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Create an enabled chat model first"));
        LlmProviderConfig provider = settings.getProviders().get(model.getProvider());
        if (provider == null) {
            throw new WikiNotFoundException("LLM provider not found: " + model.getProvider());
        }
        if (!Secret.hasValue(provider.getApiKey())) {
            throw new IllegalArgumentException("LLM provider API key is not configured: " + model.getProvider());
        }
        return new ResolvedModel(provider, model);
    }

    private LlmSettings normalizeSettings(LlmSettings settings) {
        if (settings == null) {
            return LlmSettings.builder().build();
        }
        if (settings.getProviders() == null) {
            settings.setProviders(new LinkedHashMap<>());
        }
        if (settings.getModels() == null) {
            settings.setModels(new ArrayList<>());
        }
        return settings;
    }

    private String requireMessage(ChatCommand command) {
        if (command == null || command.getMessage() == null || command.getMessage().isBlank()) {
            throw new IllegalArgumentException("Chat message is required");
        }
        return command.getMessage().trim();
    }

    private List<WikiIndexedDocument> selectDocuments(String spaceId, String message) {
        List<String> queryTerms = tokenize(message);
        return wikiDocumentCatalogPort.listDocuments(spaceId).stream()
                .filter(document -> !isBoilerplateWelcome(document))
                .filter(document -> score(document, queryTerms) > 0)
                .sorted(Comparator.comparingInt((WikiIndexedDocument document) -> score(document, queryTerms))
                        .reversed()
                        .thenComparing(this::sourcePath))
                .limit(MAX_CONTEXT_DOCUMENTS)
                .toList();
    }

    private boolean isBoilerplateWelcome(WikiIndexedDocument document) {
        String path = document.getPath();
        String title = document.getTitle();
        String body = document.getBody();
        return (path == null || path.isBlank())
                && "Welcome".equals(title)
                && body != null
                && body.contains("This lightweight wiki stores every page as markdown on disk.");
    }

    private int score(WikiIndexedDocument document, List<String> queryTerms) {
        String haystack = ((document.getTitle() == null ? "" : document.getTitle()) + "\n"
                + (document.getPath() == null ? "" : document.getPath()) + "\n"
                + (document.getBody() == null ? "" : document.getBody())).toLowerCase(Locale.ROOT);
        int score = 0;
        for (String term : queryTerms) {
            if (haystack.contains(term)) {
                score++;
            }
        }
        return score;
    }

    private List<String> tokenize(String message) {
        String normalized = message.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9\\u0430-\\u044f\\u0451]+", " ").trim();
        if (normalized.isBlank()) {
            return List.of();
        }
        List<String> rawTokens = List.of(normalized.split("\\s+"));
        List<String> matchedTokens = rawTokens.stream()
                .filter(token -> token.length() > 2)
                .filter(token -> !COMMON_QUESTION_WORDS.contains(token))
                .toList();
        return matchedTokens.isEmpty()
                ? rawTokens.stream().filter(token -> token.length() > 2).toList()
                : matchedTokens;
    }

    private List<LlmChatMessage> buildMessages(String summary, List<ChatMessage> history, String question,
            List<WikiIndexedDocument> documents) {
        List<LlmChatMessage> messages = new ArrayList<>();
        if (summary != null && !summary.isBlank()) {
            messages.add(LlmChatMessage.builder()
                    .role("user")
                    .content("Conversation summary so far:\n" + summary.trim())
                    .build());
        }
        List<ChatMessage> allowedHistory = Optional.ofNullable(history).orElse(List.of()).stream()
                .filter(this::isAllowedHistoryMessage)
                .toList();
        allowedHistory.stream()
                .skip(Math.max(0, allowedHistory.size() - 12))
                .forEach(message -> messages.add(LlmChatMessage.builder()
                        .role(message.getRole())
                        .content(message.getContent())
                        .build()));
        messages.add(LlmChatMessage.builder()
                .role("user")
                .content(buildUserMessage(question, documents))
                .build());
        return messages;
    }

    private boolean isAllowedHistoryMessage(ChatMessage message) {
        return message != null
                && message.getRole() != null
                && ("user".equals(message.getRole()) || "assistant".equals(message.getRole()))
                && message.getContent() != null
                && !message.getContent().isBlank();
    }

    private String requireAnswer(LlmChatResponse llmResponse) {
        return Optional.ofNullable(llmResponse)
                .map(LlmChatResponse::getContent)
                .filter(content -> !content.isBlank())
                .orElseThrow(() -> new IllegalStateException("LLM provider returned no answer"));
    }

    private boolean shouldCompact(Integer turnCount) {
        return turnCount != null && turnCount > 0 && turnCount % COMPACTION_INTERVAL == 0;
    }

    private String compactSummary(ResolvedModel resolvedModel, String previousSummary, List<ChatMessage> history,
            String question, String answer) {
        List<LlmChatMessage> messages = new ArrayList<>();
        messages.add(LlmChatMessage.builder()
                .role("user")
                .content(buildSummaryRequest(previousSummary, history, question, answer))
                .build());
        LlmChatResponse response = llmChatPort.chat(LlmChatRequest.builder()
                .provider(resolvedModel.provider())
                .model(resolvedModel.model())
                .systemPrompt("Summarize the space chat conversation compactly for future context.")
                .messages(messages)
                .build());
        return requireAnswer(response);
    }

    private String buildSummaryRequest(String previousSummary, List<ChatMessage> history, String question,
            String answer) {
        StringBuilder builder = new StringBuilder();
        builder.append("Previous summary:\n")
                .append(previousSummary == null || previousSummary.isBlank() ? "None" : previousSummary.trim())
                .append("\n\nRecent messages:\n");
        List<ChatMessage> allowedHistory = Optional.ofNullable(history).orElse(List.of()).stream()
                .filter(this::isAllowedHistoryMessage)
                .toList();
        allowedHistory.stream()
                .skip(Math.max(0, allowedHistory.size() - 12))
                .forEach(message -> builder.append(message.getRole())
                        .append(": ")
                        .append(message.getContent().trim())
                        .append("\n"));
        builder.append("user: ").append(question).append("\n")
                .append("assistant: ").append(answer).append("\n\n")
                .append("Return a concise running summary only.");
        return builder.toString();
    }

    private String buildUserMessage(String question, List<WikiIndexedDocument> documents) {
        StringBuilder builder = new StringBuilder();
        builder.append("Question:\n").append(question).append("\n\n");
        builder.append("Knowledge context:\n");
        if (documents.isEmpty()) {
            builder.append("No matching pages were found in this space.\n");
            return builder.toString();
        }
        for (int index = 0; index < documents.size(); index++) {
            WikiIndexedDocument document = documents.get(index);
            builder.append("\n[Source ").append(index + 1).append("] ")
                    .append(document.getTitle()).append("\n")
                    .append("Path: ").append(sourcePath(document)).append("\n")
                    .append(truncate(document.getBody())).append("\n");
        }
        return builder.toString();
    }

    private SpaceChatSource toSource(WikiIndexedDocument document) {
        return SpaceChatSource.builder()
                .path(sourcePath(document))
                .title(document.getTitle())
                .excerpt(truncate(document.getBody()))
                .build();
    }

    private String sourcePath(WikiIndexedDocument document) {
        if (document.getPath() != null && !document.getPath().isBlank()) {
            return document.getPath();
        }
        if (document.getId() != null && !document.getId().isBlank()) {
            return document.getId();
        }
        return "";
    }

    private String truncate(String value) {
        if (value == null) {
            return "";
        }
        if (value.length() <= MAX_DOCUMENT_CHARS) {
            return value;
        }
        return value.substring(0, MAX_DOCUMENT_CHARS) + "\n[truncated]";
    }

    private record ResolvedModel(LlmProviderConfig provider, LlmModelConfig model) {
    }

    @Value
    @Builder
    public static class ChatCommand {
        String message;
        String modelConfigId;
        String summary;
        Integer turnCount;

        @Builder.Default
        List<ChatMessage> history = List.of();
    }

    @Value
    @Builder
    public static class ChatMessage {
        String role;
        String content;
    }
}

package dev.golemcore.brain.config;

import dev.golemcore.brain.application.port.out.ApiKeyRepository;
import dev.golemcore.brain.application.port.out.ApiKeyTokenPort;
import dev.golemcore.brain.application.port.out.BrainSettingsPort;
import dev.golemcore.brain.application.port.out.DynamicSpaceApiRepository;
import dev.golemcore.brain.application.port.out.DynamicSpaceApiToolPort;
import dev.golemcore.brain.application.port.out.JsonCodecPort;
import dev.golemcore.brain.application.port.out.LlmChatPort;
import dev.golemcore.brain.application.port.out.LlmEmbeddingPort;
import dev.golemcore.brain.application.port.out.LlmProviderCheckPort;
import dev.golemcore.brain.application.port.out.LlmSettingsRepository;
import dev.golemcore.brain.application.port.out.ModelRegistryCachePort;
import dev.golemcore.brain.application.port.out.ModelRegistryDocumentPort;
import dev.golemcore.brain.application.port.out.ModelRegistryRemotePort;
import dev.golemcore.brain.application.port.out.SpaceRepository;
import dev.golemcore.brain.application.port.out.WikiEmbeddingIndexPort;
import dev.golemcore.brain.application.port.out.WikiFullTextIndexPort;
import dev.golemcore.brain.application.port.out.WikiDocumentCatalogPort;
import dev.golemcore.brain.application.port.out.WikiRepository;
import dev.golemcore.brain.application.port.out.auth.SessionRepository;
import dev.golemcore.brain.application.port.out.auth.UserRepository;
import dev.golemcore.brain.application.service.WikiApplicationService;
import dev.golemcore.brain.application.service.apikey.ApiKeyService;
import dev.golemcore.brain.application.service.chat.SpaceChatService;
import dev.golemcore.brain.application.service.auth.AuthService;
import dev.golemcore.brain.application.service.auth.PasswordHasher;
import dev.golemcore.brain.application.service.dynamicapi.DynamicSpaceApiService;
import dev.golemcore.brain.application.service.index.WikiIndexingService;
import dev.golemcore.brain.application.service.llm.LlmSettingsService;
import dev.golemcore.brain.application.service.llm.ModelRegistryService;
import dev.golemcore.brain.application.service.space.SpaceService;
import dev.golemcore.brain.application.service.user.UserManagementService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BrainApplicationConfiguration {

    @Bean(initMethod = "initialize")
    public AuthService authService(
            BrainSettingsPort brainSettingsPort,
            UserRepository userRepository,
            SessionRepository sessionRepository,
            PasswordHasher passwordHasher) {
        return new AuthService(brainSettingsPort, userRepository, sessionRepository, passwordHasher);
    }

    @Bean
    public PasswordHasher passwordHasher() {
        return new PasswordHasher();
    }

    @Bean(initMethod = "initialize")
    public WikiApplicationService wikiApplicationService(
            WikiRepository wikiRepository,
            BrainSettingsPort brainSettingsPort,
            WikiIndexingService wikiIndexingService) {
        return new WikiApplicationService(wikiRepository, brainSettingsPort, wikiIndexingService);
    }

    @Bean
    public WikiIndexingService wikiIndexingService(
            WikiRepository wikiRepository,
            WikiFullTextIndexPort wikiFullTextIndexPort,
            WikiEmbeddingIndexPort wikiEmbeddingIndexPort,
            LlmSettingsRepository llmSettingsRepository,
            LlmEmbeddingPort llmEmbeddingPort) {
        return new WikiIndexingService(
                wikiRepository,
                wikiFullTextIndexPort,
                wikiEmbeddingIndexPort,
                llmSettingsRepository,
                llmEmbeddingPort);
    }

    @Bean
    public ApiKeyService apiKeyService(
            ApiKeyRepository apiKeyRepository,
            SpaceRepository spaceRepository,
            ApiKeyTokenPort apiKeyTokenPort) {
        return new ApiKeyService(apiKeyRepository, spaceRepository, apiKeyTokenPort);
    }

    @Bean
    public SpaceChatService spaceChatService(
            SpaceRepository spaceRepository,
            WikiDocumentCatalogPort wikiDocumentCatalogPort,
            LlmSettingsRepository llmSettingsRepository,
            LlmChatPort llmChatPort) {
        return new SpaceChatService(spaceRepository, wikiDocumentCatalogPort, llmSettingsRepository, llmChatPort);
    }

    @Bean
    public DynamicSpaceApiService dynamicSpaceApiService(
            SpaceRepository spaceRepository,
            DynamicSpaceApiRepository dynamicSpaceApiRepository,
            LlmSettingsRepository llmSettingsRepository,
            LlmChatPort llmChatPort,
            DynamicSpaceApiToolPort dynamicSpaceApiToolPort,
            JsonCodecPort jsonCodecPort) {
        return new DynamicSpaceApiService(
                spaceRepository,
                dynamicSpaceApiRepository,
                llmSettingsRepository,
                llmChatPort,
                dynamicSpaceApiToolPort,
                jsonCodecPort);
    }

    @Bean
    public LlmSettingsService llmSettingsService(
            LlmSettingsRepository llmSettingsRepository,
            LlmProviderCheckPort llmProviderCheckPort,
            LlmChatPort llmChatPort,
            LlmEmbeddingPort llmEmbeddingPort,
            ModelRegistryService modelRegistryService) {
        return new LlmSettingsService(
                llmSettingsRepository,
                llmProviderCheckPort,
                llmChatPort,
                llmEmbeddingPort,
                modelRegistryService);
    }

    @Bean
    public ModelRegistryService modelRegistryService(
            LlmSettingsRepository llmSettingsRepository,
            ModelRegistryRemotePort modelRegistryRemotePort,
            ModelRegistryCachePort modelRegistryCachePort,
            ModelRegistryDocumentPort modelRegistryDocumentPort) {
        return new ModelRegistryService(
                llmSettingsRepository,
                modelRegistryRemotePort,
                modelRegistryCachePort,
                modelRegistryDocumentPort);
    }

    @Bean
    public SpaceService spaceService(SpaceRepository spaceRepository, WikiRepository wikiRepository) {
        return new SpaceService(spaceRepository, wikiRepository);
    }

    @Bean
    public UserManagementService userManagementService(
            UserRepository userRepository,
            PasswordHasher passwordHasher,
            AuthService authService,
            SessionRepository sessionRepository) {
        return new UserManagementService(userRepository, passwordHasher, authService, sessionRepository);
    }
}

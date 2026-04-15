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

package me.golemcore.brain.config;

import me.golemcore.brain.application.port.out.ApiKeyRepository;
import me.golemcore.brain.application.port.out.ApiKeyTokenPort;
import me.golemcore.brain.application.port.out.BrainSettingsPort;
import me.golemcore.brain.application.port.out.DynamicSpaceApiRepository;
import me.golemcore.brain.application.port.out.DynamicSpaceApiToolPort;
import me.golemcore.brain.application.port.out.JsonCodecPort;
import me.golemcore.brain.application.port.out.LlmChatPort;
import me.golemcore.brain.application.port.out.LlmEmbeddingPort;
import me.golemcore.brain.application.port.out.LlmProviderCheckPort;
import me.golemcore.brain.application.port.out.LlmSettingsRepository;
import me.golemcore.brain.application.port.out.ModelRegistryCachePort;
import me.golemcore.brain.application.port.out.ModelRegistryDocumentPort;
import me.golemcore.brain.application.port.out.ModelRegistryRemotePort;
import me.golemcore.brain.application.port.out.SpaceRepository;
import me.golemcore.brain.application.port.out.WikiEmbeddingIndexPort;
import me.golemcore.brain.application.port.out.WikiFullTextIndexPort;
import me.golemcore.brain.application.port.out.WikiDocumentCatalogPort;
import me.golemcore.brain.application.port.out.WikiRepository;
import me.golemcore.brain.application.port.out.auth.SessionRepository;
import me.golemcore.brain.application.port.out.auth.UserRepository;
import me.golemcore.brain.application.service.WikiApplicationService;
import me.golemcore.brain.application.service.apikey.ApiKeyService;
import me.golemcore.brain.application.service.chat.SpaceChatService;
import me.golemcore.brain.application.service.auth.AuthService;
import me.golemcore.brain.application.service.auth.PasswordHasher;
import me.golemcore.brain.application.service.dynamicapi.DynamicSpaceApiService;
import me.golemcore.brain.application.service.index.WikiIndexReconciliationScheduler;
import me.golemcore.brain.application.service.index.WikiIndexingService;
import me.golemcore.brain.application.service.llm.LlmSettingsService;
import me.golemcore.brain.application.service.llm.ModelRegistryService;
import me.golemcore.brain.application.service.space.SpaceService;
import me.golemcore.brain.application.service.user.UserManagementService;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
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
            LlmEmbeddingPort llmEmbeddingPort,
            Executor wikiIndexingExecutor) {
        return new WikiIndexingService(
                wikiRepository,
                wikiFullTextIndexPort,
                wikiEmbeddingIndexPort,
                llmSettingsRepository,
                llmEmbeddingPort,
                wikiIndexingExecutor);
    }

    @Bean(destroyMethod = "shutdown")
    public java.util.concurrent.ExecutorService wikiIndexingExecutor() {
        AtomicInteger counter = new AtomicInteger();
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable, "brain-indexer-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        return Executors.newSingleThreadExecutor(threadFactory);
    }

    @Bean
    public WikiIndexReconciliationScheduler wikiIndexReconciliationScheduler(
            SpaceRepository spaceRepository, WikiIndexingService wikiIndexingService) {
        return new WikiIndexReconciliationScheduler(spaceRepository, wikiIndexingService);
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

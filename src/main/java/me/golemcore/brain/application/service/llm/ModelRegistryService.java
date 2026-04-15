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

package me.golemcore.brain.application.service.llm;

import me.golemcore.brain.application.port.out.LlmSettingsRepository;
import me.golemcore.brain.application.port.out.ModelRegistryCachePort;
import me.golemcore.brain.application.port.out.ModelRegistryDocumentPort;
import me.golemcore.brain.application.port.out.ModelRegistryRemotePort;
import me.golemcore.brain.domain.llm.LlmSettings;
import me.golemcore.brain.domain.llm.ModelCatalogEntry;
import me.golemcore.brain.domain.llm.ModelRegistryConfig;
import me.golemcore.brain.domain.llm.ModelRegistryResolveResult;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@Slf4j
public class ModelRegistryService {

    private static final Duration CACHE_TTL = Duration.ofDays(1);
    private static final String DEFAULT_BRANCH = "main";
    private static final String DEFAULT_REPOSITORY_URL = "https://github.com/alexk-dev/golemcore-models";
    private static final String DEFAULT_RAW_BASE_URL = "https://raw.githubusercontent.com";

    private final LlmSettingsRepository llmSettingsRepository;
    private final ModelRegistryRemotePort modelRegistryRemotePort;
    private final ModelRegistryCachePort modelRegistryCachePort;
    private final ModelRegistryDocumentPort modelRegistryDocumentPort;

    public ModelRegistryConfig getConfig() {
        LlmSettings settings = normalizeSettings(llmSettingsRepository.load());
        ModelRegistryConfig config = settings.getModelRegistry();
        if (config == null) {
            return defaultConfig();
        }
        return ModelRegistryConfig.builder()
                .repositoryUrl(defaultIfBlank(config.getRepositoryUrl(), DEFAULT_REPOSITORY_URL))
                .branch(defaultIfBlank(config.getBranch(), DEFAULT_BRANCH))
                .build();
    }

    public LlmSettings updateConfig(ModelRegistryConfig config) {
        LlmSettings settings = normalizeSettings(llmSettingsRepository.load());
        settings.setModelRegistry(ModelRegistryConfig.builder()
                .repositoryUrl(
                        defaultIfBlank(config != null ? config.getRepositoryUrl() : null, DEFAULT_REPOSITORY_URL))
                .branch(defaultIfBlank(config != null ? config.getBranch() : null, DEFAULT_BRANCH))
                .build());
        return llmSettingsRepository.save(settings);
    }

    public ModelRegistryResolveResult resolveDefaults(String provider, String modelId) {
        String normalizedProvider = requireValue(provider, "provider");
        String normalizedModelId = normalizeModelId(modelId);
        RegistrySource source = resolveSource();
        List<RegistryCandidate> candidates = List.of(
                new RegistryCandidate("provider",
                        "providers/" + normalizedProvider + "/" + normalizedModelId + ".json"),
                new RegistryCandidate("shared", "models/" + normalizedModelId + ".json"));

        ModelRegistryResolveResult freshCachedResult = findFreshCachedResult(source, candidates, normalizedProvider);
        if (freshCachedResult != null) {
            return freshCachedResult;
        }

        for (RegistryCandidate candidate : candidates) {
            ModelRegistryResolveResult result = resolveCandidate(source, candidate, normalizedProvider);
            if (result != null) {
                return result;
            }
        }
        return new ModelRegistryResolveResult(null, null, "miss");
    }

    protected Instant now() {
        return Instant.now();
    }

    private ModelRegistryResolveResult findFreshCachedResult(
            RegistrySource source,
            List<RegistryCandidate> candidates,
            String provider) {
        for (RegistryCandidate candidate : candidates) {
            ModelRegistryCachePort.CachedRegistryEntry cacheEntry = readCacheEntry(source, candidate);
            if (cacheEntry == null || !cacheEntry.found() || !isFresh(cacheEntry)) {
                continue;
            }
            ModelCatalogEntry settings = tryParseSettings(cacheEntry.content(), provider, candidate.relativePath());
            if (settings != null) {
                return new ModelRegistryResolveResult(settings, candidate.configSource(), "fresh-hit");
            }
        }
        return null;
    }

    private ModelRegistryResolveResult resolveCandidate(RegistrySource source, RegistryCandidate candidate,
            String provider) {
        ModelRegistryCachePort.CachedRegistryEntry cacheEntry = readCacheEntry(source, candidate);
        ModelCatalogEntry cachedSettings = null;

        if (cacheEntry != null && cacheEntry.found()) {
            cachedSettings = tryParseSettings(cacheEntry.content(), provider, candidate.relativePath());
            if (cachedSettings != null && isFresh(cacheEntry)) {
                return new ModelRegistryResolveResult(cachedSettings, candidate.configSource(), "fresh-hit");
            }
        }

        try {
            URI remoteUri = remoteFileUri(source, candidate.relativePath());
            String remoteText = modelRegistryRemotePort.fetchText(remoteUri);
            if (remoteText == null) {
                writeCacheEntry(source, candidate, new ModelRegistryCachePort.CachedRegistryEntry(now(), false, null));
                if (cachedSettings != null) {
                    return new ModelRegistryResolveResult(cachedSettings, candidate.configSource(), "stale-hit");
                }
                return null;
            }

            ModelCatalogEntry settings = tryParseSettings(remoteText, provider, candidate.relativePath());
            if (settings == null) {
                if (cachedSettings != null) {
                    return new ModelRegistryResolveResult(cachedSettings, candidate.configSource(), "stale-hit");
                }
                return null;
            }

            writeCacheEntry(source, candidate,
                    new ModelRegistryCachePort.CachedRegistryEntry(now(), true, remoteText));
            return new ModelRegistryResolveResult(settings, candidate.configSource(), "remote-hit");
        } catch (RuntimeException exception) {
            log.warn("[ModelRegistry] Failed to refresh {}: {}", candidate.relativePath(), exception.getMessage());
            if (cachedSettings != null) {
                return new ModelRegistryResolveResult(cachedSettings, candidate.configSource(), "stale-hit");
            }
            return null;
        }
    }

    private ModelRegistryCachePort.CachedRegistryEntry readCacheEntry(RegistrySource source,
            RegistryCandidate candidate) {
        try {
            return modelRegistryCachePort.read(source.repositoryUrl(), source.branch(), candidate.relativePath());
        } catch (RuntimeException exception) { // NOSONAR
            log.warn("[ModelRegistry] Failed to read cache entry {}: {}", candidate.relativePath(),
                    exception.getMessage());
            return null;
        }
    }

    private void writeCacheEntry(
            RegistrySource source,
            RegistryCandidate candidate,
            ModelRegistryCachePort.CachedRegistryEntry entry) {
        try {
            modelRegistryCachePort.write(source.repositoryUrl(), source.branch(), candidate.relativePath(), entry);
        } catch (RuntimeException exception) { // NOSONAR
            log.warn("[ModelRegistry] Failed to write cache entry {}: {}", candidate.relativePath(),
                    exception.getMessage());
        }
    }

    private ModelCatalogEntry tryParseSettings(String json, String provider, String sourcePath) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            ModelCatalogEntry settings = modelRegistryDocumentPort.parseCatalogEntry(json);
            return settings.withProvider(provider);
        } catch (RuntimeException exception) {
            log.warn("[ModelRegistry] Invalid config for {}: {}", sourcePath, exception.getMessage());
            return null;
        }
    }

    private boolean isFresh(ModelRegistryCachePort.CachedRegistryEntry entry) {
        return entry.cachedAt() != null && !entry.cachedAt().plus(CACHE_TTL).isBefore(now());
    }

    private RegistrySource resolveSource() {
        ModelRegistryConfig config = getConfig();
        return new RegistrySource(config.getRepositoryUrl(), config.getBranch());
    }

    private URI remoteFileUri(RegistrySource source, String filePath) {
        GitHubRepository repository = parseRemoteRepository(source.repositoryUrl());
        String relativePath = repository.owner() + "/" + repository.name() + "/" + encodePathSegment(source.branch())
                + "/" + encodeRelativePath(filePath);
        return repositoryRawBaseUrl().resolve(relativePath);
    }

    private URI repositoryRawBaseUrl() {
        return URI.create(DEFAULT_RAW_BASE_URL + "/");
    }

    private GitHubRepository parseRemoteRepository(String repositoryUrl) {
        URI url = URI.create(repositoryUrl.endsWith("/") ? repositoryUrl : repositoryUrl + "/");
        String path = url.getPath();
        if (path == null || path.isBlank()) {
            throw new IllegalStateException("Model registry repository URL is invalid: " + url);
        }
        String normalized = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
        if (normalized.endsWith(".git")) {
            normalized = normalized.substring(0, normalized.length() - 4);
        }
        String[] segments = normalized.split("/");
        if (segments.length < 3) {
            throw new IllegalStateException("Model registry repository URL must match <host>/<owner>/<repo>");
        }
        return new GitHubRepository(segments[segments.length - 2], segments[segments.length - 1]);
    }

    private String encodeRelativePath(String value) {
        String[] segments = value.split("/");
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < segments.length; index++) {
            if (index > 0) {
                builder.append('/');
            }
            builder.append(encodePathSegment(segments[index]));
        }
        return builder.toString();
    }

    private String encodePathSegment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private String normalizeModelId(String modelId) {
        String normalized = requireValue(modelId, "modelId");
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return requireValue(stripDatedSuffix(normalized), "modelId");
    }

    private String stripDatedSuffix(String modelId) {
        String monthYearStripped = stripMonthYearSuffix(modelId);
        if (!monthYearStripped.equals(modelId)) {
            return monthYearStripped;
        }
        String isoDateStripped = stripIsoDateSuffix(modelId);
        if (!isoDateStripped.equals(modelId)) {
            return isoDateStripped;
        }
        return stripCompactDateSuffix(modelId);
    }

    private String stripMonthYearSuffix(String modelId) {
        int yearDash = modelId.lastIndexOf('-');
        if (yearDash <= 0 || modelId.length() - yearDash - 1 != 4) {
            return modelId;
        }
        String yearPart = modelId.substring(yearDash + 1);
        if (!isDigits(yearPart)) {
            return modelId;
        }
        int monthDash = modelId.lastIndexOf('-', yearDash - 1);
        if (monthDash <= 0 || yearDash - monthDash - 1 != 2) {
            return modelId;
        }
        String monthPart = modelId.substring(monthDash + 1, yearDash);
        if (!isDigits(monthPart)) {
            return modelId;
        }
        int month = Integer.parseInt(monthPart);
        if (month < 1 || month > 12) {
            return modelId;
        }
        return modelId.substring(0, monthDash);
    }

    private String stripIsoDateSuffix(String modelId) {
        int dayDash = modelId.lastIndexOf('-');
        if (dayDash <= 0 || modelId.length() - dayDash - 1 != 2) {
            return modelId;
        }
        String dayPart = modelId.substring(dayDash + 1);
        if (!isDigits(dayPart)) {
            return modelId;
        }
        int monthDash = modelId.lastIndexOf('-', dayDash - 1);
        if (monthDash <= 0 || dayDash - monthDash - 1 != 2) {
            return modelId;
        }
        String monthPart = modelId.substring(monthDash + 1, dayDash);
        if (!isDigits(monthPart)) {
            return modelId;
        }
        int yearDash = modelId.lastIndexOf('-', monthDash - 1);
        if (yearDash <= 0 || monthDash - yearDash - 1 != 4) {
            return modelId;
        }
        String yearPart = modelId.substring(yearDash + 1, monthDash);
        if (!isDigits(yearPart)) {
            return modelId;
        }
        int month = Integer.parseInt(monthPart);
        int day = Integer.parseInt(dayPart);
        if (month < 1 || month > 12 || day < 1 || day > 31) {
            return modelId;
        }
        return modelId.substring(0, yearDash);
    }

    private String stripCompactDateSuffix(String modelId) {
        int dash = modelId.lastIndexOf('-');
        if (dash <= 0 || modelId.length() - dash - 1 != 8) {
            return modelId;
        }
        String compactDate = modelId.substring(dash + 1);
        if (!isDigits(compactDate)) {
            return modelId;
        }
        int month = Integer.parseInt(compactDate.substring(4, 6));
        int day = Integer.parseInt(compactDate.substring(6, 8));
        if (month < 1 || month > 12 || day < 1 || day > 31) {
            return modelId;
        }
        return modelId.substring(0, dash);
    }

    private boolean isDigits(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            if (!Character.isDigit(value.charAt(index))) {
                return false;
            }
        }
        return true;
    }

    private String requireValue(String value, String fieldName) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return normalized;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String defaultIfBlank(String value, String fallback) {
        String normalized = trimToNull(value);
        return normalized != null ? normalized : fallback;
    }

    private ModelRegistryConfig defaultConfig() {
        return ModelRegistryConfig.builder()
                .repositoryUrl(DEFAULT_REPOSITORY_URL)
                .branch(DEFAULT_BRANCH)
                .build();
    }

    private LlmSettings normalizeSettings(LlmSettings settings) {
        if (settings == null) {
            return LlmSettings.builder().build();
        }
        if (settings.getModelRegistry() == null) {
            settings.setModelRegistry(defaultConfig());
        }
        return settings;
    }

    private record RegistrySource(String repositoryUrl, String branch) {
    }

    private record RegistryCandidate(String configSource, String relativePath) {
    }

    private record GitHubRepository(String owner, String name) {
    }
}

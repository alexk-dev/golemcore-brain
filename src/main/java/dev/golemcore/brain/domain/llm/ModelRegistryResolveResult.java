package dev.golemcore.brain.domain.llm;

public record ModelRegistryResolveResult(ModelCatalogEntry defaultSettings,String configSource,String cacheStatus){}

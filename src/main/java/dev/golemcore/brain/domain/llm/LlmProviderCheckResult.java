package dev.golemcore.brain.domain.llm;

public record LlmProviderCheckResult(boolean success,String message,Integer statusCode){}

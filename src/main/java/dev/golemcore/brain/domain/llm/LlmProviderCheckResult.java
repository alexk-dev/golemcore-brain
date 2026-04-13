package dev.golemcore.brain.domain.llm;

import java.util.List;

public record LlmProviderCheckResult(boolean success,String message,Integer statusCode,List<String>models){

public LlmProviderCheckResult(boolean success,String message,Integer statusCode){this(success,message,statusCode,List.of());}

public LlmProviderCheckResult{models=models==null?List.of():List.copyOf(models);}}

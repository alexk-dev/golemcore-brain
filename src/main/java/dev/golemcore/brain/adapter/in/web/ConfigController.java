package dev.golemcore.brain.adapter.in.web;

import dev.golemcore.brain.application.service.WikiApplicationService;
import dev.golemcore.brain.domain.WikiConfigResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ConfigController {

    private final WikiApplicationService wikiApplicationService;

    @GetMapping("/config")
    public WikiConfigResponse getConfig() {
        return wikiApplicationService.getConfig();
    }
}

package dev.golemcore.brain.adapter.in.web;

import dev.golemcore.brain.adapter.in.web.auth.AuthContextResolver;
import dev.golemcore.brain.application.service.space.SpaceService;
import dev.golemcore.brain.domain.auth.AuthContext;
import dev.golemcore.brain.domain.space.Space;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/spaces")
@RequiredArgsConstructor
public class SpaceController {

    private final SpaceService spaceService;
    private final AuthContextResolver authContextResolver;

    @GetMapping
    public List<Space> listSpaces(HttpServletRequest request) {
        AuthContext context = authContextResolver.resolve(request);
        return spaceService.listVisibleSpaces(context);
    }

    @PostMapping
    public ResponseEntity<Space> createSpace(@Valid @RequestBody CreateSpaceRequest payload,
            HttpServletRequest request) {
        AuthContext context = authContextResolver.requireAuthenticated(request);
        Space space = spaceService.createSpace(context, payload.getSlug(), payload.getName());
        return ResponseEntity.status(201).body(space);
    }

    @DeleteMapping("/{slug}")
    public ResponseEntity<Void> deleteSpace(@PathVariable String slug, HttpServletRequest request) {
        AuthContext context = authContextResolver.requireAuthenticated(request);
        spaceService.deleteSpace(context, slug);
        return ResponseEntity.noContent().build();
    }

    @Data
    public static class CreateSpaceRequest {
        @NotBlank
        @Size(max = 63)
        private String slug;

        @Size(max = 120)
        private String name;
    }
}

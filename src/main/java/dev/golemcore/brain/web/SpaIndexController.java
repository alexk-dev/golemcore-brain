package dev.golemcore.brain.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.HtmlUtils;

@Controller
public class SpaIndexController {

    private static final String APP_BASE_TAG = "<base href=\"/\" data-brain-app-base />";
    private static final String INDEX_HTML_RESOURCE = "classpath:/static/index.html";

    private final ResourceLoader resourceLoader;

    public SpaIndexController(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @ResponseBody
    @GetMapping(value = { "/", "/index.html" }, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> renderRoot(HttpServletRequest request) {
        return indexHtmlResponse(request);
    }

    @ResponseBody
    @GetMapping(value = { "/{path:[^\\.]*}", "/**/{path:[^\\.]*}" }, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> renderSpaRoute(HttpServletRequest request) {
        return indexHtmlResponse(request);
    }

    private ResponseEntity<String> indexHtmlResponse(HttpServletRequest request) {
        try {
            Resource resource = resourceLoader.getResource(INDEX_HTML_RESOURCE);
            if (!resource.exists()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Frontend index.html not found");
            }
            String indexHtml = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_HTML)
                    .body(renderIndexHtml(indexHtml, request.getContextPath()));
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to read frontend index.html",
                    exception);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, exception.getMessage(), exception);
        }
    }

    static String renderIndexHtml(String indexHtml, String contextPath) {
        if (!indexHtml.contains(APP_BASE_TAG)) {
            throw new IllegalStateException("Frontend index.html is missing the Brain app base tag");
        }
        String baseTag = "<base href=\"" + HtmlUtils.htmlEscape(baseHrefForContextPath(contextPath))
                + "\" data-brain-app-base />";
        return indexHtml.replace(APP_BASE_TAG, baseTag);
    }

    private static String baseHrefForContextPath(String contextPath) {
        if (contextPath == null || contextPath.isBlank() || "/".equals(contextPath)) {
            return "/";
        }
        String normalized = contextPath.replaceAll("/+$", "");
        return normalized + "/";
    }
}

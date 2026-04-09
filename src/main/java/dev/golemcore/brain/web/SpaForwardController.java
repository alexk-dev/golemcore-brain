package dev.golemcore.brain.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaForwardController {

    @GetMapping("/")
    public String forwardRoot() {
        return "forward:/index.html";
    }

    @GetMapping({"/{path:[^\\.]*}", "/**/{path:[^\\.]*}"})
    public String forwardSpaPaths() {
        return "forward:/index.html";
    }
}

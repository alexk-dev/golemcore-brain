package dev.golemcore.brain.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaForwardController {

    @GetMapping({"/", "/{path:^(?!api|assets|index\\.html|favicon\\.ico).*$}", "/**/{path:^(?!api|assets|index\\.html|favicon\\.ico).*$}"})
    public String forwardToIndex() {
        return "forward:/index.html";
    }
}

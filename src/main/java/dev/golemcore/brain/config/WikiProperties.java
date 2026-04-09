package dev.golemcore.brain.config;

import java.nio.file.Path;
import java.nio.file.Paths;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "brain")
public class WikiProperties {

    private Path storageRoot = Paths.get("data", "wiki");
    private String siteTitle = "GolemCore Brain";
    private boolean seedDemoContent = true;
}

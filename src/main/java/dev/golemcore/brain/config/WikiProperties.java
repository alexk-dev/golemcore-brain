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
    private boolean authDisabled = false;
    private boolean publicAccess = false;
    private String adminUsername = "admin";
    private String adminEmail = "admin@example.com";
    private String adminPassword = "admin";
    private long sessionTtlSeconds = 60L * 60L * 24L * 7L;
}

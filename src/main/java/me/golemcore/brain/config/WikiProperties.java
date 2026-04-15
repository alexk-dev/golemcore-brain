/*
 * Copyright 2026 Aleksei Kuleshov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contact: alex@kuleshov.tech
 */

package me.golemcore.brain.config;

import me.golemcore.brain.application.port.out.BrainSettingsPort;
import java.nio.file.Path;
import java.nio.file.Paths;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "brain")
public class WikiProperties implements BrainSettingsPort {

    private Path storageRoot = Paths.get("data", "wiki");
    private String siteTitle = "GolemCore Brain";
    private String imageVersion = "dev";
    private boolean seedDemoContent = true;
    private boolean authDisabled = false;
    private boolean publicAccess = false;
    private String adminUsername = "admin";
    private String adminEmail = "admin@example.com";
    private String adminPassword = "admin";
    private long sessionTtlSeconds = 60L * 60L * 24L * 7L;

    private Jwt jwt = new Jwt();
    private String defaultSpaceSlug = "default";
    private String defaultSpaceName = "Default";

    @Data
    public static class Jwt {
        private String secret = "change-me-change-me-change-me-change-me-change-me";
        private String issuer = "golemcore-brain";
    }
}

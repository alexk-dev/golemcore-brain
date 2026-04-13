package dev.golemcore.brain.application.port.out;

import java.net.URI;

public interface ModelRegistryRemotePort {
    String fetchText(URI uri);
}

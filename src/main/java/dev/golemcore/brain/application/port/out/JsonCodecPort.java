package dev.golemcore.brain.application.port.out;

public interface JsonCodecPort {

    String write(Object value);

    Object read(String content);
}

package dev.golemcore.brain.application.space;

/**
 * Per-request holder for the active space id. Populated by SpaceResolverFilter
 * before any controller / service / repository code runs.
 */
public final class SpaceContextHolder {

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private SpaceContextHolder() {
    }

    public static void set(String spaceId) {
        CURRENT.set(spaceId);
    }

    public static String get() {
        return CURRENT.get();
    }

    public static String require() {
        String value = CURRENT.get();
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Space context is not set for the current request");
        }
        return value;
    }

    public static void clear() {
        CURRENT.remove();
    }
}

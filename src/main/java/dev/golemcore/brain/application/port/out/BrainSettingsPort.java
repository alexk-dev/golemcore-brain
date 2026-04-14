package dev.golemcore.brain.application.port.out;

public interface BrainSettingsPort {

    String getSiteTitle();

    String getImageVersion();

    boolean isAuthDisabled();

    boolean isPublicAccess();

    String getAdminUsername();

    String getAdminEmail();

    String getAdminPassword();

    long getSessionTtlSeconds();
}

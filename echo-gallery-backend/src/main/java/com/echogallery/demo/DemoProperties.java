package com.echogallery.demo;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.demo")
public class DemoProperties {
    private boolean enabled = false;
    private int sessionTtlHours = 24;
    private final Cleanup cleanup = new Cleanup();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public int getSessionTtlHours() { return sessionTtlHours; }
    public void setSessionTtlHours(int sessionTtlHours) { this.sessionTtlHours = sessionTtlHours; }
    public Cleanup getCleanup() { return cleanup; }

    public static class Cleanup {
        private boolean enabled = true;
        private long fixedDelayMs = 900000;
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public long getFixedDelayMs() { return fixedDelayMs; }
        public void setFixedDelayMs(long fixedDelayMs) { this.fixedDelayMs = fixedDelayMs; }
    }
}

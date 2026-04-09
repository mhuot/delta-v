package org.deltav.netmgt.syslogd.boot;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TimeZone;

import org.opennms.netmgt.config.SyslogdConfig;
import org.opennms.netmgt.config.syslogd.HideMatch;
import org.opennms.netmgt.config.syslogd.SyslogdConfiguration;
import org.opennms.netmgt.config.syslogd.UeiMatch;

/**
 * Lightweight {@link SyslogdConfig} implementation that wraps a deserialized
 * {@link SyslogdConfiguration} model.  Replaces the legacy
 * {@code SyslogdConfigFactory} (JAXB + OSGi extensions) with a plain
 * delegation to the JAXB-annotated model objects.
 *
 * <p>UEI matches and hide matches from {@code <import-file>} directives
 * must be merged into the model <em>before</em> this adapter is constructed.</p>
 */
final class SyslogdConfigAdapter implements SyslogdConfig {

    private final SyslogdConfiguration config;

    SyslogdConfigAdapter(SyslogdConfiguration config) {
        this.config = config;
    }

    @Override
    public int getSyslogPort() {
        return config.getConfiguration().getSyslogPort();
    }

    @Override
    public String getListenAddress() {
        return config.getConfiguration().getListenAddress().orElse(null);
    }

    @Override
    public boolean getNewSuspectOnMessage() {
        return config.getConfiguration().getNewSuspectOnMessage();
    }

    @Override
    public String getForwardingRegexp() {
        return config.getConfiguration().getForwardingRegexp().orElse(null);
    }

    @Override
    public Integer getMatchingGroupHost() {
        return config.getConfiguration().getMatchingGroupHost().orElse(null);
    }

    @Override
    public Integer getMatchingGroupMessage() {
        return config.getConfiguration().getMatchingGroupMessage().orElse(null);
    }

    @Override
    public String getParser() {
        return config.getConfiguration().getParser();
    }

    @Override
    public List<UeiMatch> getUeiList() {
        List<UeiMatch> matches = config.getUeiMatches();
        return matches != null ? matches : Collections.emptyList();
    }

    @Override
    public List<HideMatch> getHideMessages() {
        List<HideMatch> matches = config.getHideMatches();
        return matches != null ? matches : Collections.emptyList();
    }

    @Override
    public String getDiscardUei() {
        return config.getConfiguration().getDiscardUei();
    }

    @Override
    public int getNumThreads() {
        return config.getConfiguration().getThreads()
                .orElse(Runtime.getRuntime().availableProcessors() * 2);
    }

    @Override
    public int getQueueSize() {
        return config.getConfiguration().getQueueSize();
    }

    @Override
    public int getBatchSize() {
        return config.getConfiguration().getBatchSize();
    }

    @Override
    public int getBatchIntervalMs() {
        return config.getConfiguration().getBatchInterval();
    }

    @Override
    public TimeZone getTimeZone() {
        return config.getConfiguration().getTimeZone().orElse(null);
    }

    @Override
    public boolean shouldIncludeRawSyslogmessage() {
        return config.getConfiguration().shouldIncludeRawSyslogmessage();
    }

    @Override
    public void reload() throws IOException {
        // Reload is not supported in the Spring Boot daemon.
        // To reload config, restart the container.
    }
}

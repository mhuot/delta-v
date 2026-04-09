package org.opennms.netmgt.syslogd.boot;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;

import org.junit.jupiter.api.Test;

class LocalDnsLookupClientTest {

    private final LocalDnsLookupClient client = new LocalDnsLookupClient();

    @Test
    void lookupResolvesHostname() throws Exception {
        String result = client.lookup("localhost", "Default").get();
        assertThat(result).isNotNull();
    }

    @Test
    void lookupWithSystemIdDelegates() throws Exception {
        String result = client.lookup("localhost", "Default", "sys-1").get();
        assertThat(result).isNotNull();
    }

    @Test
    void reverseLookupResolvesAddress() throws Exception {
        InetAddress addr = InetAddress.getByName("127.0.0.1");
        String result = client.reverseLookup(addr, "Default").get();
        assertThat(result).isNotNull();
    }

    @Test
    void reverseLookupWithSystemIdDelegates() throws Exception {
        InetAddress addr = InetAddress.getByName("127.0.0.1");
        String result = client.reverseLookup(addr, "Default", "sys-1").get();
        assertThat(result).isNotNull();
    }
}

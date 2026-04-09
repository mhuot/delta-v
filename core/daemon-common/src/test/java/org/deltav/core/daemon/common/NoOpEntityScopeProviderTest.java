package org.opennms.core.daemon.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;

import org.opennms.core.mate.api.Scope;
import org.junit.jupiter.api.Test;

class NoOpEntityScopeProviderTest {

    private final NoOpEntityScopeProvider provider = new NoOpEntityScopeProvider();

    @Test
    void getScopeForNode_returnsEmptyScope() {
        Scope scope = provider.getScopeForNode(42);
        assertThat(scope).isNotNull();
        assertThat(scope.keys()).isEmpty();
    }

    @Test
    void getScopeForScv_returnsEmptyScope() {
        Scope scope = provider.getScopeForScv();
        assertThat(scope).isNotNull();
        assertThat(scope.keys()).isEmpty();
    }

    @Test
    void getScopeForEnv_returnsEmptyScope() {
        Scope scope = provider.getScopeForEnv();
        assertThat(scope).isNotNull();
        assertThat(scope.keys()).isEmpty();
    }

    @Test
    void getScopeForInterface_returnsEmptyScope() {
        Scope scope = provider.getScopeForInterface(1, "192.168.1.1");
        assertThat(scope).isNotNull();
        assertThat(scope.keys()).isEmpty();
    }

    @Test
    void getScopeForInterfaceByIfIndex_returnsEmptyScope() {
        Scope scope = provider.getScopeForInterfaceByIfIndex(1, 5);
        assertThat(scope).isNotNull();
        assertThat(scope.keys()).isEmpty();
    }

    @Test
    void getScopeForService_returnsEmptyScope() throws Exception {
        Scope scope = provider.getScopeForService(1, InetAddress.getByName("192.168.1.1"), "ICMP");
        assertThat(scope).isNotNull();
        assertThat(scope.keys()).isEmpty();
    }
}

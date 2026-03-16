package org.opennms.netmgt.discovery.boot;

import java.net.InetAddress;

import org.opennms.core.mate.api.EmptyScope;
import org.opennms.core.mate.api.EntityScopeProvider;
import org.opennms.core.mate.api.Scope;

/**
 * No-op EntityScopeProvider for Spring Boot daemon containers.
 *
 * <p>Returns empty scopes for all entity types. MATE variable interpolation
 * (e.g., {@code ${requisition:username}}) is disabled.</p>
 *
 * <p><strong>Deferred:</strong> Real MATE support requires scope resolution
 * from the database (node/interface/service attributes).</p>
 */
public class NoOpEntityScopeProvider implements EntityScopeProvider {

    private static final Scope EMPTY = EmptyScope.EMPTY;

    @Override public Scope getScopeForScv() { return EMPTY; }
    @Override public Scope getScopeForEnv() { return EMPTY; }
    @Override public Scope getScopeForNode(Integer nodeId) { return EMPTY; }
    @Override public Scope getScopeForInterface(Integer nodeId, String ipAddress) { return EMPTY; }
    @Override public Scope getScopeForInterfaceByIfIndex(Integer nodeId, int ifIndex) { return EMPTY; }
    @Override public Scope getScopeForService(Integer nodeId, InetAddress ipAddress, String serviceName) { return EMPTY; }
}

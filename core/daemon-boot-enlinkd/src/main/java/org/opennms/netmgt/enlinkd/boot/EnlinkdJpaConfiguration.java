/*
 * Licensed to The OpenNMS Group, Inc (TOG) under one or more
 * contributor license agreements.  See the LICENSE.md file
 * distributed with this work for additional information
 * regarding copyright ownership.
 *
 * TOG licenses this file to You under the GNU Affero General
 * Public License Version 3 (the "License") or (at your option)
 * any later version.  You may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at:
 *
 *      https://www.gnu.org/licenses/agpl-3.0.txt
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the
 * License.
 */
package org.opennms.netmgt.enlinkd.boot;

import org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl;
import org.opennms.netmgt.dao.api.SessionUtils;
import org.opennms.netmgt.enlinkd.model.jakarta.BridgeBridgeLink;
import org.opennms.netmgt.enlinkd.model.jakarta.BridgeElement;
import org.opennms.netmgt.enlinkd.model.jakarta.BridgeMacLink;
import org.opennms.netmgt.enlinkd.model.jakarta.BridgeStpLink;
import org.opennms.netmgt.enlinkd.model.jakarta.CdpElement;
import org.opennms.netmgt.enlinkd.model.jakarta.CdpLink;
import org.opennms.netmgt.enlinkd.model.jakarta.IpNetToMedia;
import org.opennms.netmgt.enlinkd.model.jakarta.IsIsElement;
import org.opennms.netmgt.enlinkd.model.jakarta.IsIsLink;
import org.opennms.netmgt.enlinkd.model.jakarta.LldpElement;
import org.opennms.netmgt.enlinkd.model.jakarta.LldpLink;
import org.opennms.netmgt.enlinkd.model.jakarta.OspfArea;
import org.opennms.netmgt.enlinkd.model.jakarta.OspfElement;
import org.opennms.netmgt.enlinkd.model.jakarta.OspfLink;
import org.opennms.netmgt.enlinkd.model.jakarta.UserDefinedLink;
import org.opennms.netmgt.enlinkd.model.jakarta.dao.BridgeBridgeLinkDaoJpa;
import org.opennms.netmgt.enlinkd.model.jakarta.dao.BridgeElementDaoJpa;
import org.opennms.netmgt.enlinkd.model.jakarta.dao.BridgeMacLinkDaoJpa;
import org.opennms.netmgt.enlinkd.model.jakarta.dao.BridgeStpLinkDaoJpa;
import org.opennms.netmgt.enlinkd.model.jakarta.dao.CdpElementDaoJpa;
import org.opennms.netmgt.enlinkd.model.jakarta.dao.CdpLinkDaoJpa;
import org.opennms.netmgt.enlinkd.model.jakarta.dao.IpNetToMediaDaoJpa;
import org.opennms.netmgt.enlinkd.model.jakarta.dao.IsIsElementDaoJpa;
import org.opennms.netmgt.enlinkd.model.jakarta.dao.IsIsLinkDaoJpa;
import org.opennms.netmgt.enlinkd.model.jakarta.dao.LldpElementDaoJpa;
import org.opennms.netmgt.enlinkd.model.jakarta.dao.LldpLinkDaoJpa;
import org.opennms.netmgt.enlinkd.model.jakarta.dao.OspfAreaDaoJpa;
import org.opennms.netmgt.enlinkd.model.jakarta.dao.OspfElementDaoJpa;
import org.opennms.netmgt.enlinkd.model.jakarta.dao.OspfLinkDaoJpa;
import org.opennms.netmgt.enlinkd.model.jakarta.dao.UserDefinedLinkDaoJpa;
import org.opennms.netmgt.eventd.EventUtil;
import org.opennms.netmgt.model.OnmsCategory;
import org.opennms.netmgt.model.OnmsDistPoller;
import org.opennms.netmgt.model.OnmsIpInterface;
import org.opennms.netmgt.model.OnmsMonitoringSystem;
import org.opennms.netmgt.model.OnmsNode;
import org.opennms.netmgt.model.OnmsSnmpInterface;
import org.opennms.netmgt.model.monitoringLocations.OnmsMonitoringLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Spring Boot configuration for Enlinkd JPA entities, DAOs, and infrastructure beans.
 *
 * <p>Enlinkd requires the core node/interface entities plus all 15 link discovery
 * entities (CDP, LLDP, OSPF, IS-IS, Bridge, IpNetToMedia, UserDefinedLink).
 * Entity classes are listed explicitly via {@link PersistenceManagedTypes} to avoid
 * scanning legacy javax.persistence classes that are incompatible with Hibernate 7.</p>
 */
@Configuration
@EnableTransactionManagement
public class EnlinkdJpaConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(EnlinkdJpaConfiguration.class);

    // ===================================================================
    // Section 1: JPA / Naming
    // ===================================================================

    @Bean
    public PhysicalNamingStrategyStandardImpl physicalNamingStrategy() {
        return new PhysicalNamingStrategyStandardImpl();
    }

    /**
     * Explicitly lists all Jakarta entity classes needed by Enlinkd:
     * core entities (Node, IpInterface, SnmpInterface, MonitoringLocation,
     * DistPoller, MonitoringSystem, Category) plus all 15 Enlinkd entities.
     */
    @Bean
    public PersistenceManagedTypes persistenceManagedTypes() {
        return PersistenceManagedTypes.of(
            // Core entities
            OnmsNode.class.getName(),
            OnmsIpInterface.class.getName(),
            OnmsSnmpInterface.class.getName(),
            OnmsMonitoringLocation.class.getName(),
            OnmsDistPoller.class.getName(),
            OnmsMonitoringSystem.class.getName(),
            OnmsCategory.class.getName(),
            // Enlinkd entities (Jakarta)
            CdpLink.class.getName(),
            CdpElement.class.getName(),
            LldpLink.class.getName(),
            LldpElement.class.getName(),
            OspfLink.class.getName(),
            OspfElement.class.getName(),
            OspfArea.class.getName(),
            IsIsLink.class.getName(),
            IsIsElement.class.getName(),
            IpNetToMedia.class.getName(),
            BridgeBridgeLink.class.getName(),
            BridgeMacLink.class.getName(),
            BridgeStpLink.class.getName(),
            BridgeElement.class.getName(),
            UserDefinedLink.class.getName()
        );
    }

    // ===================================================================
    // Section 2: Transaction / SessionUtils
    // ===================================================================

    @Bean
    public SessionUtils sessionUtils(PlatformTransactionManager txManager) {
        var txTemplate = new TransactionTemplate(txManager);
        var readOnlyTxTemplate = new TransactionTemplate(txManager);
        readOnlyTxTemplate.setReadOnly(true);
        return new SessionUtils() {
            @Override
            public <V> V withTransaction(java.util.function.Supplier<V> supplier) {
                return txTemplate.execute(status -> supplier.get());
            }
            @Override
            public <V> V withReadOnlyTransaction(java.util.function.Supplier<V> supplier) {
                return readOnlyTxTemplate.execute(status -> supplier.get());
            }
            @Override
            public <V> V withManualFlush(java.util.function.Supplier<V> supplier) {
                return supplier.get();
            }
        };
    }

    @Bean
    public TransactionTemplate transactionTemplate(PlatformTransactionManager txManager) {
        return new TransactionTemplate(txManager);
    }

    // ===================================================================
    // Section 3: Enlinkd JPA DAOs (15 beans)
    // ===================================================================

    @Bean
    public CdpLinkDaoJpa cdpLinkDaoJpa() {
        return new CdpLinkDaoJpa();
    }

    @Bean
    public CdpElementDaoJpa cdpElementDaoJpa() {
        return new CdpElementDaoJpa();
    }

    @Bean
    public LldpLinkDaoJpa lldpLinkDaoJpa() {
        return new LldpLinkDaoJpa();
    }

    @Bean
    public LldpElementDaoJpa lldpElementDaoJpa() {
        return new LldpElementDaoJpa();
    }

    @Bean
    public OspfLinkDaoJpa ospfLinkDaoJpa() {
        return new OspfLinkDaoJpa();
    }

    @Bean
    public OspfElementDaoJpa ospfElementDaoJpa() {
        return new OspfElementDaoJpa();
    }

    @Bean
    public OspfAreaDaoJpa ospfAreaDaoJpa() {
        return new OspfAreaDaoJpa();
    }

    @Bean
    public IsIsLinkDaoJpa isisLinkDaoJpa() {
        return new IsIsLinkDaoJpa();
    }

    @Bean
    public IsIsElementDaoJpa isisElementDaoJpa() {
        return new IsIsElementDaoJpa();
    }

    @Bean
    public IpNetToMediaDaoJpa ipNetToMediaDaoJpa() {
        return new IpNetToMediaDaoJpa();
    }

    @Bean
    public BridgeElementDaoJpa bridgeElementDaoJpa() {
        return new BridgeElementDaoJpa();
    }

    @Bean
    public BridgeBridgeLinkDaoJpa bridgeBridgeLinkDaoJpa() {
        return new BridgeBridgeLinkDaoJpa();
    }

    @Bean
    public BridgeMacLinkDaoJpa bridgeMacLinkDaoJpa() {
        return new BridgeMacLinkDaoJpa();
    }

    @Bean
    public BridgeStpLinkDaoJpa bridgeStpLinkDaoJpa() {
        return new BridgeStpLinkDaoJpa();
    }

    @Bean
    public UserDefinedLinkDaoJpa userDefinedLinkDaoJpa() {
        return new UserDefinedLinkDaoJpa();
    }

    // ===================================================================
    // Section 4: No-op stubs
    // ===================================================================

    @Bean
    public EventUtil eventUtil() {
        return new EventUtil() {
            @Override public String expandParms(String inp, org.opennms.netmgt.xml.event.Event event) { return inp; }
            @Override public String expandParms(String inp, org.opennms.netmgt.xml.event.Event event, java.util.Map<String, java.util.Map<String, String>> decode) { return inp; }
            @Override public String getNamedParmValue(String string, org.opennms.netmgt.xml.event.Event event) { return ""; }
            @Override public void expandMapValues(java.util.Map<String, String> parmMap, org.opennms.netmgt.xml.event.Event event) {}
            @Override public String getHardwareFieldValue(String parm, long nodeId) { return ""; }
            @Override public String getHostName(int nodeId, String hostip) { return hostip; }
            @Override public String getEventHost(org.opennms.netmgt.xml.event.Event event) { return ""; }
            @Override public String getIfAlias(long nodeId, String ipAddr) { return ""; }
            @Override public String getAssetFieldValue(String parm, long nodeId) { return ""; }
            @Override public String getForeignId(long nodeId) { return ""; }
            @Override public String getForeignSource(long nodeId) { return ""; }
            @Override public String getNodeLabel(long nodeId) { return ""; }
            @Override public String getNodeLocation(long nodeId) { return ""; }
            @Override public org.opennms.netmgt.eventd.processor.expandable.ExpandableParameterResolver getResolver(String token) { return null; }
            @Override public java.util.Date decodeSnmpV2TcDateAndTime(java.math.BigInteger value) { return new java.util.Date(); }
            @Override public String getPrimaryInterface(long nodeId) { return ""; }
        };
    }
}

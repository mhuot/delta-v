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
package org.opennms.netmgt.alarmd.boot;

import java.util.List;

import org.opennms.core.daemon.common.DaemonSmartLifecycle;
import org.opennms.netmgt.alarmd.Alarmd;
import org.opennms.netmgt.alarmd.AlarmLifecycleListenerManager;
import org.opennms.netmgt.alarmd.AlarmPersister;
import org.opennms.netmgt.alarmd.AlarmPersisterImpl;
import org.opennms.netmgt.alarmd.NorthbounderManager;
import org.opennms.netmgt.events.api.AnnotationBasedEventListenerAdapter;
import org.opennms.netmgt.events.api.EventSubscriptionService;
import org.opennms.netmgt.model.AlarmAssociation;
import org.opennms.netmgt.model.OnmsAlarm;
import org.opennms.netmgt.model.OnmsCategory;
import org.opennms.netmgt.model.OnmsDistPoller;
import org.opennms.netmgt.model.OnmsIpInterface;
import org.opennms.netmgt.model.OnmsMemo;
import org.opennms.netmgt.model.OnmsMonitoredService;
import org.opennms.netmgt.model.OnmsMonitoringSystem;
import org.opennms.netmgt.model.OnmsNode;
import org.opennms.netmgt.model.OnmsReductionKeyMemo;
import org.opennms.netmgt.model.OnmsServiceType;
import org.opennms.netmgt.model.OnmsSnmpInterface;
import org.opennms.netmgt.model.monitoringLocations.OnmsMonitoringLocation;
import org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;

/**
 * Spring Boot @Configuration that wires all Alarmd beans.
 *
 * <p>This replaces the Karaf-era {@code applicationContext-daemon-loader-alarmd.xml}.
 * Beans that depend on DAOs and other infrastructure services (EventUtil,
 * AlarmEntityNotifier, SessionUtils, EventProxy, etc.) receive those dependencies
 * via @Autowired field injection in the existing classes. The actual DAO beans
 * are expected to be provided by a separate configuration (e.g., JPA auto-config
 * or a dedicated DAO configuration class).</p>
 *
 * <p>The {@link AnnotationBasedEventListenerAdapter} bridges Alarmd's
 * {@code @EventHandler}-annotated methods to the {@link EventSubscriptionService},
 * registering Alarmd as an event listener during {@code afterPropertiesSet()}.</p>
 *
 * <p>Entity classes are listed explicitly via a custom {@link PersistenceManagedTypes}
 * bean instead of using package-based {@code @EntityScan} because the legacy
 * opennms-model module shares the same package ({@code org.opennms.netmgt.model})
 * and contains classes with incompatible javax.persistence / Hibernate 3.x
 * annotations that cause scanning failures with Hibernate 7.</p>
 */
@Configuration
public class AlarmdConfiguration {

    /**
     * Use standard JPA naming — table/column names from @Table/@Column annotations
     * are used as-is, without Spring Boot's default CamelCase→snake_case conversion.
     * This is required because the OpenNMS schema uses camelCase table names
     * (e.g., monitoringSystems, ipInterface, ifServices).
     */
    @Bean
    public PhysicalNamingStrategyStandardImpl physicalNamingStrategy() {
        return new PhysicalNamingStrategyStandardImpl();
    }

    /**
     * Explicitly lists the Jakarta entity classes to register with Hibernate 7.
     * This replaces {@code @EntityScan(basePackages = "org.opennms.netmgt.model")}
     * which would scan ALL classes in the package, including legacy entities
     * with incompatible javax.persistence annotations.
     */
    @Bean
    public PersistenceManagedTypes persistenceManagedTypes() {
        return PersistenceManagedTypes.of(
            OnmsAlarm.class.getName(),
            AlarmAssociation.class.getName(),
            OnmsCategory.class.getName(),
            OnmsDistPoller.class.getName(),
            OnmsIpInterface.class.getName(),
            OnmsMemo.class.getName(),
            OnmsMonitoredService.class.getName(),
            OnmsMonitoringSystem.class.getName(),
            OnmsNode.class.getName(),
            OnmsReductionKeyMemo.class.getName(),
            OnmsServiceType.class.getName(),
            OnmsSnmpInterface.class.getName(),
            OnmsMonitoringLocation.class.getName()
        );
    }

    @Bean
    public AlarmPersisterImpl alarmPersister() {
        return new AlarmPersisterImpl();
    }

    @Bean
    public AlarmLifecycleListenerManager alarmLifecycleListenerManager() {
        return new AlarmLifecycleListenerManager();
    }

    @Bean
    public NorthbounderManager northbounderManager() {
        return new NorthbounderManager();
    }

    @Bean
    public Alarmd alarmd(AlarmPersister alarmPersister) {
        var alarmd = new Alarmd();
        alarmd.setPersister(alarmPersister);
        return alarmd;
    }

    @Bean
    public AnnotationBasedEventListenerAdapter alarmdEventListenerAdapter(
            Alarmd alarmd,
            EventSubscriptionService eventSubscriptionService) {
        var adapter = new AnnotationBasedEventListenerAdapter();
        adapter.setAnnotatedListener(alarmd);
        adapter.setEventSubscriptionService(eventSubscriptionService);
        return adapter;
    }

    @Bean
    public SmartLifecycle alarmdLifecycle(Alarmd alarmd) {
        return new DaemonSmartLifecycle(alarmd);
    }
}

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
package org.opennms.netmgt.model;

import java.io.Serializable;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import jakarta.persistence.Transient;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.hibernate.annotations.Cascade;
import org.opennms.core.utils.InetAddressUtils;
import org.opennms.netmgt.events.api.EventForwarder;
import org.opennms.netmgt.model.jakarta.converter.InetAddressConverter;

import com.google.common.base.MoreObjects;

/**
 * <p>OnmsIpInterface class.</p>
 */
@Entity
@Table(name="ipInterface")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class OnmsIpInterface extends OnmsEntity implements Serializable {
    private static final long serialVersionUID = 8463903013592837114L;

    private Integer m_id;

    private InetAddress m_ipAddress;

    private InetAddress m_netMask;

    private String m_ipHostName;

    private String m_isManaged;

    private PrimaryType m_isSnmpPrimary = PrimaryType.NOT_ELIGIBLE;

    private Date m_ipLastCapsdPoll;

    private OnmsNode m_node;

    private Set<OnmsMonitoredService> m_monitoredServices = new LinkedHashSet<>();

    private OnmsSnmpInterface m_snmpInterface;

    private List<OnmsMetaData> m_metaData = new ArrayList<>();

    private List<OnmsMetaData> m_requisitionedMetaData = new ArrayList<>();

    /**
     * <p>Constructor for OnmsIpInterface.</p>
     */
    public OnmsIpInterface() {
    }

    /**
     * minimal constructor
     * @deprecated Use the {@link InetAddress} version instead.
     * @param ipAddr a {@link java.lang.String} object.
     * @param node a {@link org.opennms.netmgt.model.OnmsNode} object.
     */
    public OnmsIpInterface(String ipAddr, OnmsNode node) {
        this(InetAddressUtils.getInetAddress(ipAddr), node);
    }

    /**
     * minimal constructor
     *
     * @param ipAddr a {@link java.net.InetAddress} object.
     * @param node a {@link org.opennms.netmgt.model.OnmsNode} object.
     */
    public OnmsIpInterface(InetAddress ipAddr, OnmsNode node) {
        m_ipAddress = ipAddr;
        m_node = node;
        if (node != null) {
            node.getIpInterfaces().add(this);
        }
    }

    /**
     * Unique identifier for ipInterface.
     *
     * @return a {@link java.lang.Integer} object.
     */
    @Id
    @Column(nullable=false)
    @SequenceGenerator(name="opennmsSequence", sequenceName="opennmsNxtId", allocationSize = 1)
    @GeneratedValue(generator="opennmsSequence")
    public Integer getId() {
        return m_id;
    }

    /**
     * <p>setId</p>
     *
     * @param id a {@link java.lang.Integer} object.
     */
    public void setId(Integer id) {
        m_id = id;
    }

    /**
     * <p>getInterfaceId</p>
     *
     * @return a {@link java.lang.String} object.
     */
    @Transient
    public String getInterfaceId() {
        return getId() == null? null : getId().toString();
    }

    public void setInterfaceId(final String id) {
        setId(Integer.valueOf(id));
    }

    @Transient
    public Date getLastIngressFlow() {
        if (m_snmpInterface == null) {
            return null;
        }
        return m_snmpInterface.getLastIngressFlow();
    }

    @Transient
    public Date getLastEgressFlow() {
        if (m_snmpInterface == null) {
            return null;
        }
        return m_snmpInterface.getLastEgressFlow();
    }

    /**
     * <p>getIpAddressAsString</p>
     *
     * @return a {@link java.lang.String} object.
     * @deprecated
     */
    @Transient
    public String getIpAddressAsString() {
        return InetAddressUtils.toIpAddrString(m_ipAddress);
    }

    /**
     * <p>getIfIndex</p>
     *
     * @return a {@link java.lang.Integer} object.
     */
    @Transient
    public Integer getIfIndex() {
        if (m_snmpInterface == null) {
            return null;
        }
        return m_snmpInterface.getIfIndex();
    }

    /**
     * <p>setIfIndex</p>
     *
     * @param ifindex a {@link java.lang.Integer} object.
     */
    public void setIfIndex(Integer ifindex) {
        if (m_snmpInterface == null) {
            throw new IllegalStateException("Cannot set ifIndex if snmpInterface relation isn't setup");
        }
        m_snmpInterface.setIfIndex(ifindex);
    }

    /**
     * <p>getIpHostName</p>
     *
     * @return a {@link java.lang.String} object.
     */
    @Column(name="ipHostName", length=256)
    public String getIpHostName() {
        return m_ipHostName;
    }

    /**
     * <p>setIpHostName</p>
     *
     * @param iphostname a {@link java.lang.String} object.
     */
    public void setIpHostName(String iphostname) {
        m_ipHostName = iphostname;
    }

    /**
     * <p>getIsManaged</p>
     *
     * @return a {@link java.lang.String} object.
     */
    @Column(name="isManaged", length=1)
    public String getIsManaged() {
        return m_isManaged;
    }

    /**
     * <p>setIsManaged</p>
     *
     * @param ismanaged a {@link java.lang.String} object.
     */
    public void setIsManaged(String ismanaged) {
        m_isManaged = ismanaged;
    }

    /**
     * <p>isManaged</p>
     *
     * @return a boolean.
     */
    @Transient
    public boolean isManaged() {
        return "M".equals(getIsManaged());
    }

    /**
     * <p>getIpLastCapsdPoll</p>
     *
     * @return a {@link java.util.Date} object.
     */
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name="ipLastCapsdPoll")
    public Date getIpLastCapsdPoll() {
        return m_ipLastCapsdPoll;
    }

    /**
     * <p>setIpLastCapsdPoll</p>
     *
     * @param iplastcapsdpoll a {@link java.util.Date} object.
     */
    public void setIpLastCapsdPoll(Date iplastcapsdpoll) {
        m_ipLastCapsdPoll = iplastcapsdpoll;
    }

    /**
     * Returns the snmpPrimary value as a single character code.
     * The CharacterUserType is replaced by the getter/setter logic — no @Convert needed
     * since this getter returns a String directly.
     */
    @Column(name="isSnmpPrimary", length=1)
    public String getSnmpPrimary() {
        final PrimaryType type = m_isSnmpPrimary == null? PrimaryType.NOT_ELIGIBLE : m_isSnmpPrimary;
        return type.getCode();
    }

    public void setSnmpPrimary(final String primary) {
        this.m_isSnmpPrimary = PrimaryType.get(primary);
    }

    /**
     * <p>getIsSnmpPrimary</p>
     *
     * @return a {@link org.opennms.netmgt.model.PrimaryType} object.
     */
    @Transient
    public PrimaryType getIsSnmpPrimary() {
        return m_isSnmpPrimary == null? PrimaryType.NOT_ELIGIBLE : m_isSnmpPrimary;
    }

    /**
     * <p>setIsSnmpPrimary</p>
     *
     * @param issnmpprimary a {@link org.opennms.netmgt.model.PrimaryType} object.
     */
    public void setIsSnmpPrimary(PrimaryType issnmpprimary) {
        m_isSnmpPrimary = issnmpprimary;
    }

    /**
     * <p>isPrimary</p>
     *
     * @return a boolean.
     */
    @Transient
    public boolean isPrimary(){
        return PrimaryType.PRIMARY.equals(m_isSnmpPrimary);
    }

    @Transient
    public List<OnmsMetaData> getRequisitionedMetaData() {
        return m_requisitionedMetaData;
    }

    public void setRequisionedMetaData(final List<OnmsMetaData> requisitionedMetaData) {
        m_requisitionedMetaData = requisitionedMetaData;
    }

    public void addRequisionedMetaData(final OnmsMetaData onmsMetaData) {
        m_requisitionedMetaData.add(onmsMetaData);
    }

    @JsonIgnore
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name="ipInterface_metadata", joinColumns = @JoinColumn(name = "id"))
    public List<OnmsMetaData> getMetaData() {
        return m_metaData;
    }

    public void setMetaData(final List<OnmsMetaData> metaData) {
        m_metaData = metaData;
    }

    public void addMetaData(final String context, final String key, final String value) {
        Objects.requireNonNull(context);
        Objects.requireNonNull(key);
        Objects.requireNonNull(value);

        final Optional<OnmsMetaData> entry = getMetaData().stream()
                .filter(m -> m.getContext().equals(context))
                .filter(m -> m.getKey().equals(key))
                .findFirst();

        // Update the value if present, otherwise create a new entry
        if (entry.isPresent()) {
            entry.get().setValue(value);
        } else {
            getMetaData().add(new OnmsMetaData(context, key, value));
        }
    }

    public void removeMetaData(final String context, final String key) {
        Objects.requireNonNull(context);
        Objects.requireNonNull(key);
        final Iterator<OnmsMetaData> iterator = getMetaData().iterator();

        while (iterator.hasNext()) {
            final OnmsMetaData onmsNodeMetaData = iterator.next();

            if (context.equals(onmsNodeMetaData.getContext()) && key.equals(onmsNodeMetaData.getKey())) {
                iterator.remove();
            }
        }
    }

    public void removeMetaData(final String context) {
        Objects.requireNonNull(context);
        final Iterator<OnmsMetaData> iterator = getMetaData().iterator();

        while (iterator.hasNext()) {
            final OnmsMetaData onmsNodeMetaData = iterator.next();

            if (context.equals(onmsNodeMetaData.getContext())) {
                iterator.remove();
            }
        }
    }

    /**
     * <p>getNode</p>
     *
     * @return a {@link org.opennms.netmgt.model.OnmsNode} object.
     */
    @ManyToOne(optional=false, fetch=FetchType.LAZY)
    @JoinColumn(name="nodeId")
    public OnmsNode getNode() {
        return m_node;
    }

    /**
     * <p>setNode</p>
     *
     * @param node a {@link org.opennms.netmgt.model.OnmsNode} object.
     */
    public void setNode(org.opennms.netmgt.model.OnmsNode node) {
        m_node = node;
    }

    @Transient
    public Integer getNodeId() {
        if (m_node != null) {
            return m_node.getId();
        }
        return null;
    }

    /**
     * The services on this interface
     *
     * @return a {@link java.util.Set} object.
     */
    @OneToMany(mappedBy="ipInterface",orphanRemoval=true)
    @Cascade(org.hibernate.annotations.CascadeType.ALL)
    public Set<OnmsMonitoredService> getMonitoredServices() {
        return m_monitoredServices ;
    }

    /**
     * <p>setMonitoredServices</p>
     *
     * @param ifServices a {@link java.util.Set} object.
     */
    public void setMonitoredServices(Set<OnmsMonitoredService> ifServices) {
        m_monitoredServices = ifServices;
    }

    public void addMonitoredService(final OnmsMonitoredService svc) {
        m_monitoredServices.add(svc);
    }

    public void removeMonitoredService(final OnmsMonitoredService svc) {
        m_monitoredServices.remove(svc);
    }

    /**
     * The SnmpInterface associated with this interface if any
     *
     * @return a {@link org.opennms.netmgt.model.OnmsSnmpInterface} object.
     */
    @ManyToOne(optional=true, fetch=FetchType.LAZY)
    @JoinColumn(name="snmpInterfaceId")
    public OnmsSnmpInterface getSnmpInterface() {
        return m_snmpInterface;
    }

    /**
     * <p>setSnmpInterface</p>
     *
     * @param snmpInterface a {@link org.opennms.netmgt.model.OnmsSnmpInterface} object.
     */
    public void setSnmpInterface(OnmsSnmpInterface snmpInterface) {
        m_snmpInterface = snmpInterface;
    }

    /**
     * <p>toString</p>
     *
     * @return a {@link java.lang.String} object.
     */
    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
        .add("id", m_id)
        .add("ipAddr", InetAddressUtils.str(m_ipAddress))
        .add("netMask", InetAddressUtils.str(m_netMask))
        .add("ipHostName", m_ipHostName)
        .add("isManaged", m_isManaged)
        .add("snmpPrimary", m_isSnmpPrimary)
        .add("ipLastCapsdPoll", m_ipLastCapsdPoll)
        .add("nodeId", getNodeId())
        .toString();
    }

    /**
     * <p>getIpAddress</p>
     *
     * @return a {@link java.net.InetAddress} object.
     */
    @Column(name="ipAddr")
    @Convert(converter = InetAddressConverter.class)
    public InetAddress getIpAddress() {
        return m_ipAddress;
    }

    /**
     * <p>setIpAddress</p>
     *
     * @param ipaddr a {@link java.net.InetAddress} object.
     */
    public void setIpAddress(InetAddress ipaddr) {
        m_ipAddress = ipaddr;
    }

    @Column(name = "netmask")
    @Convert(converter = InetAddressConverter.class)
    public InetAddress getNetMask() {
        return m_netMask;
    }

    public void setNetMask(final InetAddress netMask) {
        m_netMask = netMask;
    }

    /**
     * <p>isDown</p>
     *
     * @return a boolean.
     */
    @Transient
    public boolean isDown() {
        boolean down = true;
        for (OnmsMonitoredService svc : m_monitoredServices) {
            if (!svc.isDown()) {
                return !down;
            }
        }
        return down;
    }

    @Transient
    public int getMonitoredServiceCount() {
        return m_monitoredServices.size();
    }

    /**
     * <p>getMonitoredServiceByServiceType</p>
     *
     * @param svcName a {@link java.lang.String} object.
     * @return a {@link org.opennms.netmgt.model.OnmsMonitoredService} object.
     */
    @JsonIgnore
    public OnmsMonitoredService getMonitoredServiceByServiceType(String svcName) {
        for (OnmsMonitoredService monSvc : getMonitoredServices()) {
            if (monSvc.getServiceType().getName().equals(svcName)) {
                return monSvc;
            }
        }
        return null;
    }

    /**
     * <p>mergeInterfaceAttributes</p>
     *
     * @param scannedIface a {@link org.opennms.netmgt.model.OnmsIpInterface} object.
     */
    public void mergeInterfaceAttributes(OnmsIpInterface scannedIface) {

        if (hasNewValue(scannedIface.getIfIndex(), getIfIndex())) {
            setIfIndex(scannedIface.getIfIndex());
        }

        if (hasNewValue(scannedIface.getNetMask(), getNetMask())) {
            setNetMask(scannedIface.getNetMask());
        }

        if (hasNewValue(scannedIface.getIsManaged(), getIsManaged())) {
            setIsManaged(scannedIface.getIsManaged());
        }

        if (hasNewCollectionTypeValue(scannedIface.getIsSnmpPrimary(), getIsSnmpPrimary())) {
            setIsSnmpPrimary(scannedIface.getIsSnmpPrimary());
        }

        if (hasNewValue(scannedIface.getIpHostName(), getIpHostName())) {
            setIpHostName(scannedIface.getIpHostName());
        }

        if (hasNewValue(scannedIface.getIpLastCapsdPoll(), getIpLastCapsdPoll())) {
            setIpLastCapsdPoll(scannedIface.getIpLastCapsdPoll());
        }
    }

    /**
     * <p>hasNewCollectionTypeValue</p>
     *
     * @param newVal a {@link org.opennms.netmgt.model.PrimaryType} object.
     * @param existingVal a {@link org.opennms.netmgt.model.PrimaryType} object.
     * @return a boolean.
     */
    protected static boolean hasNewCollectionTypeValue(PrimaryType newVal, PrimaryType existingVal) {
        return newVal != null && !newVal.equals(existingVal) && newVal != PrimaryType.NOT_ELIGIBLE;
    }

    /**
     * <p>mergeMonitoredServices</p>
     *
     * @param scannedIface a {@link org.opennms.netmgt.model.OnmsIpInterface} object.
     * @param eventForwarder a {@link org.opennms.netmgt.events.api.EventForwarder} object.
     * @param deleteMissing a boolean.
     */
    public void mergeMonitoredServices(OnmsIpInterface scannedIface, EventForwarder eventForwarder, boolean deleteMissing) {

        // create map of services to serviceType
        Map<OnmsServiceType, OnmsMonitoredService> serviceTypeMap = new HashMap<OnmsServiceType, OnmsMonitoredService>();
        for (OnmsMonitoredService svc : scannedIface.getMonitoredServices()) {
            serviceTypeMap.put(svc.getServiceType(), svc);
        }

        // for each service in the database
        for (Iterator<OnmsMonitoredService> it = getMonitoredServices().iterator(); it.hasNext();) {
            OnmsMonitoredService svc = it.next();

            // find the corresponding scanned service
            OnmsMonitoredService imported = serviceTypeMap.get(svc.getServiceType());
            if (imported == null) {
                if (deleteMissing) {
                    // there is no scanned service... delete it from the database
                    it.remove();
                    // Visitor-based event firing removed — jakarta entities drop visit()
                }
            }
            else {
                // otherwise update the service attributes
                svc.mergeServiceAttributes(imported);
                svc.mergeMetaData(imported);
            }

            // mark the service is updated
            serviceTypeMap.remove(svc.getServiceType());
        }

        // for any services not found in the database, add them
        Collection<OnmsMonitoredService> newServices = serviceTypeMap.values();
        for (OnmsMonitoredService svc : newServices) {
            svc.setIpInterface(this);
            getMonitoredServices().add(svc);
            // Visitor-based event firing removed — jakarta entities drop visit()
        }
    }

    public void mergeMetaData(OnmsIpInterface scanned) {
        if (!getMetaData().equals(scanned.getMetaData())) {
            setMetaData(scanned.getMetaData());
        }
    }

    /**
     * <p>updateSnmpInterface</p>
     *
     * @param scannedIface a {@link org.opennms.netmgt.model.OnmsIpInterface} object.
     */
    public void updateSnmpInterface(OnmsIpInterface scannedIface) {

        if (!hasNewValue(scannedIface.getIfIndex(), getIfIndex())) {
            /* no ifIndex in currently scanned interface so don't bother
             * we must have failed to collect data
             */
            return;
        }

        if (scannedIface.getSnmpInterface() == null) {
            // there is no longer an snmpInterface associated with the ipInterface
            setSnmpInterface(null);
        } else {
            // locate the snmpInterface on this node that has the new ifIndex and set it
            // into the interface
            OnmsSnmpInterface snmpIface = getNode().getSnmpInterfaceWithIfIndex(scannedIface.getIfIndex());
            setSnmpInterface(snmpIface);
        }
    }

    /**
     * <p>mergeInterface</p>
     *
     * @param scannedIface a {@link org.opennms.netmgt.model.OnmsIpInterface} object.
     * @param eventForwarder a {@link org.opennms.netmgt.events.api.EventForwarder} object.
     * @param deleteMissing a boolean.
     */
    public void mergeInterface(OnmsIpInterface scannedIface, EventForwarder eventForwarder, boolean deleteMissing) {
        updateSnmpInterface(scannedIface);
        mergeInterfaceAttributes(scannedIface);
        mergeMonitoredServices(scannedIface, eventForwarder, deleteMissing);
        mergeMetaData(scannedIface);
    }

    @Transient
    @JsonIgnore
    public String getForeignSource() {
        if (getNode() != null) {
            return getNode().getForeignSource();
        }
        return null;
    }

    @Transient
    @JsonIgnore
    public String getForeignId() {
        if (getNode() != null) {
            return getNode().getForeignId();
        }
        return null;
    }
}

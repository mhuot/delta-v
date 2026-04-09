/*
 * Copyright (C) 1999-2024 The OpenNMS Group, Inc.
 * Copyright (C) 2026 BeaconStrategists, Inc. (Modifications)
 *
 * This file is part of OpenNMS(R) / Delta-V.
 *
 * OpenNMS(R) is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.opennms.netmgt.model;

import java.io.Serializable;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.DiscriminatorType;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import jakarta.persistence.Transient;

import org.hibernate.annotations.DiscriminatorOptions;

import com.google.common.base.MoreObjects;

/**
 * <p>Represents an OpenNMS monitoring system that can poll status of nodes
 * and report events that occur on the network. Examples of monitoring systems
 * include:</p>
 *
 * <ul>
 * <li>OpenNMS</li>
 * <li>OpenNMS Remote Poller</li>
 * <li>OpenNMS Minion</li>
 * </ul>
 *
 * <p>CAUTION: Don't add final modifiers to methods here because they need to be
 * proxyable to the child classes and Javassist doesn't override final methods.
 *
 * @author Seth
 */
@Entity
@Table(name="monitoringSystems")
@Inheritance(strategy=InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(
    name="type",
    discriminatorType=DiscriminatorType.STRING
)
@DiscriminatorValue("System")
// Require all objects to have a discriminator type
@DiscriminatorOptions(force=true)
public class OnmsMonitoringSystem implements Serializable {

    private static final long serialVersionUID = -5095710111103727832L;

    public static final String TYPE_OPENNMS = "OpenNMS";
    public static final String TYPE_MINION = "Minion";
    public static final String TYPE_SENTINEL = "Sentinel";

    private String m_id;

    private String m_label;

    private String m_location;

    private String m_type;

    private Date m_lastUpdated;

    private Date m_lastCheckedIn;

    private Map<String,String> m_properties = new HashMap<String,String>();

    /**
     * default constructor
     */
    public OnmsMonitoringSystem() {}

    /**
     * Minimal constructor.
     *
     * @param id a {@link java.lang.String} object.
     * @param location a {@link java.lang.String} object.
     */
    public OnmsMonitoringSystem(String id, String location) {
        m_id = id;
        m_location = location;
    }

    /**
     * A human-readable name for each system.
     * Typically, the system's hostname (not fully qualified).
     *
     * @return a {@link java.lang.String} object.
     */
    @Id
    @Column(name="id", nullable=false)
    public String getId() {
        return m_id;
    }

    /**
     * <p>setId</p>
     *
     * @param id a {@link java.lang.String} object.
     */
    public void setId(String id) {
        m_id = id;
    }

    /**
     * A human-readable name for each system.
     * Typically, the system's hostname (not fully qualified).
     *
     * @return a {@link java.lang.String} object.
     */
    @Column(name="label")
    public String getLabel() {
        return m_label;
    }

    /**
     * @param label a {@link java.lang.String} object.
     */
    public void setLabel(String label) {
        m_label = label;
    }

    /**
     * The monitoring location that this system is located in.
     *
     * @return a {@link java.lang.String} object.
     */
    @Column(name="location", nullable=false)
    public String getLocation() {
        return m_location;
    }

    /**
     * @param location a {@link java.lang.String} object.
     */
    public void setLocation(String location) {
        m_location = location;
    }

    /**
     * The type of monitoring system. Mark this as insertable=false and updatable=false
     * because it is also used as the @DiscriminatorColumn.
     *
     * @return a {@link java.lang.String} object.
     */
    @Column(name="type", nullable=false, insertable=false, updatable=false)
    public String getType() {
        return m_type;
    }

    /**
     * @param type a {@link java.lang.String} object.
     */
    public void setType(String type) {
        m_type = type;
    }

    /**
     * The timestamp of the last message passed from the remote system.
     *
     * @return a {@link java.util.Date} object.
     */
    @Column(name="last_updated")
    @Temporal(TemporalType.TIMESTAMP)
    public Date getLastUpdated() {
        return m_lastUpdated;
    }

    public void setLastUpdated(final Date lastUpdated) {
        m_lastUpdated = lastUpdated;
    }

    @Transient
    public Date getLastCheckedIn() {
        return m_lastCheckedIn;
    }

    public void setLastCheckedIn(final Date lastCheckedIn) {
        m_lastCheckedIn = lastCheckedIn;
    }

    @ElementCollection
    @JoinTable(name="monitoringSystemsProperties", joinColumns = @JoinColumn(name="monitoringSystemId"))
    @MapKeyColumn(name="property", nullable=false)
    @Column(name="propertyValue")
    public Map<String, String> getProperties() {
        return m_properties;
    }

    /**
     * @param properties a {@link java.util.Map} object.
     */
    public void setProperties(Map<String, String> properties) {
        m_properties = properties;
    }

    public void setProperty(String property, String value) {
        m_properties.put(property, value);
    }

    /**
     * <p>toString</p>
     *
     * @return a {@link java.lang.String} object.
     */
    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("id", getId())
            .add("label", getLabel())
            .add("location", getLocation())
            .add("type", getType())
            .toString();
    }
}

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

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.common.base.MoreObjects;


/**
 * <p>OnmsServiceType class.</p>
 *
 * @hibernate.class table="service"
 */
@Entity
@Table(name="service")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class OnmsServiceType implements Serializable {

    private static final long serialVersionUID = -459218937667452586L;

    /** identifier field */
    private Integer m_id;

    /** persistent field */
    private String m_name;

    /**
     * full constructor
     *
     * @param servicename a {@link java.lang.String} object.
     */
    public OnmsServiceType(String servicename) {
        m_name = servicename;
    }

    public OnmsServiceType(Integer id, String servicename) {
        m_id = id;
        m_name = servicename;
    }

    /**
     * default constructor
     */
    public OnmsServiceType() {
    }

    /**
     * <p>getId</p>
     *
     * @return a {@link java.lang.Integer} object.
     */
    @Id
    @Column(name="serviceId")
    @SequenceGenerator(name="serviceTypeSequence", sequenceName="serviceNxtId", allocationSize = 1)
    @GeneratedValue(generator="serviceTypeSequence")
    public Integer getId() {
        return m_id;
    }

    /**
     * <p>setId</p>
     *
     * @param serviceid a {@link java.lang.Integer} object.
     */
    public void setId(Integer serviceid) {
        m_id = serviceid;
    }

    /**
     * <p>getName</p>
     *
     * @return a {@link java.lang.String} object.
     */
    @Column(name="serviceName", nullable=false, unique=true, length=255)
    public String getName() {
        return m_name;
    }

    /**
     * <p>setName</p>
     *
     * @param name a {@link java.lang.String} object.
     */
    public void setName(String name) {
        m_name = name;
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
            .add("name", getName())
            .toString();
    }

    /** {@inheritDoc} */
    @Override
    public boolean equals(final Object obj) {
        if (obj instanceof OnmsServiceType) {
            OnmsServiceType t = (OnmsServiceType)obj;
            return m_id.equals(t.m_id);
        }
        return false;
    }

    /**
     * <p>hashCode</p>
     *
     * @return a int.
     */
    @Override
    public int hashCode() {
        return m_id.intValue();
    }

}
